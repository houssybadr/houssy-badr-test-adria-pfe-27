package ma.enset.adria.account.mcp;

import ma.enset.adria.account.domain.AccountStatus;
import ma.enset.adria.account.service.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
public class AccountMcpTools {
    private final AccountService service;

    public AccountMcpTools(AccountService service) { this.service = service; }

    @Tool(name = "listAccounts", description = "List bank accounts with their status, currency and balance")
    public List<AccountView> listAccounts() { return service.findAll(); }

    @Tool(name = "getAccount", description = "Read a bank account by its UUID")
    public AccountView getAccount(@ToolParam(description = "Account UUID") String accountId) {
        return service.findById(UUID.fromString(accountId));
    }

    @Tool(name = "getAccountBalance", description = "Read the current balance and currency of a bank account")
    public AccountBalance getAccountBalance(@ToolParam(description = "Account UUID") String accountId) {
        return service.balance(UUID.fromString(accountId));
    }

    @Tool(name = "createAccount", description = "Create an ACTIVE bank account with a nonnegative initial balance")
    public AccountView createAccount(
            @ToolParam(description = "Customer reference, 1 to 100 characters") String customerReference,
            @ToolParam(description = "Uppercase ISO currency, for example EUR or MAD") String currency,
            @ToolParam(description = "Exact initial balance as a decimal string, for example 100.25") String balance) {
        return service.create(new CreateAccountRequest(customerReference, currency, new BigDecimal(balance)));
    }

    @Tool(name = "changeAccountStatus", description = "Change a bank account status to ACTIVE or BLOCKED")
    public AccountView changeAccountStatus(@ToolParam(description = "Account UUID") String accountId,
                                          @ToolParam(description = "ACTIVE or BLOCKED") AccountStatus status) {
        return service.changeStatus(UUID.fromString(accountId), status);
    }

    @Tool(name = "debitAccount", description = "Debit an ACTIVE account in its own currency. Amount must be positive and funds sufficient. Does not record a transaction; use transaction-service for transaction creation")
    public AccountView debitAccount(@ToolParam(description = "Account UUID") String accountId,
                                   @ToolParam(description = "Positive amount as a decimal string") String amount,
                                   @ToolParam(description = "Account currency") String currency) {
        return service.debit(UUID.fromString(accountId), new BalanceOperationRequest(new BigDecimal(amount), currency));
    }

    @Tool(name = "creditAccount", description = "Credit an ACTIVE account in its own currency. Amount must be positive. Does not record a transaction; use transaction-service for transaction creation")
    public AccountView creditAccount(@ToolParam(description = "Account UUID") String accountId,
                                    @ToolParam(description = "Positive amount as a decimal string") String amount,
                                    @ToolParam(description = "Account currency") String currency) {
        return service.credit(UUID.fromString(accountId), new BalanceOperationRequest(new BigDecimal(amount), currency));
    }
}
