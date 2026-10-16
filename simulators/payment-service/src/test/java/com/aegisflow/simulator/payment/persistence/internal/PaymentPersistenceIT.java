package com.aegisflow.simulator.payment.persistence.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.aegisflow.simulator.payment.CreatePaymentCommand;
import com.aegisflow.simulator.payment.PaymentCreation;
import com.aegisflow.simulator.payment.PaymentIdempotencyConflictException;
import com.aegisflow.simulator.payment.PaymentStatus;
import com.aegisflow.simulator.payment.RecordedPayment;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentPersistenceIT {

    private static final String RUNTIME = "aegisflow_payment_runtime";
    private static final String MIGRATOR = "aegisflow_payment_migrator";
    private static final String PASSWORD = "synthetic-test-only";

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse(
            "postgres:18.6@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336")
            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("aegisflow_payment")
            .withUsername("payment_inspection_admin")
            .withPassword(PASSWORD);

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry properties) throws SQLException {
        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + MIGRATOR + " LOGIN PASSWORD '" + PASSWORD + "'");
            statement.execute("CREATE ROLE " + RUNTIME + " LOGIN PASSWORD '" + PASSWORD + "'");
            statement.execute("REVOKE ALL ON DATABASE aegisflow_payment FROM PUBLIC");
            statement.execute("GRANT CONNECT ON DATABASE aegisflow_payment TO " + MIGRATOR + ", " + RUNTIME);
            statement.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC");
            statement.execute("CREATE SCHEMA aegisflow AUTHORIZATION " + MIGRATOR);
        }
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", () -> RUNTIME);
        properties.add("spring.datasource.password", () -> PASSWORD);
        properties.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        properties.add("spring.flyway.user", () -> MIGRATOR);
        properties.add("spring.flyway.password", () -> PASSWORD);
        properties.add("spring.flyway.placeholders.runtimeRole", () -> RUNTIME);
    }

    @Autowired
    private Flyway flyway;

    @Autowired
    private PaymentCreation paymentCreation;

    @Autowired
    private PaymentJpaRepository paymentRepository;

    @Autowired
    private PaymentCommandValidator paymentCommandValidator;

    @Autowired
    private PaymentJsonFactory paymentJsonFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private JpaPaymentOutboxAppender outboxAppender;

    @LocalServerPort
    private int port;

    @BeforeEach
    void clearBusinessData() throws SQLException {
        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute(
                    "TRUNCATE TABLE aegisflow.idempotency_record, aegisflow.outbox_event, aegisflow.payments CASCADE");
        }
    }

    @Test
    void migrationValidatesAndPostgresProfileServesHealthyHttp() throws Exception {
        assertThat(flyway.info().applied()).hasSize(1);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/actuator/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void successfulCreateCommitsPaymentIdempotencyAndPendingOutbox() {
        CreatePaymentCommand command = authorizedCommand(
                "tenant-one", "request-one", "order-one", UUID.randomUUID(), "corr-one", "1499.00");

        RecordedPayment recorded = paymentCreation.recordInitialOutcome(command);

        assertThat(recorded.tenantId()).isEqualTo("tenant-one");
        assertThat(recorded.paymentId()).isEqualTo(command.paymentId());
        assertThat(recorded.businessVersion()).isEqualTo(1);
        assertThat(recorded.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(recorded.occurredAt().getNano() % 1_000).isZero();
        assertThat(count("payments")).isEqualTo(1);
        assertThat(count("idempotency_record")).isEqualTo(1);
        assertThat(count("outbox_event")).isEqualTo(1);

        var row = jdbcTemplate.queryForMap(
                """
                SELECT aggregate_version, event_id, event_type, status, payload_hash_version,
                       envelope::text AS envelope, publish_attempts, fencing_token
                  FROM aegisflow.outbox_event
                 WHERE tenant_id = ? AND aggregate_id = ?
                """,
                command.tenantId(),
                command.paymentId());
        assertThat(row.get("aggregate_version")).isEqualTo(1L);
        assertThat(row.get("event_id")).isEqualTo(recorded.eventId());
        assertThat(row.get("event_type")).isEqualTo("PaymentAuthorized");
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("payload_hash_version")).isEqualTo("aegisflow-json-v1");
        assertThat(row.get("publish_attempts")).isEqualTo(0);
        assertThat(row.get("fencing_token")).isEqualTo(0L);
        assertThat((String) row.get("envelope"))
                .contains("\"sourceSystem\": \"payment-service\"")
                .contains("\"schemaVersion\": \"1.0\"")
                .contains("\"amount\": \"1499.00\"")
                .contains("\"paymentVersion\": 1");
    }

    @Test
    void outboxFailureAfterInsertRollsBackAllThreeRecords() {
        doAnswer(invocation -> {
                    invocation.callRealMethod();
                    throw new SimulatedOutboxFailure();
                })
                .when(outboxAppender)
                .append(any(OutboxEventEntity.class));

        assertThatThrownBy(() -> paymentCreation.recordInitialOutcome(authorizedCommand(
                        "tenant-one", "request-one", "order-one", UUID.randomUUID(), "corr-one", "10.00")))
                .isInstanceOf(SimulatedOutboxFailure.class);

        assertThat(count("payments")).isZero();
        assertThat(count("idempotency_record")).isZero();
        assertThat(count("outbox_event")).isZero();
    }

    @Test
    void identicalReplayReturnsStoredIdentityAndChangedMaterialConflicts() {
        UUID paymentId = UUID.randomUUID();
        CreatePaymentCommand first = authorizedCommand(
                "tenant-one", "request-one", "order-one", paymentId, "corr-original", "10.0");
        CreatePaymentCommand replay = authorizedCommand(
                "tenant-one", "request-one", "order-one", paymentId, "corr-retry", "10.00");

        RecordedPayment recorded = paymentCreation.recordInitialOutcome(first);
        RecordedPayment repeated = paymentCreation.recordInitialOutcome(replay);

        assertThat(repeated).isEqualTo(recorded);
        assertThat(count("payments")).isEqualTo(1);
        assertThat(count("idempotency_record")).isEqualTo(1);
        assertThat(count("outbox_event")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT envelope->>'correlationId' FROM aegisflow.outbox_event", String.class))
                .isEqualTo("corr-original");

        CreatePaymentCommand changed = authorizedCommand(
                "tenant-one", "request-one", "order-one", paymentId, "corr-other", "11.00");
        assertThatThrownBy(() -> paymentCreation.recordInitialOutcome(changed))
                .isInstanceOf(PaymentIdempotencyConflictException.class);
        assertThat(count("payments")).isEqualTo(1);
        assertThat(count("outbox_event")).isEqualTo(1);
    }

    @Test
    void concurrentIdenticalCommandsProduceOnePaymentAndOneEvent() throws Exception {
        CreatePaymentCommand command = authorizedCommand(
                "tenant-one", "request-race", "order-race", UUID.randomUUID(), "corr-race", "25.00");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> race(command, ready, start));
            var second = executor.submit(() -> race(command, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            RecordedPayment firstResult = first.get(10, TimeUnit.SECONDS);
            RecordedPayment secondResult = second.get(10, TimeUnit.SECONDS);
            assertThat(firstResult).isEqualTo(secondResult);
        } finally {
            executor.shutdownNow();
        }

        assertThat(count("payments")).isEqualTo(1);
        assertThat(count("idempotency_record")).isEqualTo(1);
        assertThat(count("outbox_event")).isEqualTo(1);
    }

    @Test
    void tenantScopedRowsDoNotPermitCrossTenantReferencesOrDuplicateEventIdentities() {
        RecordedPayment tenantOne = paymentCreation.recordInitialOutcome(authorizedCommand(
                "tenant-one", "request-one", "shared-order", UUID.randomUUID(), "corr-one", "10.00"));
        RecordedPayment tenantTwo = paymentCreation.recordInitialOutcome(authorizedCommand(
                "tenant-two", "request-two", "shared-order", UUID.randomUUID(), "corr-two", "10.00"));

        assertThat(count("payments")).isEqualTo(2);
        NormalizedPaymentCommand crossTenant = paymentCommandValidator.validate(authorizedCommand(
                "tenant-two", "unused-cross", "cross-order", tenantOne.paymentId(), "corr-cross", "10.00"));
        assertThatThrownBy(() -> insertOutbox(
                        crossTenant,
                        1L,
                        UUID.randomUUID(),
                        Instant.parse("2026-10-12T01:02:03.123456Z")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_outbox_payment");

        NormalizedPaymentCommand duplicateEvent = paymentCommandValidator.validate(authorizedCommand(
                "tenant-two", "unused-event", "shared-order", tenantTwo.paymentId(), "corr-duplicate", "10.00"));
        assertThatThrownBy(() -> insertOutbox(
                        duplicateEvent,
                        2L,
                        tenantOne.eventId(),
                        Instant.parse("2026-10-12T01:02:04.123456Z")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_outbox_event_global");

        NormalizedPaymentCommand duplicateAggregateVersion = paymentCommandValidator.validate(authorizedCommand(
                "tenant-one", "unused-version", "shared-order", tenantOne.paymentId(), "corr-version", "10.00"));
        assertThatThrownBy(() -> insertOutbox(
                        duplicateAggregateVersion,
                        1L,
                        UUID.randomUUID(),
                        Instant.parse("2026-10-12T01:02:05.123456Z")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_outbox_aggregate_version");
    }

    @Test
    void databaseRejectsDeclinedPaymentWithoutAReason() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        INSERT INTO aegisflow.payments (
                            tenant_id, payment_id, business_transaction_id, order_id, status,
                            amount, currency, decline_reason_code, lock_version, created_at, updated_at
                        ) VALUES (?, ?, 'txn-declined', 'order-declined', 'DECLINED',
                                  10.0000, 'INR', NULL, 0, now(), now())
                        """,
                        "tenant-one",
                        UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_payments_decline_reason");
    }

    @Test
    void staleJpaVersionCannotOverwriteAConcurrentVersion() {
        RecordedPayment recorded = paymentCreation.recordInitialOutcome(authorizedCommand(
                "tenant-one", "request-one", "order-one", UUID.randomUUID(), "corr-one", "10.00"));
        PaymentEntityId id = new PaymentEntityId(recorded.tenantId(), recorded.paymentId());
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        PaymentEntity stale = transactions.execute(status -> paymentRepository.findById(id).orElseThrow());

        assertThat(jdbcTemplate.update(
                        "UPDATE aegisflow.payments SET lock_version = lock_version + 1 WHERE tenant_id = ? AND payment_id = ?",
                        recorded.tenantId(),
                        recorded.paymentId()))
                .isEqualTo(1);

        assertThatThrownBy(() -> transactions.executeWithoutResult(
                        status -> paymentRepository.saveAndFlush(stale)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void runtimePrivilegesAndOutboxTriggerProtectOwnedState() throws Exception {
        paymentCreation.recordInitialOutcome(authorizedCommand(
                "tenant-one", "request-one", "order-one", UUID.randomUUID(), "corr-one", "10.00"));

        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), RUNTIME, PASSWORD);
                var statement = connection.createStatement()) {
            assertSqlState(statement, "CREATE TABLE aegisflow.forbidden(id integer)", "42501");
            assertSqlState(statement, "UPDATE aegisflow.idempotency_record SET result_code = 'DECLINED'", "42501");
            assertSqlState(statement, "UPDATE aegisflow.outbox_event SET envelope = '{}'::jsonb", "42501");
            assertSqlState(statement, "DELETE FROM aegisflow.outbox_event", "42501");
            assertThat(statement.executeUpdate(
                            "UPDATE aegisflow.outbox_event SET status = 'FAILED', publish_attempts = publish_attempts + 1"))
                    .isEqualTo(1);
        }

        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), MIGRATOR, PASSWORD);
                var statement = connection.createStatement()) {
            assertSqlState(statement, "UPDATE aegisflow.outbox_event SET envelope = '{}'::jsonb", "55000");
            assertSqlState(statement, "DELETE FROM aegisflow.outbox_event", "55000");
        }
    }

    private RecordedPayment race(
            CreatePaymentCommand command, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Race start was not released");
        }
        return paymentCreation.recordInitialOutcome(command);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM aegisflow." + table, Long.class);
    }

    private void insertOutbox(
            NormalizedPaymentCommand command, long aggregateVersion, UUID eventId, Instant occurredAt) {
        PaymentJsonFactory.PaymentEvent event =
                paymentJsonFactory.event(command, eventId, aggregateVersion, occurredAt);
        jdbcTemplate.update(
                """
                INSERT INTO aegisflow.outbox_event (
                    tenant_id, outbox_id, aggregate_id, aggregate_version, event_id, event_type,
                    schema_version, source_system, business_transaction_id, correlation_id,
                    envelope, payload_hash, payload_hash_version, status, created_at,
                    publish_attempts, next_attempt_at, fencing_token
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, 'PENDING', ?, 0, ?, 0)
                """,
                command.tenantId(),
                UUID.randomUUID(),
                command.paymentId(),
                aggregateVersion,
                eventId,
                event.eventType(),
                PaymentJsonFactory.SCHEMA_VERSION,
                PaymentJsonFactory.SOURCE_SYSTEM,
                command.businessTransactionId(),
                command.correlationId(),
                event.envelope(),
                event.payloadHash(),
                CanonicalJson.PROFILE,
                Timestamp.from(occurredAt),
                Timestamp.from(occurredAt));
    }

    private static CreatePaymentCommand authorizedCommand(
            String tenant,
            String idempotencyKey,
            String orderId,
            UUID paymentId,
            String correlationId,
            String amount) {
        return new CreatePaymentCommand(
                tenant,
                idempotencyKey,
                paymentId,
                "txn-" + orderId,
                orderId,
                PaymentStatus.AUTHORIZED,
                new BigDecimal(amount),
                "INR",
                null,
                correlationId);
    }

    private static void assertSqlState(java.sql.Statement statement, String sql, String expectedState) {
        assertThatThrownBy(() -> statement.execute(sql))
                .isInstanceOf(SQLException.class)
                .extracting("SQLState")
                .isEqualTo(expectedState);
    }

    private static final class SimulatedOutboxFailure extends RuntimeException {}
}
