package ma.enset.adria.transaction.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bank_transactions")
public class BankTransaction {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false) private UUID accountId;
    private UUID recipientAccountId;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false, length = 100) private String merchantCategory;
    @Column(nullable = false, length = 2) private String country;
    @Column(nullable = false) private Instant createdAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private TransactionStatus status;

    protected BankTransaction() {}

    public BankTransaction(UUID accountId, UUID recipientAccountId, BigDecimal amount,
                           String currency, String merchantCategory, String country) {
        this.accountId = accountId;
        this.recipientAccountId = recipientAccountId;
        this.amount = amount;
        this.currency = currency;
        this.merchantCategory = merchantCategory;
        this.country = country;
        this.createdAt = Instant.now();
        this.status = TransactionStatus.PENDING;
    }

    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public UUID getRecipientAccountId() { return recipientAccountId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getMerchantCategory() { return merchantCategory; }
    public String getCountry() { return country; }
    public Instant getCreatedAt() { return createdAt; }
    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }
}
