package com.ensitech.transfer.web.dto;

import com.ensitech.transfer.domain.TransferState;

import java.time.Instant;

public record StateTransitionResponse(TransferState state, Instant at, String reason) {
}
