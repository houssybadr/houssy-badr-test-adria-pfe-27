package ma.enset.adria.account.service;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountBalance(UUID accountId, BigDecimal balance, String currency) {
}
