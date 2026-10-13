package com.aegisflow.simulator.payment;

public final class PaymentIdempotencyConflictException extends IllegalStateException {

    public PaymentIdempotencyConflictException(String message) {
        super(message);
    }
}
