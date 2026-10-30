package ma.enset.adria.account.service;

import ma.enset.adria.account.domain.Account;
import ma.enset.adria.account.domain.AccountStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record AccountView(UUID id, String customerReference, AccountStatus status,
                          String currency, BigDecimal balance) {
    public static AccountView from(Account account) {
        return new AccountView(account.getId(), account.getCustomerReference(),
                account.getStatus(), account.getCurrency(), account.getBalance());
    }
}
