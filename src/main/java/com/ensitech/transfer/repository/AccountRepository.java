package com.ensitech.transfer.repository;

import com.ensitech.transfer.domain.Account;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {
    void save(Account account);

    Optional<Account> findById(UUID id);
}
