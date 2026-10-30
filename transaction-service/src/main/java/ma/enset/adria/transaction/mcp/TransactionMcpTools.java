package ma.enset.adria.transaction.mcp;

import ma.enset.adria.transaction.domain.*;
import ma.enset.adria.transaction.service.TransactionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.UUID;

@Component
public class TransactionMcpTools {
    private final TransactionService service;
    public TransactionMcpTools(TransactionService service) { this.service = service; }

    @Tool(description = "List all bank transactions and transfers, including their status")
    public List<BankTransaction> listTransactions() { return service.findAll(null); }

    @Tool(description = "List transactions involving an account as sender or recipient")
    public List<BankTransaction> transactionsByAccount(@ToolParam(description = "Account UUID") String accountId) {
        return service.findAll(UUID.fromString(accountId));
    }

    @Tool(description = "Read a transaction by UUID, including its status")
    public BankTransaction getTransaction(@ToolParam(description = "Transaction UUID") String id) {
        return service.findById(UUID.fromString(id));
    }

    @Tool(description = "Create a bank transaction and debit the account. Validates positive amount, configured limit, active account, currency and funds. Never repeat automatically after a timeout")
    public BankTransaction createTransaction(@ToolParam(description = "Transaction details") CreateTransactionRequest input) {
        return service.create(input);
    }

    @Tool(description = "Transfer money between two different active accounts in the same currency. Validates the configured limit and funds. Never repeat automatically after a timeout")
    public BankTransaction transferMoney(@ToolParam(description = "Transfer details") CreateTransferRequest input) {
        return service.transfer(input);
    }
}
