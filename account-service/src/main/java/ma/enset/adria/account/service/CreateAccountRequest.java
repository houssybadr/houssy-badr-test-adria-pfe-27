package ma.enset.adria.account.service;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank @Size(max = 100) String customerReference,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal balance) {
}
