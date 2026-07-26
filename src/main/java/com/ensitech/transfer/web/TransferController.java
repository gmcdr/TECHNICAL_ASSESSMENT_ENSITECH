package com.ensitech.transfer.web;

import com.ensitech.transfer.service.CreateTransferCommand;
import com.ensitech.transfer.service.TransferResult;
import com.ensitech.transfer.service.TransferService;
import com.ensitech.transfer.web.dto.CreateTransferRequest;
import com.ensitech.transfer.web.dto.TransferResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/transfers")
public class TransferController {
    private final TransferService transfers;

    public TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request
    ) {
        TransferResult result = transfers.transfer(
                idempotencyKey,
                new CreateTransferCommand(
                        request.sourceAccountId(),
                        request.destinationAccountId(),
                        request.amount()
                )
        );

        HttpStatus status = statusFor(result);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (result.replayed()) {
            response.header("Idempotent-Replayed", "true");
        }
        return response.body(ResponseMapper.transfer(result.transfer()));
    }

    @GetMapping("/{transferId}")
    public TransferResponse find(@PathVariable UUID transferId) {
        return ResponseMapper.transfer(transfers.find(transferId));
    }

    private HttpStatus statusFor(TransferResult result) {
        return switch (result.outcome()) {
            case COMPLETED -> result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
            case REJECTED -> HttpStatus.UNPROCESSABLE_ENTITY;
            case PROCESSING_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
