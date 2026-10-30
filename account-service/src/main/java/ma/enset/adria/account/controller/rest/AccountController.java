package ma.enset.adria.account.controller.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import ma.enset.adria.account.domain.AccountStatus;
import ma.enset.adria.account.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AccountView> create(@Valid @RequestBody CreateAccountRequest request) {
        AccountView account = service.create(request);
        return ResponseEntity.created(URI.create("/api/accounts/" + account.id())).body(account);
    }

    @GetMapping
    public List<AccountView> findAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public AccountView findById(@PathVariable UUID id) { return service.findById(id); }

    @GetMapping("/{id}/balance")
    public AccountBalance balance(@PathVariable UUID id) { return service.balance(id); }

    @PatchMapping("/{id}/status")
    public AccountView status(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return service.changeStatus(id, request.status());
    }

    @PostMapping("/{id}/debit")
    public AccountView debit(@PathVariable UUID id, @Valid @RequestBody BalanceOperationRequest request) {
        return service.debit(id, request);
    }

    @PostMapping("/{id}/credit")
    public AccountView credit(@PathVariable UUID id, @Valid @RequestBody BalanceOperationRequest request) {
        return service.credit(id, request);
    }

    public record StatusRequest(@NotNull AccountStatus status) {}
}
