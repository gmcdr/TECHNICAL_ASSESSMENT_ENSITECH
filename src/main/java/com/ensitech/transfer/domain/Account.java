package com.ensitech.transfer.domain;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public final class Account {
    @FunctionalInterface
    public interface PostingAction {
        void post(long sourceBalanceAfter, long destinationBalanceAfter);
    }

    private final UUID id;
    private final String owner;
    private final ReentrantLock lock = new ReentrantLock();
    private long balanceCents;

    public Account(UUID id, String owner, long balanceCents) {
        this.id = Objects.requireNonNull(id);
        this.owner = Objects.requireNonNull(owner);
        this.balanceCents = balanceCents;
    }

    public UUID id() {
        return id;
    }

    public String owner() {
        return owner;
    }

    public long balanceCents() {
        lock.lock();
        try {
            return balanceCents;
        } finally {
            lock.unlock();
        }
    }

    public static boolean transfer(Account source, Account destination, long amountCents) {
        return transfer(source, destination, amountCents, (sourceAfter, destinationAfter) -> {
        });
    }

    public static boolean transfer(
            Account source,
            Account destination,
            long amountCents,
            PostingAction posting
    ) {
        Account first = source.id.compareTo(destination.id) < 0 ? source : destination;
        Account second = first == source ? destination : source;

        first.lock.lock();
        second.lock.lock();
        try {
            if (source.balanceCents < amountCents) {
                return false;
            }

            long sourceBefore = source.balanceCents;
            long destinationBefore = destination.balanceCents;
            long sourceAfter = Math.subtractExact(sourceBefore, amountCents);
            long destinationAfter = Math.addExact(destinationBefore, amountCents);

            source.balanceCents = sourceAfter;
            destination.balanceCents = destinationAfter;
            try {
                posting.post(sourceAfter, destinationAfter);
            } catch (RuntimeException | Error exception) {
                source.balanceCents = sourceBefore;
                destination.balanceCents = destinationBefore;
                throw exception;
            }
            return true;
        } finally {
            second.lock.unlock();
            first.lock.unlock();
        }
    }
}
