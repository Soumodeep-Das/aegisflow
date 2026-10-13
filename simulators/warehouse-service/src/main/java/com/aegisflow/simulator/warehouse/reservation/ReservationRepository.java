package com.aegisflow.simulator.warehouse.reservation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

import jakarta.persistence.EntityManager;

@Repository
@Profile("postgres")
final class ReservationRepository {

    private final EntityManager entityManager;

    ReservationRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    void insert(String tenantId, ReservationEntity reservation) {
        requireTenant(tenantId, reservation.tenantId());
        entityManager.persist(reservation);
    }

    Optional<ReservationEntity> find(String tenantId, UUID reservationId) {
        return Optional.ofNullable(entityManager.find(
                ReservationEntity.class,
                new ReservationEntityId(tenantId, reservationId)));
    }

    void flush(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        entityManager.flush();
    }

    private static void requireTenant(String requestedTenant, String entityTenant) {
        if (!Objects.equals(requestedTenant, entityTenant)) {
            throw new IllegalArgumentException("repository tenant does not match reservation tenant");
        }
    }
}
