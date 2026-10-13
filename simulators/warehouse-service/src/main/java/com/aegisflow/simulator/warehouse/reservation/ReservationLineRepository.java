package com.aegisflow.simulator.warehouse.reservation;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

import jakarta.persistence.EntityManager;

@Repository
@Profile("postgres")
final class ReservationLineRepository {

    private final EntityManager entityManager;

    ReservationLineRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    void insertAll(
            String tenantId,
            UUID reservationId,
            List<ReservationLineEntity> lines) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        for (var line : lines) {
            if (!tenantId.equals(line.tenantId()) || !reservationId.equals(line.reservationId())) {
                throw new IllegalArgumentException("repository scope does not match reservation line");
            }
            entityManager.persist(line);
        }
    }
}
