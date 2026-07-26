package com.ensitech.transfer.domain;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransferStateMachineTest {
    @Test
    void permitsOnlyDefinedStateTransitions() {
        var transfer = new Transfer(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                100,
                Clock.systemUTC()
        );

        assertEquals(TransferState.PENDING, transfer.state());
        assertThrows(IllegalStateException.class, () -> transfer.transitionTo(TransferState.COMPLETED, null));

        transfer.transitionTo(TransferState.PROCESSING, null);
        transfer.transitionTo(TransferState.COMPLETED, null);

        assertEquals(TransferState.COMPLETED, transfer.state());
        assertThrows(
                IllegalStateException.class,
                () -> transfer.transitionTo(TransferState.PROCESSING, null)
        );
    }
}
