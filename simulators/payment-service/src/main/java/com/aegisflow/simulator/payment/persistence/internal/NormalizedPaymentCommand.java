package com.aegisflow.simulator.payment.persistence.internal;

import com.aegisflow.simulator.payment.PaymentDeclineReason;
import com.aegisflow.simulator.payment.PaymentStatus;
import java.math.BigDecimal;
import java.util.UUID;

record NormalizedPaymentCommand(
        String tenantId,
        String idempotencyKey,
        UUID paymentId,
        String businessTransactionId,
        String orderId,
        PaymentStatus status,
        BigDecimal amount,
        String eventAmount,
        String currency,
        PaymentDeclineReason declineReason,
        String correlationId) {}
