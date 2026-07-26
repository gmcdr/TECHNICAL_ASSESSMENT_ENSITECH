package com.ensitech.transfer.repository.memory;

import com.ensitech.transfer.domain.Transfer;
import com.ensitech.transfer.repository.TransferRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryTransferRepository implements TransferRepository {
    private final Map<UUID, Transfer> transfers = new ConcurrentHashMap<>();

    @Override
    public void save(Transfer transfer) {
        transfers.put(transfer.id(), transfer);
    }

    @Override
    public Optional<Transfer> findById(UUID id) {
        return Optional.ofNullable(transfers.get(id));
    }

    @Override
    public void remove(Transfer transfer) {
        transfers.remove(transfer.id(), transfer);
    }
}
