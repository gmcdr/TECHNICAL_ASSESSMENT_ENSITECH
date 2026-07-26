package com.ensitech.transfer.web.dto;

import com.ensitech.transfer.domain.TransferState;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        TransferState state,
        String failureReason,
        List<StateTransitionResponse> transitions
) {
}
