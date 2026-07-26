package com.ensitech.transfer.web;

import com.ensitech.transfer.service.AccountService;
import com.ensitech.transfer.service.CreateAccountCommand;
import com.ensitech.transfer.web.dto.AccountResponse;
import com.ensitech.transfer.web.dto.CreateAccountRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
public class AccountController {
    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        var account = accounts.create(new CreateAccountCommand(
                request.owner(),
                request.initialBalance()
        ));
        return ResponseEntity
                .created(URI.create("/accounts/" + account.id()))
                .body(ResponseMapper.account(account));
    }

    @GetMapping("/{accountId}")
    public AccountResponse find(@PathVariable UUID accountId) {
        return ResponseMapper.account(accounts.find(accountId));
    }
}
