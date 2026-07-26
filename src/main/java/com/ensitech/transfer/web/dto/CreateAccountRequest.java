package com.ensitech.transfer.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank(message = "Owner is required")
        @Size(max = 100, message = "Owner must not exceed 100 characters")
        String owner,

        @NotNull(message = "Initial balance is required")
        @DecimalMin(value = "0.00", message = "Initial balance cannot be negative")
        @Digits(integer = 16, fraction = 2, message = "Initial balance must have at most two decimal places")
        BigDecimal initialBalance
) {
}
