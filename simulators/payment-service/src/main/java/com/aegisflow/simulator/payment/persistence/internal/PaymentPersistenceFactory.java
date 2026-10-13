package com.aegisflow.simulator.payment.persistence.internal;

import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("postgres")
final class PaymentPersistenceFactory {

    NewRecordIds newRecordIds() {
        return new NewRecordIds(UUID.randomUUID(), UUID.randomUUID());
    }

    PaymentEntity newPayment(NormalizedPaymentCommand command, Instant now) {
        return new PaymentEntity(command, now);
    }

    OutboxEventEntity newOutboxEvent(
            NormalizedPaymentCommand command,
            UUID outboxId,
            UUID eventId,
            long aggregateVersion,
            PaymentJsonFactory.PaymentEvent event,
            Instant now) {
        return new OutboxEventEntity(
                new OutboxEventEntityId(command.tenantId(), outboxId),
                command,
                aggregateVersion,
                eventId,
                event,
                now);
    }

    record NewRecordIds(UUID outboxId, UUID eventId) {}
}
