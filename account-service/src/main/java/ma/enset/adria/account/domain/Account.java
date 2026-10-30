package ma.enset.adria.account.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "bank_accounts")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String customerReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccountStatus status;

    @Column(nullable = false, length = 3)
    private String currency;

    // Exact decimal storage avoids rounding errors in monetary operations.
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    protected Account() {}

    public Account(String customerReference, String currency, BigDecimal balance) {
        this.customerReference = customerReference;
        this.currency = currency;
        this.balance = balance;
        this.status = AccountStatus.ACTIVE;
    }

    public UUID getId() { return id; }
    public String getCustomerReference() { return customerReference; }
    public AccountStatus getStatus() { return status; }
    public String getCurrency() { return currency; }
    public BigDecimal getBalance() { return balance; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
}
