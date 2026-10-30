package ma.enset.adria.account.controller.graphql;

import ma.enset.adria.account.domain.AccountStatus;
import ma.enset.adria.account.service.*;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import java.util.List;
import java.util.UUID;

@Controller
public class AccountGraphQlController {
    private final AccountService service;

    public AccountGraphQlController(AccountService service) { this.service = service; }

    @QueryMapping
    public List<AccountView> accounts() { return service.findAll(); }

    @QueryMapping
    public AccountView account(@Argument UUID id) { return service.findById(id); }

    @QueryMapping
    public AccountBalance accountBalance(@Argument UUID id) { return service.balance(id); }

    @MutationMapping
    public AccountView createAccount(@Argument CreateAccountRequest input) { return service.create(input); }

    @MutationMapping
    public AccountView changeAccountStatus(@Argument UUID id, @Argument AccountStatus status) {
        return service.changeStatus(id, status);
    }

    @MutationMapping
    public AccountView debitAccount(@Argument UUID id, @Argument BalanceOperationRequest input) {
        return service.debit(id, input);
    }

    @MutationMapping
    public AccountView creditAccount(@Argument UUID id, @Argument BalanceOperationRequest input) {
        return service.credit(id, input);
    }
}
