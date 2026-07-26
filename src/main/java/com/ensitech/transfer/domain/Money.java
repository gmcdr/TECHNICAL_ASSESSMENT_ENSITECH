package com.ensitech.transfer.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    private Money() {
    }

    public static long toCents(BigDecimal amount, boolean allowZero) {
        if (amount == null) {
            throw new InvalidMoneyException("Amount is required");
        }
        if (amount.scale() > 2) {
            throw new InvalidMoneyException("Amount must have at most two decimal places");
        }

        try {
            long cents = amount.setScale(2, RoundingMode.UNNECESSARY)
                    .movePointRight(2)
                    .longValueExact();
            if (allowZero ? cents < 0 : cents <= 0) {
                String message = allowZero
                        ? "Amount cannot be negative"
                        : "Amount must be greater than zero";
                throw new InvalidMoneyException(message);
            }
            return cents;
        } catch (ArithmeticException exception) {
            throw new InvalidMoneyException("Amount is outside the supported range");
        }
    }

    public static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }
}
