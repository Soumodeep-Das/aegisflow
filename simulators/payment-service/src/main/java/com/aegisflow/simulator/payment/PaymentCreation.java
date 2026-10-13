package com.aegisflow.simulator.payment;

/** Tenant-scoped application boundary; intentionally not exposed through HTTP in P0.2. */
public interface PaymentCreation {

    RecordedPayment recordInitialOutcome(CreatePaymentCommand command);
}
