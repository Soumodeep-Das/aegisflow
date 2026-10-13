package com.aegisflow.simulator.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Internal application command for recording a simulator-owned initial payment decision.
 * The tenant is mandatory even though P0.2 deliberately exposes no HTTP boundary yet.
 */
public record CreatePaymentCommand(
        String tenantId,
        String idempotencyKey,
        UUID paymentId,
        String businessTransactionId,
        String orderId,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        PaymentDeclineReason declineReason,
        String correlationId) {}
