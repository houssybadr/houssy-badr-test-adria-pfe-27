package ma.enset.adria.transaction.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.UUID;

@FeignClient(name = "account-service", url = "${bank.account-service.url:}")
public interface AccountClient {
    @GetMapping("/api/accounts/{id}")
    Account getAccount(@PathVariable("id") UUID id);

    @GetMapping("/api/accounts/{id}/balance")
    Balance getBalance(@PathVariable("id") UUID id);

    @PostMapping("/api/accounts/{id}/debit")
    Account debit(@PathVariable("id") UUID id, @RequestBody Movement movement);

    @PostMapping("/api/accounts/transfers")
    void transfer(@RequestBody Transfer transfer);

    record Account(UUID id, String customerReference, String status, String currency, BigDecimal balance) {}
    record Balance(UUID accountId, BigDecimal balance, String currency) {}
    record Movement(BigDecimal amount, String currency) {}
    record Transfer(UUID sourceAccountId, UUID targetAccountId, BigDecimal amount, String currency) {}
}
