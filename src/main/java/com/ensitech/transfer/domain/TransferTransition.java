package com.ensitech.transfer.domain;

import java.time.Instant;

public record TransferTransition(TransferState state, Instant at, String reason) {
}
