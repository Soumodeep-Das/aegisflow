package com.aegisflow.simulator.warehouse.reservation;

/** Raised when a caller reuses an idempotency key for different material input. */
public final class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("idempotency key was already used for different reservation input");
    }
}
