package com.ensitech.transfer.web.dto;

import com.ensitech.transfer.domain.LedgerEntryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        UUID transferId,
        UUID accountId,
        LedgerEntryType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Instant createdAt
) {
}
