package com.ensitech.transfer.domain;

public final class InvalidMoneyException extends IllegalArgumentException {
    public InvalidMoneyException(String message) {
        super(message);
    }
}
