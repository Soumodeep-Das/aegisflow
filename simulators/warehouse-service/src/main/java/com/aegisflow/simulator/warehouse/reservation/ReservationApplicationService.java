package com.aegisflow.simulator.warehouse.reservation;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("postgres")
public class ReservationApplicationService {

    private static final long INITIAL_RESERVATION_VERSION = 1;

    private final ReservationRepository reservationRepository;
    private final ReservationLineRepository reservationLineRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ReservationRequestFingerprint requestFingerprint;
    private final WarehouseEventFactory eventFactory;
    private final Clock clock;

    ReservationApplicationService(
            ReservationRepository reservationRepository,
            ReservationLineRepository reservationLineRepository,
            IdempotencyRepository idempotencyRepository,
            OutboxEventRepository outboxEventRepository,
            ReservationRequestFingerprint requestFingerprint,
            WarehouseEventFactory eventFactory,
            Clock clock) {
        this.reservationRepository = reservationRepository;
        this.reservationLineRepository = reservationLineRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.requestFingerprint = requestFingerprint;
        this.eventFactory = eventFactory;
        this.clock = clock;
    }

    /**
     * Persists the reservation outcome, idempotency result, and publication intent in one local transaction.
     * No network operation or Kafka publication occurs in this boundary.
     */
    @Transactional
    public ReservationResult createReservation(CreateReservationCommand command) {
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var candidateEventId = UUID.randomUUID();
        var claim = idempotencyRepository.claim(
                command.tenantId(),
                command.idempotencyKey(),
                requestFingerprint.hash(command),
                command.reservationId(),
                candidateEventId,
                INITIAL_RESERVATION_VERSION,
                command.status(),
                now);

        if (!claim.newlyClaimed()) {
            return new ReservationResult(
                    command.tenantId(),
                    claim.reservationId(),
                    claim.eventId(),
                    claim.reservationVersion(),
                    claim.status());
        }

        var reservation = new ReservationEntity(command, now);
        reservationRepository.insert(command.tenantId(), reservation);
        reservationLineRepository.insertAll(
                command.tenantId(),
                command.reservationId(),
                command.lines().stream()
                        .map(line -> new ReservationLineEntity(
                                command.tenantId(), command.reservationId(), line))
                        .toList());

        var message = eventFactory.create(
                command,
                claim.eventId(),
                claim.reservationVersion(),
                now);
        outboxEventRepository.append(
                command.tenantId(),
                UUID.randomUUID(),
                command.reservationId(),
                claim.reservationVersion(),
                message);

        // Force every SQL statement before returning so any database failure marks this transaction rollback-only.
        reservationRepository.flush(command.tenantId());
        if (reservation.exposedVersion() != claim.reservationVersion()) {
            throw new IllegalStateException("persisted reservation version differs from outbox version");
        }

        return new ReservationResult(
                command.tenantId(),
                command.reservationId(),
                claim.eventId(),
                claim.reservationVersion(),
                command.status());
    }
}
