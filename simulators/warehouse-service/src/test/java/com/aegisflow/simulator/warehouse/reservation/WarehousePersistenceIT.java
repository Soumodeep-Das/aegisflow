package com.aegisflow.simulator.warehouse.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import tools.jackson.databind.ObjectMapper;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WarehousePersistenceIT {

    private static final String POSTGRES_IMAGE =
            "postgres:18.6@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336";
    private static final String RUNTIME_USER = "aegisflow_warehouse_runtime";
    private static final String RUNTIME_PASSWORD = "synthetic-runtime-test-only";
    private static final String MIGRATION_USER = "aegisflow_warehouse_migrator";
    private static final String MIGRATION_PASSWORD = "synthetic-migrator-test-only";
    private static final UUID RESERVATION_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000003");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse(POSTGRES_IMAGE).asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("warehouse_p02")
            .withUsername("warehouse_test_admin")
            .withPassword("synthetic-admin-test-only")
            .withInitScript("db/test/warehouse-test-bootstrap.sql");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> RUNTIME_USER);
        registry.add("spring.datasource.password", () -> RUNTIME_PASSWORD);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", () -> MIGRATION_USER);
        registry.add("spring.flyway.password", () -> MIGRATION_PASSWORD);
        registry.add("spring.flyway.placeholders.runtimeRole", () -> RUNTIME_USER);
    }

    @Autowired
    private ReservationApplicationService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ObjectMapper objectMapper;

    @LocalServerPort
    private int port;

    private java.util.concurrent.ExecutorService executor;

    @BeforeEach
    void clearBusinessTables() throws Exception {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("""
                    TRUNCATE TABLE aegisflow.idempotency_record,
                                   aegisflow.reservation_lines,
                                   aegisflow.outbox_event,
                                   aegisflow.reservations
                    """);
        }
    }

    @AfterEach
    void stopExecutor() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    @Test
    void migrationsValidateAndPostgresProfileServesHealth() throws Exception {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().applied()).hasSize(1);

        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("select current_user")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo(RUNTIME_USER);
        }

        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/actuator/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        var response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void successCommitsReservationLinesIdempotencyAndOutboxTogether() {
        var result = service.createReservation(reservedCommand("idem-success", "corr-success"));

        assertThat(result.reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(result.reservationVersion()).isEqualTo(1);
        assertThat(result.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(count("reservations")).isEqualTo(1);
        assertThat(count("reservation_lines")).isEqualTo(2);
        assertThat(count("idempotency_record")).isEqualTo(1);
        assertThat(count("outbox_event")).isEqualTo(1);

        var envelope = jdbcTemplate.queryForObject(
                "select envelope::text from aegisflow.outbox_event where tenant_id = ? and event_id = ?",
                String.class,
                "tenant-demo",
                result.eventId());
        var event = objectMapper.readTree(envelope);
        assertThat(event.required("sourceSystem").asString()).isEqualTo("warehouse-service");
        assertThat(event.required("eventType").asString()).isEqualTo("InventoryReserved");
        assertThat(event.required("eventId").asString()).isEqualTo(result.eventId().toString());
        assertThat(event.required("details").required("reservationVersion").asLong()).isEqualTo(1);
        assertThat(event.required("details").required("items").size()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select status from aegisflow.outbox_event where tenant_id = ? and event_id = ?",
                String.class,
                "tenant-demo",
                result.eventId())).isEqualTo("PENDING");
    }

    @Test
    void failureAfterActualOutboxAppendRollsBackEveryRecord() {
        var transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(ignored -> {
            service.createReservation(reservedCommand("idem-rollback", "corr-rollback"));
            assertThat(count("reservations")).isEqualTo(1);
            assertThat(count("outbox_event")).isEqualTo(1);
            throw new InjectedFailure();
        })).isInstanceOf(InjectedFailure.class);

        assertThat(count("reservations")).isZero();
        assertThat(count("reservation_lines")).isZero();
        assertThat(count("idempotency_record")).isZero();
        assertThat(count("outbox_event")).isZero();
    }

    @Test
    void identicalReplayReturnsStoredResultAndChangedMaterialConflicts() {
        var first = service.createReservation(reservedCommand("idem-replay", "corr-first"));
        var replay = service.createReservation(reservedCommand("idem-replay", "corr-retry"));

        assertThat(replay).isEqualTo(first);
        assertThat(count("reservations")).isEqualTo(1);
        assertThat(count("outbox_event")).isEqualTo(1);

        var changed = new CreateReservationCommand(
                "tenant-demo",
                "idem-replay",
                RESERVATION_ID,
                "txn-order-1",
                "order-1",
                "corr-third",
                ReservationStatus.RESERVED,
                null,
                List.of(new ReservationLine("SKU-A", 99), new ReservationLine("SKU-B", 2)));
        assertThatThrownBy(() -> service.createReservation(changed))
                .isInstanceOf(IdempotencyConflictException.class);
        assertThat(count("reservations")).isEqualTo(1);
        assertThat(count("idempotency_record")).isEqualTo(1);
        assertThat(count("outbox_event")).isEqualTo(1);
    }

    @Test
    void concurrentIdenticalCommandsConvergeOnOneDurableResult() throws Exception {
        executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var command = reservedCommand("idem-race", "corr-race");

        var first = executor.submit(() -> {
            ready.countDown();
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            return service.createReservation(command);
        });
        var second = executor.submit(() -> {
            ready.countDown();
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            return service.createReservation(command);
        });

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
        assertThat(count("reservations")).isEqualTo(1);
        assertThat(count("idempotency_record")).isEqualTo(1);
        assertThat(count("outbox_event")).isEqualTo(1);
    }

    @Test
    void tenantForeignKeysAndGlobalEventUniquenessFailClosed() {
        var result = service.createReservation(reservedCommand("idem-constraints", "corr-constraints"));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into aegisflow.reservation_lines (tenant_id, reservation_id, sku, quantity)
                values (?, ?, ?, ?)
                """, "tenant-other", RESERVATION_ID, "SKU-X", 1))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into aegisflow.outbox_event (
                    tenant_id, outbox_id, aggregate_id, aggregate_version,
                    event_id, event_type, envelope, payload_hash, payload_hash_version,
                    status, created_at, publish_attempts, next_attempt_at, fencing_token
                )
                select ?, ?, aggregate_id, aggregate_version,
                       event_id, event_type, envelope, payload_hash, payload_hash_version,
                       status, created_at, publish_attempts, next_attempt_at, fencing_token
                  from aegisflow.outbox_event
                 where tenant_id = ? and event_id = ?
                """,
                "tenant-other",
                UUID.fromString("40000000-0000-0000-0000-000000000004"),
                "tenant-demo",
                result.eventId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void hibernateVersionRejectsAStaleForceIncrement() {
        service.createReservation(reservedCommand("idem-version", "corr-version"));
        var id = new ReservationEntityId("tenant-demo", RESERVATION_ID);
        var firstEntityManager = entityManagerFactory.createEntityManager();
        var staleEntityManager = entityManagerFactory.createEntityManager();
        try {
            firstEntityManager.getTransaction().begin();
            var current = firstEntityManager.find(ReservationEntity.class, id);
            staleEntityManager.getTransaction().begin();
            var stale = staleEntityManager.find(ReservationEntity.class, id);

            firstEntityManager.lock(current, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
            firstEntityManager.getTransaction().commit();

            assertThatThrownBy(() -> {
                staleEntityManager.lock(stale, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
                staleEntityManager.getTransaction().commit();
            }).isInstanceOfAny(OptimisticLockException.class, RollbackException.class);
        } finally {
            if (firstEntityManager.getTransaction().isActive()) {
                firstEntityManager.getTransaction().rollback();
            }
            if (staleEntityManager.getTransaction().isActive()) {
                staleEntityManager.getTransaction().rollback();
            }
            firstEntityManager.close();
            staleEntityManager.close();
        }

        assertThat(jdbcTemplate.queryForObject(
                "select lock_version from aegisflow.reservations where tenant_id = ? and reservation_id = ?",
                Long.class,
                "tenant-demo",
                RESERVATION_ID)).isEqualTo(1L);
    }

    @Test
    void runtimeRoleHasDmlButNoDdlDeleteOrPayloadMutationPrivilege() throws Exception {
        var result = service.createReservation(reservedCommand("idem-privileges", "corr-privileges"));

        assertThatThrownBy(() -> jdbcTemplate.execute(
                "create table aegisflow.runtime_must_not_create (id integer)"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from aegisflow.idempotency_record where tenant_id = ?",
                "tenant-demo"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update aegisflow.outbox_event set envelope = envelope where tenant_id = ? and event_id = ?",
                "tenant-demo",
                result.eventId()))
                .isInstanceOf(DataAccessException.class);

        assertThat(jdbcTemplate.update("""
                update aegisflow.outbox_event
                   set publish_attempts = publish_attempts + 1
                 where tenant_id = ? and event_id = ?
                """, "tenant-demo", result.eventId())).isEqualTo(1);

        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), MIGRATION_USER, MIGRATION_PASSWORD);
             var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate("""
                    update aegisflow.outbox_event
                       set envelope = envelope || '{"changed":true}'::jsonb
                     where tenant_id = 'tenant-demo'
                    """))
                    .isInstanceOf(java.sql.SQLException.class)
                    .hasMessageContaining("immutable");
        }
    }

    private long count(String table) {
        if (!List.of("reservations", "reservation_lines", "idempotency_record", "outbox_event")
                .contains(table)) {
            throw new IllegalArgumentException("unexpected table");
        }
        return jdbcTemplate.queryForObject(
                "select count(*) from aegisflow." + table,
                Long.class);
    }

    private static CreateReservationCommand reservedCommand(String idempotencyKey, String correlationId) {
        return new CreateReservationCommand(
                "tenant-demo",
                idempotencyKey,
                RESERVATION_ID,
                "txn-order-1",
                "order-1",
                correlationId,
                ReservationStatus.RESERVED,
                null,
                List.of(new ReservationLine("SKU-B", 2), new ReservationLine("SKU-A", 1)));
    }

    private static final class InjectedFailure extends RuntimeException {
    }
}
