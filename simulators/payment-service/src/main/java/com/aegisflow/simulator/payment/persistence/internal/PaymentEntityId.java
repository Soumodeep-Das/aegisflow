package com.aegisflow.simulator.payment.persistence.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
class PaymentEntityId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "tenant_id", nullable = false, length = 128, updatable = false)
    private String tenantId;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    protected PaymentEntityId() {}

    PaymentEntityId(String tenantId, UUID paymentId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId");
    }

    String tenantId() {
        return tenantId;
    }

    UUID paymentId() {
        return paymentId;
    }

    @Override
    public boolean equals(Object candidate) {
        if (this == candidate) {
            return true;
        }
        if (!(candidate instanceof PaymentEntityId other)) {
            return false;
        }
        return tenantId.equals(other.tenantId) && paymentId.equals(other.paymentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, paymentId);
    }
}
