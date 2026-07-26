package com.ensitech.transfer.repository;

import com.ensitech.transfer.domain.LedgerEntry;

import java.util.List;
import java.util.UUID;

public interface LedgerRepository {
    void postTransfer(
            UUID transferId,
            UUID sourceAccountId,
            UUID destinationAccountId,
            long amountCents,
            long sourceBalanceAfter,
            long destinationBalanceAfter
    );

    List<LedgerEntry> findAll();

    List<LedgerEntry> findByAccount(UUID accountId);

    List<LedgerEntry> findByTransfer(UUID transferId);
}
