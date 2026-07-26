package com.ensitech.transfer.service;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransferCommand(
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount
) {
}
