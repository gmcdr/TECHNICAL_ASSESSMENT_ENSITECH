package com.ensitech.transfer.service;

import com.ensitech.transfer.domain.LedgerEntry;
import com.ensitech.transfer.repository.LedgerRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class LedgerQueryService {
    private final LedgerRepository ledger;
    private final AccountService accountService;
    private final TransferService transferService;

    public LedgerQueryService(
            LedgerRepository ledger,
            AccountService accountService,
            TransferService transferService
    ) {
        this.ledger = ledger;
        this.accountService = accountService;
        this.transferService = transferService;
    }

    public List<LedgerEntry> findAll() {
        return ledger.findAll();
    }

    public List<LedgerEntry> findByAccount(UUID accountId) {
        accountService.find(accountId);
        return ledger.findByAccount(accountId);
    }

    public List<LedgerEntry> findByTransfer(UUID transferId) {
        transferService.find(transferId);
        return ledger.findByTransfer(transferId);
    }
}
