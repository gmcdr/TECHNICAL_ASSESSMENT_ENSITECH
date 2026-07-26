package com.ensitech.transfer.domain;

import java.time.Instant;
import java.util.UUID;

public record LedgerEntry(
        UUID id,
        UUID transferId,
        UUID accountId,
        LedgerEntryType type,
        long amountCents,
        long balanceAfterCents,
        Instant createdAt
) {
}
