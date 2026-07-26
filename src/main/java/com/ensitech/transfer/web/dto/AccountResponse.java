package com.ensitech.transfer.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(UUID id, String owner, BigDecimal balance) {
}
