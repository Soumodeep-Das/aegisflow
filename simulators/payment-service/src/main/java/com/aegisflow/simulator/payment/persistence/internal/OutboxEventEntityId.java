package com.aegisflow.simulator.payment.persistence.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
class OutboxEventEntityId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "tenant_id", nullable = false, length = 128, updatable = false)
    private String tenantId;

    @Column(name = "outbox_id", nullable = false, updatable = false)
    private UUID outboxId;

    protected OutboxEventEntityId() {}

    OutboxEventEntityId(String tenantId, UUID outboxId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.outboxId = Objects.requireNonNull(outboxId, "outboxId");
    }

    @Override
    public boolean equals(Object candidate) {
        if (this == candidate) {
            return true;
        }
        if (!(candidate instanceof OutboxEventEntityId other)) {
            return false;
        }
        return tenantId.equals(other.tenantId) && outboxId.equals(other.outboxId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, outboxId);
    }
}
