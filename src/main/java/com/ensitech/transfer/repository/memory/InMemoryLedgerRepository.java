package com.ensitech.transfer.repository.memory;

import com.ensitech.transfer.domain.LedgerEntry;
import com.ensitech.transfer.domain.LedgerEntryType;
import com.ensitech.transfer.repository.LedgerRepository;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public class InMemoryLedgerRepository implements LedgerRepository {
    private final Clock clock;
    private final List<LedgerEntry> entries = new ArrayList<>();
    private final Set<UUID> postedTransfers = new HashSet<>();

    public InMemoryLedgerRepository(Clock clock) {
        this.clock = clock;
    }

    @Override
    public synchronized void postTransfer(
            UUID transferId,
            UUID sourceAccountId,
            UUID destinationAccountId,
            long amountCents,
            long sourceBalanceAfter,
            long destinationBalanceAfter
    ) {
        if (postedTransfers.contains(transferId)) {
            throw new IllegalStateException("Transfer already posted to ledger: " + transferId);
        }

        var timestamp = clock.instant();
        var debit = new LedgerEntry(
                UUID.randomUUID(),
                transferId,
                sourceAccountId,
                LedgerEntryType.DEBIT,
                amountCents,
                sourceBalanceAfter,
                timestamp
        );
        var credit = new LedgerEntry(
                UUID.randomUUID(),
                transferId,
                destinationAccountId,
                LedgerEntryType.CREDIT,
                amountCents,
                destinationBalanceAfter,
                timestamp
        );

        entries.add(debit);
        entries.add(credit);
        postedTransfers.add(transferId);
    }

    @Override
    public synchronized List<LedgerEntry> findAll() {
        return List.copyOf(entries);
    }

    @Override
    public synchronized List<LedgerEntry> findByAccount(UUID accountId) {
        return entries.stream().filter(entry -> entry.accountId().equals(accountId)).toList();
    }

    @Override
    public synchronized List<LedgerEntry> findByTransfer(UUID transferId) {
        return entries.stream().filter(entry -> entry.transferId().equals(transferId)).toList();
    }
}
