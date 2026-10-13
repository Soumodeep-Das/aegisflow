package com.aegisflow.simulator.order.persistence;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("postgres")
public class OrderPersistenceService {
    private final OrderRepository orders;
    private final JdbcTemplate jdbc;
    private final OrderOutbox outbox;
    private final Clock clock;

    @Autowired
    OrderPersistenceService(OrderRepository orders, JdbcTemplate jdbc, OrderOutbox outbox) {
        this(orders, jdbc, outbox, Clock.systemUTC());
    }
    OrderPersistenceService(OrderRepository orders, JdbcTemplate jdbc, OrderOutbox outbox, Clock clock) {
        this.orders = orders; this.jdbc = jdbc; this.outbox = outbox; this.clock = clock;
    }

    /** The PostgreSQL unique-key wait serializes concurrent replays; no application cache is authoritative. */
    @Transactional
    public Result create(CreateOrder command) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String requestHash = CanonicalJson.requestHash(command);
        UUID eventId = UUID.randomUUID();
        int claimed = jdbc.update("""
                INSERT INTO aegisflow.idempotency_record(tenant_id,operation,idempotency_key,request_hash,
                    request_hash_version,result_code,resource_id,resource_version,event_id,created_at)
                VALUES (?,'CREATE_ORDER',?,?,?,'CREATED',?,1,?,?)
                ON CONFLICT (tenant_id,operation,idempotency_key) DO NOTHING
                """, command.tenantId(), command.idempotencyKey(), requestHash, CanonicalJson.PROFILE,
                command.orderId(), eventId, Timestamp.from(now));
        if (claimed == 0) {
            return jdbc.queryForObject("""
                    SELECT request_hash,request_hash_version,resource_id,resource_version,event_id FROM aegisflow.idempotency_record
                    WHERE tenant_id=? AND operation='CREATE_ORDER' AND idempotency_key=?
                    """, (rs, row) -> {
                if (!CanonicalJson.PROFILE.equals(rs.getString("request_hash_version"))
                        || !requestHash.equals(rs.getString("request_hash"))) throw new IdempotencyConflictException();
                return new Result(rs.getObject("resource_id", UUID.class), rs.getLong("resource_version"), rs.getObject("event_id", UUID.class));
            }, command.tenantId(), command.idempotencyKey());
        }
        OrderEntity entity = orders.saveAndFlush(new OrderEntity(command, now));
        int lineNo = 0;
        for (CreateOrder.Line line : command.lines()) {
            jdbc.update("INSERT INTO aegisflow.order_lines(tenant_id,order_id,line_no,sku,quantity) VALUES (?,?,?,?,?)",
                    command.tenantId(), command.orderId(), ++lineNo, line.sku(), line.quantity());
        }
        outbox.append(command, eventId, entity.businessVersion(), now);
        return new Result(command.orderId(), entity.businessVersion(), eventId);
    }

    @Transactional(readOnly = true)
    public String status(String tenantId, UUID orderId) {
        CreateOrder.text(tenantId, "tenantId");
        return orders.findById(new OrderKey(tenantId, orderId)).map(OrderEntity::status).orElseThrow();
    }
    public record Result(UUID orderId, long version, UUID eventId) { }
    public static class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException() { super("Idempotency key already has different material request content"); }
    }
}
