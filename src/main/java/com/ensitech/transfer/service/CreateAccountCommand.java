package com.ensitech.transfer.service;

import java.math.BigDecimal;

public record CreateAccountCommand(String owner, BigDecimal initialBalance) {
}
