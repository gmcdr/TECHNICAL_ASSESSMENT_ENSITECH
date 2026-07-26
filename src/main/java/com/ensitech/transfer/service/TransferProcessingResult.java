package com.ensitech.transfer.service;

public record TransferProcessingResult(boolean successful, String failureReason) {
    public static TransferProcessingResult completed() {
        return new TransferProcessingResult(true, null);
    }

    public static TransferProcessingResult failed(String reason) {
        return new TransferProcessingResult(false, reason);
    }
}
