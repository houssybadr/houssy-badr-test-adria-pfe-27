package ma.enset.adria.transaction.controller.graphql;

import ma.enset.adria.transaction.domain.*;
import ma.enset.adria.transaction.service.TransactionService;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.stereotype.Controller;
import java.util.List;
import java.util.UUID;

@Controller
public class TransactionGraphQlController {
    private final TransactionService service;
    public TransactionGraphQlController(TransactionService service) { this.service = service; }

    @QueryMapping
    public List<BankTransaction> transactions(@Argument UUID accountId) { return service.findAll(accountId); }

    @QueryMapping
    public BankTransaction transaction(@Argument UUID id) { return service.findById(id); }

    @MutationMapping
    public BankTransaction createTransaction(@Argument CreateTransactionRequest input) { return service.create(input); }

    @MutationMapping
    public BankTransaction transferMoney(@Argument CreateTransferRequest input) { return service.transfer(input); }
}
