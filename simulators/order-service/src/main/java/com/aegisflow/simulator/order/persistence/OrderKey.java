package com.aegisflow.simulator.order.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class OrderKey implements Serializable {
    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    protected OrderKey() { }
    OrderKey(String tenantId, UUID orderId) { this.tenantId = tenantId; this.orderId = orderId; }
    public String tenantId() { return tenantId; }
    public UUID orderId() { return orderId; }
    @Override public boolean equals(Object other) {
        return other instanceof OrderKey key && Objects.equals(tenantId, key.tenantId) && Objects.equals(orderId, key.orderId);
    }
    @Override public int hashCode() { return Objects.hash(tenantId, orderId); }
}
