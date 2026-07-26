package com.ensitech.transfer.domain;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Transfer {
    private final UUID id;
    private final UUID sourceAccountId;
    private final UUID destinationAccountId;
    private final long amountCents;
    private final Clock clock;
    private final List<TransferTransition> transitions = new ArrayList<>();
    private TransferState state;

    public Transfer(
            UUID id,
            UUID sourceAccountId,
            UUID destinationAccountId,
            long amountCents,
            Clock clock
    ) {
        this.id = Objects.requireNonNull(id);
        this.sourceAccountId = Objects.requireNonNull(sourceAccountId);
        this.destinationAccountId = Objects.requireNonNull(destinationAccountId);
        this.amountCents = amountCents;
        this.clock = Objects.requireNonNull(clock);
        this.state = TransferState.PENDING;
        transitions.add(new TransferTransition(state, clock.instant(), null));
    }

    public synchronized void transitionTo(TransferState next, String reason) {
        boolean allowed = switch (state) {
            case PENDING -> next == TransferState.PROCESSING;
            case PROCESSING -> next == TransferState.COMPLETED || next == TransferState.FAILED;
            case COMPLETED, FAILED -> false;
        };
        if (!allowed) {
            throw new IllegalStateException("Invalid transfer transition: " + state + " -> " + next);
        }

        state = next;
        transitions.add(new TransferTransition(next, clock.instant(), reason));
    }

    public UUID id() {
        return id;
    }

    public UUID sourceAccountId() {
        return sourceAccountId;
    }

    public UUID destinationAccountId() {
        return destinationAccountId;
    }

    public long amountCents() {
        return amountCents;
    }

    public synchronized TransferState state() {
        return state;
    }

    public synchronized String failureReason() {
        if (state != TransferState.FAILED) {
            return null;
        }
        return transitions.getLast().reason();
    }

    public synchronized List<TransferTransition> transitions() {
        return List.copyOf(transitions);
    }
}
