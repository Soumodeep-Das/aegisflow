package com.aegisflow.simulator.payment;

/** Bounded reason codes keep event details typed and policy-neutral. */
public enum PaymentDeclineReason {
    INSUFFICIENT_FUNDS,
    PAYMENT_METHOD_REJECTED,
    RISK_REJECTED,
    SIMULATED_DECLINE
}
