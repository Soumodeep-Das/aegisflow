package com.aegisflow.simulator.payment.persistence.internal;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("postgres")
class JpaPaymentOutboxAppender implements PaymentOutboxAppender {

    private final OutboxEventJpaRepository repository;

    JpaPaymentOutboxAppender(OutboxEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(OutboxEventEntity event) {
        repository.saveAndFlush(event);
    }
}
