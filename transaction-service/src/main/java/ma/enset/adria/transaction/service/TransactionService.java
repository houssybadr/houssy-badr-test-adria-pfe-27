package ma.enset.adria.transaction.service;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import ma.enset.adria.transaction.client.AccountClient;
import ma.enset.adria.transaction.domain.*;
import ma.enset.adria.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import static ma.enset.adria.transaction.domain.TransactionException.Kind.*;

@Service
@Validated
public class TransactionService {
    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);
    private final TransactionRepository repository;
    private final AccountClient accounts;
    private final BigDecimal maxAmount;
    private final CircuitBreaker accountReads;
    private final Retry readRetry;

    public TransactionService(TransactionRepository repository, AccountClient accounts,
                              @Value("${bank.transaction.max-amount}") BigDecimal maxAmount,
                              CircuitBreakerRegistry breakers, RetryRegistry retries) {
        if (maxAmount.signum() <= 0) throw new IllegalArgumentException("Le plafond doit etre positif");
        this.repository = repository;
        this.accounts = accounts;
        this.maxAmount = maxAmount;
        this.accountReads = breakers.circuitBreaker("accountReads");
        this.readRetry = retries.retry("accountReads");
    }

    public BankTransaction create(@NotNull @Valid CreateTransactionRequest request) {
        return execute(request.accountId(), null, request.amount(), request.currency(),
                request.merchantCategory(), request.country());
    }

    public BankTransaction transfer(@NotNull @Valid CreateTransferRequest request) {
        if (request.sourceAccountId().equals(request.targetAccountId())) {
            throw invalid("SAME_ACCOUNT", "Les deux comptes doivent etre differents");
        }
        return execute(request.sourceAccountId(), request.targetAccountId(), request.amount(),
                request.currency(), "TRANSFER", request.country());
    }

    public BankTransaction findById(@NotNull UUID id) {
        return repository.findById(id).orElseThrow(() -> new TransactionException(
                NOT_FOUND, "TRANSACTION_NOT_FOUND", "Transaction introuvable", id));
    }

    public List<BankTransaction> findAll(UUID accountId) {
        return accountId == null ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findByAccountIdOrRecipientAccountIdOrderByCreatedAtDesc(accountId, accountId);
    }

    private BankTransaction execute(UUID source, UUID target, BigDecimal amount,
                                    String currency, String category, String country) {
        if (amount.compareTo(maxAmount) > 0) throw invalid("AMOUNT_LIMIT", "Le montant depasse le plafond configure");
        checkAccount(read(() -> accounts.getAccount(source)), currency);
        if (target != null) checkAccount(read(() -> accounts.getAccount(target)), currency);
        AccountClient.Balance balance = read(() -> accounts.getBalance(source));
        if (balance == null || balance.balance() == null || !currency.equals(balance.currency())) {
            throw unavailable(null);
        }
        if (balance.balance().compareTo(amount) < 0) throw invalid("INSUFFICIENT_FUNDS", "Solde insuffisant");

        // Each repository call commits locally. Keep evidence before any remote mutation.
        BankTransaction transaction = repository.saveAndFlush(
                new BankTransaction(source, target, amount, currency, category, country));
        try {
            // Never retry monetary writes: a timeout does not prove that the write failed.
            if (target == null) accounts.debit(source, new AccountClient.Movement(amount, currency));
            else accounts.transfer(new AccountClient.Transfer(source, target, amount, currency));
        } catch (FeignException exception) {
            boolean rejected = exception.status() == 400 || exception.status() == 404 || exception.status() == 409;
            transaction.setStatus(rejected ? TransactionStatus.REJECTED : TransactionStatus.UNKNOWN);
            repository.saveAndFlush(transaction);
            if (rejected) throw new TransactionException(INVALID, "ACCOUNT_OPERATION_REJECTED",
                    "Operation refusee par account-service; aucun mouvement applique", transaction.getId());
            log.warn("Resultat distant inconnu pour la transaction {}", transaction.getId());
            throw unavailable(transaction.getId());
        } catch (RuntimeException exception) {
            transaction.setStatus(TransactionStatus.UNKNOWN);
            repository.saveAndFlush(transaction);
            throw unavailable(transaction.getId());
        }
        transaction.setStatus(TransactionStatus.COMPLETED);
        try {
            return repository.saveAndFlush(transaction);
        } catch (RuntimeException exception) {
            // Account movement succeeded; the previously committed PENDING row remains for reconciliation.
            log.error("Mouvement applique, finalisation locale a verifier: {}", transaction.getId(), exception);
            throw new TransactionException(UNAVAILABLE, "RECONCILIATION_REQUIRED",
                    "Mouvement applique; finalisation locale a verifier. Ne pas repeter l'operation", transaction.getId());
        }
    }

    private void checkAccount(AccountClient.Account account, String currency) {
        if (account == null) throw unavailable(null);
        if (!"ACTIVE".equals(account.status())) throw invalid("ACCOUNT_BLOCKED", "Le compte doit etre actif");
        if (!currency.equals(account.currency())) throw invalid("CURRENCY_MISMATCH", "La devise doit correspondre au compte");
    }

    private <T> T read(Supplier<T> request) {
        try {
            return readRetry.executeSupplier(() -> accountReads.executeSupplier(request));
        } catch (FeignException exception) {
            if (exception.status() == 404) throw invalid("ACCOUNT_NOT_FOUND", "Compte introuvable");
            if (exception.status() >= 400 && exception.status() < 500) {
                throw invalid("ACCOUNT_REQUEST_REJECTED", "Requete refusee par account-service");
            }
            log.warn("Fallback de lecture account-service: {}", exception.getClass().getSimpleName());
            throw unavailable(null);
        } catch (CallNotPermittedException exception) {
            log.warn("Fallback: circuit accountReads ouvert");
            throw unavailable(null);
        }
    }

    private TransactionException invalid(String code, String message) {
        return new TransactionException(INVALID, code, message, null);
    }

    private TransactionException unavailable(UUID id) {
        String message = id == null ? "account-service indisponible; aucune operation lancee"
                : "Resultat de l'operation a verifier; ne pas la repeter automatiquement";
        return new TransactionException(UNAVAILABLE, "ACCOUNT_SERVICE_UNAVAILABLE", message, id);
    }
}
