package ma.enset.adria.transaction.controller.rest;

import jakarta.validation.Valid;
import ma.enset.adria.transaction.domain.*;
import ma.enset.adria.transaction.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final TransactionService service;
    public TransactionController(TransactionService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<BankTransaction> create(@Valid @RequestBody CreateTransactionRequest request) {
        return created(service.create(request));
    }

    @PostMapping("/transfers")
    public ResponseEntity<BankTransaction> transfer(@Valid @RequestBody CreateTransferRequest request) {
        return created(service.transfer(request));
    }

    @GetMapping
    public List<BankTransaction> findAll(@RequestParam(required = false) UUID accountId) { return service.findAll(accountId); }

    @GetMapping("/{id}")
    public BankTransaction findById(@PathVariable UUID id) { return service.findById(id); }

    private ResponseEntity<BankTransaction> created(BankTransaction transaction) {
        return ResponseEntity.created(URI.create("/api/transactions/" + transaction.getId())).body(transaction);
    }
}
