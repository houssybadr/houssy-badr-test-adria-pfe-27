package ma.enset.adria.account.service;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record BalanceOperationRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {
}
