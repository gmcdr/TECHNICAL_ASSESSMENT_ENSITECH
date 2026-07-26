package com.ensitech.transfer.service;

import com.ensitech.transfer.domain.Account;
import com.ensitech.transfer.domain.Transfer;
import com.ensitech.transfer.repository.LedgerRepository;
import org.springframework.stereotype.Component;

@Component
public class AtomicTransferProcessor implements TransferProcessor {
    private final LedgerRepository ledger;

    public AtomicTransferProcessor(LedgerRepository ledger) {
        this.ledger = ledger;
    }

    @Override
    public TransferProcessingResult process(Transfer transfer, Account source, Account destination) {
        boolean transferred = Account.transfer(
                source,
                destination,
                transfer.amountCents(),
                (sourceBalanceAfter, destinationBalanceAfter) -> ledger.postTransfer(
                        transfer.id(),
                        source.id(),
                        destination.id(),
                        transfer.amountCents(),
                        sourceBalanceAfter,
                        destinationBalanceAfter
                )
        );

        return transferred
                ? TransferProcessingResult.completed()
                : TransferProcessingResult.failed("Insufficient funds");
    }
}
