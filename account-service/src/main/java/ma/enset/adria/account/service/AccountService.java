package ma.enset.adria.account.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import ma.enset.adria.account.domain.Account;
import ma.enset.adria.account.domain.AccountStatus;
import ma.enset.adria.account.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

@Service
@Validated
@Transactional(readOnly = true)
public class AccountService {
    private static final BigDecimal MAX_BALANCE = new BigDecimal("999999999999999.9999");
    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AccountView create(@NotNull @Valid CreateAccountRequest request) {
        try {
            Currency.getInstance(request.currency());
        } catch (IllegalArgumentException exception) {
            throw new AccountRuleException("INVALID_CURRENCY", "Devise ISO 4217 inconnue");
        }
        return AccountView.from(repository.save(new Account(
                request.customerReference().trim(), request.currency(), request.balance())));
    }

    public List<AccountView> findAll() {
        return repository.findAll().stream().map(AccountView::from).toList();
    }

    public AccountView findById(@NotNull UUID id) {
        return AccountView.from(repository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id)));
    }

    public AccountBalance balance(@NotNull UUID id) {
        AccountView account = findById(id);
        return new AccountBalance(account.id(), account.balance(), account.currency());
    }

    @Transactional
    public AccountView changeStatus(@NotNull UUID id, @NotNull AccountStatus status) {
        Account account = locked(id);
        account.setStatus(status);
        return AccountView.from(account);
    }

    @Transactional
    public AccountView debit(@NotNull UUID id, @NotNull @Valid BalanceOperationRequest request) {
        return apply(id, request, false);
    }

    @Transactional
    public AccountView credit(@NotNull UUID id, @NotNull @Valid BalanceOperationRequest request) {
        return apply(id, request, true);
    }

    @Transactional
    public void transfer(@NotNull UUID sourceId, @NotNull UUID targetId,
                         @NotNull @Valid BalanceOperationRequest request) {
        if (sourceId.equals(targetId)) {
            throw new AccountRuleException("SAME_ACCOUNT", "Les deux comptes doivent etre differents");
        }
        // Lock in a fixed order to avoid opposite transfers deadlocking each other.
        Account first = locked(sourceId.compareTo(targetId) < 0 ? sourceId : targetId);
        Account second = locked(sourceId.compareTo(targetId) < 0 ? targetId : sourceId);
        Account source = first.getId().equals(sourceId) ? first : second;
        Account target = first.getId().equals(targetId) ? first : second;
        if (source.getStatus() != AccountStatus.ACTIVE || target.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountRuleException("ACCOUNT_BLOCKED", "Les deux comptes doivent etre actifs");
        }
        if (!source.getCurrency().equals(request.currency()) || !target.getCurrency().equals(request.currency())) {
            throw new AccountRuleException("CURRENCY_MISMATCH", "Les devises doivent correspondre");
        }
        BigDecimal remaining = source.getBalance().subtract(request.amount());
        BigDecimal received = target.getBalance().add(request.amount());
        if (remaining.signum() < 0) throw new AccountRuleException("INSUFFICIENT_FUNDS", "Solde insuffisant");
        if (received.compareTo(MAX_BALANCE) > 0) throw new AccountRuleException("BALANCE_LIMIT", "Solde destinataire trop eleve");
        source.setBalance(remaining);
        target.setBalance(received);
    }

    private AccountView apply(UUID id, BalanceOperationRequest request, boolean credit) {
        Account account = locked(id);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountRuleException("ACCOUNT_BLOCKED", "Le compte doit etre actif");
        }
        if (!account.getCurrency().equals(request.currency())) {
            throw new AccountRuleException("CURRENCY_MISMATCH", "La devise doit correspondre au compte");
        }
        BigDecimal result = credit ? account.getBalance().add(request.amount())
                : account.getBalance().subtract(request.amount());
        if (result.signum() < 0) {
            throw new AccountRuleException("INSUFFICIENT_FUNDS", "Solde insuffisant");
        }
        if (result.compareTo(MAX_BALANCE) > 0) {
            throw new AccountRuleException("BALANCE_LIMIT", "Le solde depasse la capacite de stockage");
        }
        account.setBalance(result);
        return AccountView.from(account);
    }

    private Account locked(UUID id) {
        // The row lock serializes balance and status changes until transaction commit.
        return repository.findForUpdate(id).orElseThrow(() -> new AccountNotFoundException(id));
    }
}
