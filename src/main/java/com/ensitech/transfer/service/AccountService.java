package com.ensitech.transfer.service;

import com.ensitech.transfer.domain.Account;
import com.ensitech.transfer.domain.Money;
import com.ensitech.transfer.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AccountService {
    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public Account create(CreateAccountCommand command) {
        if (command == null || command.owner() == null || command.owner().isBlank()) {
            throw ApiException.badRequest("INVALID_OWNER", "Owner is required");
        }
        if (command.owner().length() > 100) {
            throw ApiException.badRequest(
                    "INVALID_OWNER",
                    "Owner must not exceed 100 characters"
            );
        }
        String owner = command.owner().trim();
        long initialBalanceCents = Money.toCents(command.initialBalance(), true);
        var account = new Account(UUID.randomUUID(), owner, initialBalanceCents);
        accounts.save(account);
        return account;
    }

    public Account find(UUID accountId) {
        if (accountId == null) {
            throw ApiException.badRequest("INVALID_ACCOUNT_ID", "Account ID is required");
        }
        return accounts.findById(accountId)
                .orElseThrow(() -> ApiException.notFound(
                        "ACCOUNT_NOT_FOUND",
                        "Account not found: " + accountId
                ));
    }
}
