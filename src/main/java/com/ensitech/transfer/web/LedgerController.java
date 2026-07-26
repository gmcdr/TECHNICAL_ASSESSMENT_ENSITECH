package com.ensitech.transfer.web;

import com.ensitech.transfer.service.LedgerQueryService;
import com.ensitech.transfer.web.dto.LedgerEntryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class LedgerController {
    private final LedgerQueryService ledger;

    public LedgerController(LedgerQueryService ledger) {
        this.ledger = ledger;
    }

    @GetMapping("/ledger")
    public List<LedgerEntryResponse> findAll() {
        return ledger.findAll().stream().map(ResponseMapper::ledgerEntry).toList();
    }

    @GetMapping("/accounts/{accountId}/ledger")
    public List<LedgerEntryResponse> findByAccount(@PathVariable UUID accountId) {
        return ledger.findByAccount(accountId).stream().map(ResponseMapper::ledgerEntry).toList();
    }

    @GetMapping("/transfers/{transferId}/ledger")
    public List<LedgerEntryResponse> findByTransfer(@PathVariable UUID transferId) {
        return ledger.findByTransfer(transferId).stream().map(ResponseMapper::ledgerEntry).toList();
    }
}
