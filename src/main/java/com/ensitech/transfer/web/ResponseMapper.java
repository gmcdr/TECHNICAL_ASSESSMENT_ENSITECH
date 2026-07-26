package com.ensitech.transfer.web;

import com.ensitech.transfer.domain.Account;
import com.ensitech.transfer.domain.LedgerEntry;
import com.ensitech.transfer.domain.Money;
import com.ensitech.transfer.domain.Transfer;
import com.ensitech.transfer.web.dto.AccountResponse;
import com.ensitech.transfer.web.dto.LedgerEntryResponse;
import com.ensitech.transfer.web.dto.StateTransitionResponse;
import com.ensitech.transfer.web.dto.TransferResponse;

final class ResponseMapper {
    private ResponseMapper() {
    }

    static AccountResponse account(Account account) {
        return new AccountResponse(
                account.id(),
                account.owner(),
                Money.fromCents(account.balanceCents())
        );
    }

    static TransferResponse transfer(Transfer transfer) {
        return new TransferResponse(
                transfer.id(),
                transfer.sourceAccountId(),
                transfer.destinationAccountId(),
                Money.fromCents(transfer.amountCents()),
                transfer.state(),
                transfer.failureReason(),
                transfer.transitions().stream()
                        .map(transition -> new StateTransitionResponse(
                                transition.state(),
                                transition.at(),
                                transition.reason()
                        ))
                        .toList()
        );
    }

    static LedgerEntryResponse ledgerEntry(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.id(),
                entry.transferId(),
                entry.accountId(),
                entry.type(),
                Money.fromCents(entry.amountCents()),
                Money.fromCents(entry.balanceAfterCents()),
                entry.createdAt()
        );
    }
}
