package com.ensitech.transfer.service;

import com.ensitech.transfer.domain.Account;
import com.ensitech.transfer.domain.TransferState;
import com.ensitech.transfer.repository.memory.InMemoryAccountRepository;
import com.ensitech.transfer.repository.memory.InMemoryLedgerRepository;
import com.ensitech.transfer.repository.memory.InMemoryTransferRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TransferConcurrencyTest {
    @Test
    void concurrentIdempotentRequestsExecuteExactlyOnce() {
        AtomicInteger executions = new AtomicInteger();
        ExecutorService transferExecutor = Executors.newFixedThreadPool(4);
        var context = context(
                transferExecutor,
                (transfer, source, destination) -> {
                    executions.incrementAndGet();
                    return Account.transfer(source, destination, transfer.amountCents())
                            ? TransferProcessingResult.completed()
                            : TransferProcessingResult.failed("Insufficient funds");
                }
        );

        try (var callers = Executors.newFixedThreadPool(16)) {
            Account source = account(context.accounts(), "Source", "100.00");
            Account destination = account(context.accounts(), "Destination", "0.00");
            CreateTransferCommand command = command(source, destination, "10.00");

            List<CompletableFuture<TransferResult>> calls = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(index -> CompletableFuture.supplyAsync(
                            () -> context.transfers().transfer("one-logical-operation", command),
                            callers
                    ))
                    .toList();
            List<TransferResult> results = calls.stream().map(CompletableFuture::join).toList();

            Set<Object> transferIds = results.stream()
                    .map(result -> result.transfer().id())
                    .collect(java.util.stream.Collectors.toSet());
            assertEquals(1, transferIds.size());
            assertEquals(1, executions.get());
            assertMoney("90.00", source.balanceCents());
            assertMoney("10.00", destination.balanceCents());
        } finally {
            transferExecutor.shutdownNow();
        }
    }

    @Test
    void concurrentTransfersCannotOverspendAndEverySuccessHasTwoLedgerEntries() {
        ExecutorService transferExecutor = Executors.newFixedThreadPool(8);
        Clock clock = Clock.systemUTC();
        var ledger = new InMemoryLedgerRepository(clock);
        var context = context(transferExecutor, new AtomicTransferProcessor(ledger), ledger);

        try (var callers = Executors.newFixedThreadPool(20)) {
            Account source = account(context.accounts(), "Source", "100.00");
            Account destination = account(context.accounts(), "Destination", "0.00");

            List<CompletableFuture<TransferResult>> calls = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(index -> CompletableFuture.supplyAsync(
                            () -> context.transfers().transfer(
                                    "concurrent-" + index,
                                    command(source, destination, "10.00")
                            ),
                            callers
                    ))
                    .toList();
            List<TransferResult> results = calls.stream().map(CompletableFuture::join).toList();

            long completed = results.stream()
                    .filter(result -> result.transfer().state() == TransferState.COMPLETED)
                    .count();
            long failed = results.stream()
                    .filter(result -> result.transfer().state() == TransferState.FAILED)
                    .count();
            assertEquals(10, completed);
            assertEquals(10, failed);
            assertMoney("0.00", source.balanceCents());
            assertMoney("100.00", destination.balanceCents());
            assertEquals(20, ledger.findAll().size());
            results.forEach(result -> assertEquals(
                    result.outcome() == TransferResult.Outcome.COMPLETED ? 2 : 0,
                    ledger.findByTransfer(result.transfer().id()).size()
            ));
        } finally {
            transferExecutor.shutdownNow();
        }
    }

    @Test
    void rejectsNewWorkWhenTheBoundedQueueIsFull() throws Exception {
        ThreadPoolExecutor transferExecutor = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy()
        );
        CountDownLatch processingStarted = new CountDownLatch(1);
        CountDownLatch releaseProcessing = new CountDownLatch(1);
        var context = context(
                transferExecutor,
                (transfer, source, destination) -> {
                    processingStarted.countDown();
                    try {
                        if (!releaseProcessing.await(3, TimeUnit.SECONDS)) {
                            return TransferProcessingResult.failed("Test timed out");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return TransferProcessingResult.failed("Interrupted");
                    }
                    return Account.transfer(source, destination, transfer.amountCents())
                            ? TransferProcessingResult.completed()
                            : TransferProcessingResult.failed("Insufficient funds");
                }
        );

        try (var callers = Executors.newFixedThreadPool(2)) {
            Account source = account(context.accounts(), "Source", "100.00");
            Account destination = account(context.accounts(), "Destination", "0.00");
            CreateTransferCommand command = command(source, destination, "1.00");

            CompletableFuture<TransferResult> first = CompletableFuture.supplyAsync(
                    () -> context.transfers().transfer("first", command),
                    callers
            );
            assertTrue(processingStarted.await(1, TimeUnit.SECONDS));
            CompletableFuture<TransferResult> queued = CompletableFuture.supplyAsync(
                    () -> context.transfers().transfer("queued", command),
                    callers
            );
            awaitQueueSize(transferExecutor, 1);

            try {
                context.transfers().transfer("over-capacity", command);
                fail("Expected the full transfer queue to reject new work");
            } catch (ApiException exception) {
                assertEquals(429, exception.status());
                assertEquals("TOO_MANY_REQUESTS", exception.code());
            } finally {
                releaseProcessing.countDown();
            }

            assertEquals(TransferState.COMPLETED, first.join().transfer().state());
            assertEquals(TransferState.COMPLETED, queued.join().transfer().state());
        } finally {
            releaseProcessing.countDown();
            transferExecutor.shutdownNow();
        }
    }

    private TestContext context(ExecutorService executor, TransferProcessor processor) {
        return context(executor, processor, new InMemoryLedgerRepository(Clock.systemUTC()));
    }

    private TestContext context(
            ExecutorService executor,
            TransferProcessor processor,
            InMemoryLedgerRepository ledger
    ) {
        Clock clock = Clock.systemUTC();
        var accountRepository = new InMemoryAccountRepository();
        var transferRepository = new InMemoryTransferRepository();
        var accountService = new AccountService(accountRepository);
        var transferService = new TransferService(
                accountRepository,
                transferRepository,
                processor,
                executor,
                clock
        );
        return new TestContext(accountService, transferService, ledger);
    }

    private static void awaitQueueSize(ThreadPoolExecutor executor, int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (executor.getQueue().size() != expected && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(expected, executor.getQueue().size());
    }

    private static Account account(AccountService accounts, String owner, String balance) {
        return accounts.create(new CreateAccountCommand(owner, new BigDecimal(balance)));
    }

    private static CreateTransferCommand command(Account source, Account destination, String amount) {
        return new CreateTransferCommand(source.id(), destination.id(), new BigDecimal(amount));
    }

    private static void assertMoney(String expected, long actualCents) {
        assertEquals(0, new BigDecimal(expected).compareTo(
                com.ensitech.transfer.domain.Money.fromCents(actualCents)
        ));
    }

    private record TestContext(
            AccountService accounts,
            TransferService transfers,
            InMemoryLedgerRepository ledger
    ) {
    }
}
