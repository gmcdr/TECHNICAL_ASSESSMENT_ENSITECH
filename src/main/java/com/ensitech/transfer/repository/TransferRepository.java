package com.ensitech.transfer.repository;

import com.ensitech.transfer.domain.Transfer;

import java.util.Optional;
import java.util.UUID;

public interface TransferRepository {
    void save(Transfer transfer);

    Optional<Transfer> findById(UUID id);

    void remove(Transfer transfer);
}
