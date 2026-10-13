package com.aegisflow.simulator.payment.persistence.internal;

import com.aegisflow.simulator.payment.CreatePaymentCommand;
import com.aegisflow.simulator.payment.PaymentCreation;
import com.aegisflow.simulator.payment.PaymentIdempotencyConflictException;
import com.aegisflow.simulator.payment.RecordedPayment;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("postgres")
class TransactionalPaymentCreationService implements PaymentCreation {

    private static final long INITIAL_BUSINESS_VERSION = 1L;

    private final PaymentCommandValidator validator;
    private final PaymentJsonFactory jsonFactory;
    private final PaymentPersistenceFactory persistenceFactory;
    private final IdempotencyRecordStore idempotencyStore;
    private final PaymentJpaRepository paymentRepository;
    private final PaymentOutboxAppender outboxAppender;
    private final Clock clock;

    TransactionalPaymentCreationService(
            PaymentCommandValidator validator,
            PaymentJsonFactory jsonFactory,
            PaymentPersistenceFactory persistenceFactory,
            IdempotencyRecordStore idempotencyStore,
            PaymentJpaRepository paymentRepository,
            PaymentOutboxAppender outboxAppender,
            Clock clock) {
        this.validator = validator;
        this.jsonFactory = jsonFactory;
        this.persistenceFactory = persistenceFactory;
        this.idempotencyStore = idempotencyStore;
        this.paymentRepository = paymentRepository;
        this.outboxAppender = outboxAppender;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RecordedPayment recordInitialOutcome(CreatePaymentCommand rawCommand) {
        NormalizedPaymentCommand command = validator.validate(rawCommand);
        PaymentJsonFactory.HashedJson request = jsonFactory.requestFingerprint(command);
        PaymentPersistenceFactory.NewRecordIds ids = persistenceFactory.newRecordIds();
        Instant occurredAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);

        IdempotencyRecordStore.ClaimResult claim = idempotencyStore.claim(
                command,
                request.sha256(),
                ids.eventId(),
                INITIAL_BUSINESS_VERSION,
                occurredAt);
        if (!claim.won()) {
            return replay(command, request.sha256(), claim.storedResult());
        }

        PaymentEntity payment = paymentRepository.saveAndFlush(
                persistenceFactory.newPayment(command, occurredAt));
        long businessVersion = payment.businessVersion();
        if (businessVersion != INITIAL_BUSINESS_VERSION) {
            throw new IllegalStateException("A new payment must begin at business version 1");
        }

        PaymentJsonFactory.PaymentEvent event =
                jsonFactory.event(command, ids.eventId(), businessVersion, occurredAt);
        outboxAppender.append(persistenceFactory.newOutboxEvent(
                command,
                ids.outboxId(),
                ids.eventId(),
                businessVersion,
                event,
                occurredAt));

        return new RecordedPayment(
                command.tenantId(),
                command.paymentId(),
                command.status(),
                businessVersion,
                ids.eventId(),
                occurredAt);
    }

    private RecordedPayment replay(
            NormalizedPaymentCommand command,
            String requestHash,
            IdempotencyRecordStore.StoredResult stored) {
        if (!CanonicalJson.PROFILE.equals(stored.requestHashVersion())) {
            throw new PaymentIdempotencyConflictException(
                    "The idempotency key uses an unsupported request hash profile");
        }
        boolean sameRequest = MessageDigest.isEqual(
                requestHash.getBytes(StandardCharsets.UTF_8),
                stored.requestHash().getBytes(StandardCharsets.UTF_8));
        if (!sameRequest) {
            throw new PaymentIdempotencyConflictException(
                    "The idempotency key is already bound to different payment content");
        }
        return new RecordedPayment(
                command.tenantId(),
                stored.resourceId(),
                stored.status(),
                stored.resourceVersion(),
                stored.eventId(),
                stored.createdAt());
    }
}
