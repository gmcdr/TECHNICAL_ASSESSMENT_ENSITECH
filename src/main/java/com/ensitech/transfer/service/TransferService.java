package com.ensitech.transfer.service;

import com.ensitech.transfer.domain.Account;
import com.ensitech.transfer.domain.Money;
import com.ensitech.transfer.domain.Transfer;
import com.ensitech.transfer.domain.TransferState;
import com.ensitech.transfer.repository.AccountRepository;
import com.ensitech.transfer.repository.TransferRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

@Service
public class TransferService {
    private record RequestFingerprint(UUID sourceId, UUID destinationId, long amountCents) {
    }

    private record IdempotencyEntry(
            RequestFingerprint fingerprint,
            CompletableFuture<TransferResult> result
    ) {
    }

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final TransferProcessor processor;
    private final ExecutorService executor;
    private final Clock clock;
    private final Map<String, IdempotencyEntry> idempotencyEntries = new ConcurrentHashMap<>();

    public TransferService(
            AccountRepository accounts,
            TransferRepository transfers,
            TransferProcessor processor,
            @Qualifier("transferExecutor") ExecutorService executor,
            Clock clock
    ) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.processor = processor;
        this.executor = executor;
        this.clock = clock;
    }

    public TransferResult transfer(String idempotencyKey, CreateTransferCommand command) {
        String key = validateIdempotencyKey(idempotencyKey);
        validateAccounts(command);

        long amountCents = Money.toCents(command.amount(), false);
        Account source = findAccount(command.sourceAccountId());
        Account destination = findAccount(command.destinationAccountId());
        var fingerprint = new RequestFingerprint(source.id(), destination.id(), amountCents);

        var resultFuture = new CompletableFuture<TransferResult>();
        var proposed = new IdempotencyEntry(fingerprint, resultFuture);
        IdempotencyEntry existing = idempotencyEntries.putIfAbsent(key, proposed);

        if (existing != null) {
            if (!existing.fingerprint().equals(fingerprint)) {
                throw new ApiException(
                        409,
                        "IDEMPOTENCY_CONFLICT",
                        "Idempotency-Key was already used for a different transfer"
                );
            }
            return existing.result().join().asReplay();
        }

        var transfer = new Transfer(
                UUID.randomUUID(),
                source.id(),
                destination.id(),
                amountCents,
                clock
        );
        transfers.save(transfer);

        try {
            executor.execute(() -> process(transfer, source, destination, resultFuture));
        } catch (RejectedExecutionException exception) {
            transfers.remove(transfer);
            idempotencyEntries.remove(key, proposed);
            throw new ApiException(
                    429,
                    "TOO_MANY_REQUESTS",
                    "Transfer queue is full; retry later with the same Idempotency-Key"
            );
        }

        return resultFuture.join();
    }

    public Transfer find(UUID transferId) {
        return transfers.findById(transferId)
                .orElseThrow(() -> ApiException.notFound(
                        "TRANSFER_NOT_FOUND",
                        "Transfer not found: " + transferId
                ));
    }

    private void process(
            Transfer transfer,
            Account source,
            Account destination,
            CompletableFuture<TransferResult> future
    ) {
        transfer.transitionTo(TransferState.PROCESSING, null);
        try {
            TransferProcessingResult processingResult = processor.process(transfer, source, destination);
            if (processingResult.successful()) {
                transfer.transitionTo(TransferState.COMPLETED, null);
                future.complete(new TransferResult(
                        transfer,
                        TransferResult.Outcome.COMPLETED,
                        false
                ));
            } else {
                transfer.transitionTo(TransferState.FAILED, processingResult.failureReason());
                future.complete(new TransferResult(
                        transfer,
                        TransferResult.Outcome.REJECTED,
                        false
                ));
            }
        } catch (Exception exception) {
            transfer.transitionTo(TransferState.FAILED, "Transfer processing failed");
            future.complete(new TransferResult(
                    transfer,
                    TransferResult.Outcome.PROCESSING_ERROR,
                    false
            ));
        }
    }

    private Account findAccount(UUID id) {
        return accounts.findById(id)
                .orElseThrow(() -> ApiException.notFound(
                        "ACCOUNT_NOT_FOUND",
                        "Account not found: " + id
                ));
    }

    private void validateAccounts(CreateTransferCommand command) {
        if (command == null) {
            throw ApiException.badRequest("INVALID_TRANSFER", "Transfer request is required");
        }
        if (command.sourceAccountId() == null || command.destinationAccountId() == null) {
            throw ApiException.badRequest(
                    "INVALID_TRANSFER",
                    "Source and destination account IDs are required"
            );
        }
        if (command.sourceAccountId().equals(command.destinationAccountId())) {
            throw ApiException.badRequest(
                    "INVALID_TRANSFER",
                    "Source and destination accounts must be different"
            );
        }
    }

    private String validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw ApiException.badRequest(
                    "IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key header is required"
            );
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > 100) {
            throw ApiException.badRequest(
                    "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must not exceed 100 characters"
            );
        }
        return normalized;
    }
}
