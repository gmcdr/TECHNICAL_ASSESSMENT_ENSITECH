package com.ensitech.transfer.service;

import com.ensitech.transfer.domain.Transfer;

public record TransferResult(Transfer transfer, Outcome outcome, boolean replayed) {
    public enum Outcome {
        COMPLETED,
        REJECTED,
        PROCESSING_ERROR
    }

    public TransferResult asReplay() {
        return new TransferResult(transfer, outcome, true);
    }
}
