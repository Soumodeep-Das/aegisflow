package com.aegisflow.simulator.payment.persistence.internal;

import com.aegisflow.simulator.payment.PaymentDeclineReason;
import com.aegisflow.simulator.payment.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments", schema = "aegisflow")
class PaymentEntity {

    @EmbeddedId
    private PaymentEntityId id;

    @Column(name = "business_transaction_id", nullable = false, length = 128, updatable = false)
    private String businessTransactionId;

    @Column(name = "order_id", nullable = false, length = 128, updatable = false)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16, updatable = false)
    private PaymentStatus status;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "decline_reason_code", length = 64, updatable = false)
    private PaymentDeclineReason declineReason;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentEntity() {}

    PaymentEntity(NormalizedPaymentCommand command, Instant now) {
        this.id = new PaymentEntityId(command.tenantId(), command.paymentId());
        this.businessTransactionId = command.businessTransactionId();
        this.orderId = command.orderId();
        this.status = command.status();
        this.amount = command.amount();
        this.currency = command.currency();
        this.declineReason = command.declineReason();
        this.createdAt = now;
        this.updatedAt = now;
    }

    PaymentEntityId id() {
        return id;
    }

    long businessVersion() {
        if (lockVersion == null || lockVersion < 0) {
            throw new IllegalStateException("Payment must be flushed before its business version is emitted");
        }
        return lockVersion + 1;
    }
}
