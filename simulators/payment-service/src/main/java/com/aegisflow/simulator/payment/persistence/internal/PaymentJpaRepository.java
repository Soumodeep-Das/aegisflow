package com.aegisflow.simulator.payment.persistence.internal;

import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentJpaRepository extends JpaRepository<PaymentEntity, PaymentEntityId> {}
