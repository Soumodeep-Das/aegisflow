package com.aegisflow.simulator.payment.persistence.internal;

import org.springframework.data.jpa.repository.JpaRepository;

interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, OutboxEventEntityId> {}
