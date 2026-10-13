package com.aegisflow.simulator.payment;

public final class PaymentValidationException extends IllegalArgumentException {

    public PaymentValidationException(String message) {
        super(message);
    }
}
