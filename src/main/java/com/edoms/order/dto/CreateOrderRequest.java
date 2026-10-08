package com.edoms.order.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateOrderRequest(
        @NotBlank @Size(max = 255) String productName,
        @NotNull @Positive Integer quantity,
        @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal price) {
}
