package com.aegisflow.simulator.payment.persistence.internal;

interface PaymentOutboxAppender {

    void append(OutboxEventEntity event);
}
