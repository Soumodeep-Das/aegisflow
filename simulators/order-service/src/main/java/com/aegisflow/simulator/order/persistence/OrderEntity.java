package com.aegisflow.simulator.order.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "orders", schema = "aegisflow")
class OrderEntity {
    @EmbeddedId private OrderKey id;
    @Column(name = "business_transaction_id", nullable = false, length = 128) private String transactionId;
    @Column(name = "correlation_id", nullable = false, length = 128) private String correlationId;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Version @Column(name = "lock_version", nullable = false) private Long lockVersion;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected OrderEntity() { }
    OrderEntity(CreateOrder command, Instant now) {
        id = new OrderKey(command.tenantId(), command.orderId()); transactionId = command.businessTransactionId();
        correlationId = command.correlationId(); status = "CREATED"; amount = command.amount();
        currency = command.currency(); createdAt = now; updatedAt = now;
    }
    OrderKey id() { return id; }
    long businessVersion() { return lockVersion + 1; }
    String status() { return status; }
    void touch(Instant now) { updatedAt = now; }
}
