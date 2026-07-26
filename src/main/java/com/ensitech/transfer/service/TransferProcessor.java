package com.ensitech.transfer.service;

import com.ensitech.transfer.domain.Account;
import com.ensitech.transfer.domain.Transfer;

@FunctionalInterface
public interface TransferProcessor {
    TransferProcessingResult process(Transfer transfer, Account source, Account destination);
}
