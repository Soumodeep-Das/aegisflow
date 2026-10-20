package com.aegisflow.simulator.warehouse.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

/** Real PostgreSQL claim/fencing checks; no Kafka delivery is inferred from these tests. */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "aegisflow.warehouse.relay.enabled=false")
class WarehouseOutboxRelayIT {
    private static final String DATABASE = "aegisflow_warehouse_relay";
    private static final String RUNTIME = "aegisflow_warehouse_runtime";
    private static final String MIGRATOR = "aegisflow_warehouse_migrator";
    private static final String PASSWORD = "synthetic-relay-test-only";
    private static final Duration LEASE = Duration.ofSeconds(60);

    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse(
            "postgres:18.6@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336")
            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName(DATABASE).withUsername("inspection_admin").withPassword(PASSWORD);

    @DynamicPropertySource static void configure(DynamicPropertyRegistry properties) throws SQLException {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + MIGRATOR + " LOGIN PASSWORD '" + PASSWORD + "'");
            statement.execute("CREATE ROLE " + RUNTIME + " LOGIN PASSWORD '" + PASSWORD + "'");
            statement.execute("REVOKE ALL ON DATABASE " + DATABASE + " FROM PUBLIC");
            statement.execute("GRANT CONNECT ON DATABASE " + DATABASE + " TO " + MIGRATOR + ", " + RUNTIME);
            statement.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC");
            statement.execute("CREATE SCHEMA aegisflow AUTHORIZATION " + MIGRATOR);
        }
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        // The postgres profile has a separate Flyway URL; both must target this disposable database.
        properties.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", () -> RUNTIME);
        properties.add("spring.datasource.password", () -> PASSWORD);
        properties.add("spring.flyway.user", () -> MIGRATOR);
        properties.add("spring.flyway.password", () -> PASSWORD);
        properties.add("spring.flyway.placeholders.runtimeRole", () -> RUNTIME);
    }

    @Autowired ReservationApplicationService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    private WarehouseOutboxStore store;

    @BeforeEach void isolatePendingClaims() {
        // The class owns a disposable database. Retain immutable evidence instead of deleting outbox rows.
        jdbc.update("""
                UPDATE aegisflow.outbox_event
                SET status='FAILED',lease_owner=NULL,lease_until=NULL,last_error_code='TEST_RESET'
                WHERE status='PENDING'
                """);
        store = new WarehouseOutboxStore(jdbc, transactions);
    }

    @Test void claimCommitsDurablyWithDatabaseClockAndRuntimePrivileges() throws Exception {
        UUID eventId = createEvent();
        var claim = claim();
        assertThat(claim.eventId()).isEqualTo(eventId);
        assertThat(claim.attempts()).isEqualTo(1);
        assertThat(claim.fencingToken()).isEqualTo(1);
        assertThat(claim.payloadHashVersion()).isEqualTo(RelaySupport.PROFILE);
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo(RUNTIME);

        // A distinct connection cannot see an uncommitted claim, even if the caller's connection can.
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), RUNTIME, PASSWORD);
             var statement = connection.prepareStatement("""
                     SELECT status,lease_owner,publish_attempts,fencing_token,
                         lease_until > clock_timestamp() AS live,
                         lease_until <= clock_timestamp() + interval '61 seconds' AS bounded
                     FROM aegisflow.outbox_event WHERE tenant_id=? AND outbox_id=?
                     """)) {
            statement.setString(1, claim.tenantId());
            statement.setObject(2, claim.outboxId());
            try (var row = statement.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("status")).isEqualTo("PENDING");
                assertThat(row.getString("lease_owner")).isEqualTo(claim.workerId());
                assertThat(row.getInt("publish_attempts")).isEqualTo(1);
                assertThat(row.getLong("fencing_token")).isEqualTo(1);
                assertThat(row.getBoolean("live")).isTrue();
                assertThat(row.getBoolean("bounded")).isTrue();
            }
        }
        assertThat(store.claim(workerId(), LEASE)).isEmpty();
        assertThatThrownBy(() -> jdbc.update("UPDATE aegisflow.outbox_event SET payload_hash=repeat('0',64) WHERE event_id=?", eventId))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test void competingWorkersClaimDifferentRows() throws Exception {
        UUID firstEvent = createEvent();
        UUID secondEvent = createEvent();
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { gate.await(); return claim(); });
            var second = executor.submit(() -> { gate.await(); return claim(); });
            gate.countDown();
            var firstClaim = first.get(10, TimeUnit.SECONDS);
            var secondClaim = second.get(10, TimeUnit.SECONDS);
            assertThat(List.of(firstClaim.eventId(), secondClaim.eventId()))
                    .containsExactlyInAnyOrder(firstEvent, secondEvent);
            assertThat(firstClaim.workerId()).isNotEqualTo(secondClaim.workerId());
            assertThat(firstClaim.attempts()).isEqualTo(1);
            assertThat(secondClaim.attempts()).isEqualTo(1);
        }
        assertThat(store.claim(workerId(), LEASE)).isEmpty();
    }

    @Test void lockedCandidateIsSkippedWithoutHoldingUpAnotherWorker() throws Exception {
        UUID lockedEvent = createEvent();
        UUID availableEvent = createEvent();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), RUNTIME, PASSWORD)) {
            connection.setAutoCommit(false);
            try (var lock = connection.prepareStatement("SELECT event_id FROM aegisflow.outbox_event WHERE event_id=? FOR UPDATE")) {
                lock.setObject(1, lockedEvent);
                try (var row = lock.executeQuery()) { assertThat(row.next()).isTrue(); }
            }
            var executor = Executors.newSingleThreadExecutor();
            try {
                var result = executor.submit(this::claim).get(5, TimeUnit.SECONDS);
                assertThat(result.eventId()).isEqualTo(availableEvent);
            } finally {
                // Release the lock before joining the worker, even if SKIP LOCKED regresses.
                connection.rollback();
                executor.shutdownNow();
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            }
        }
        assertThat(claim().eventId()).isEqualTo(lockedEvent);
    }

    @Test void expiredClaimIsFencedBeforeAndAfterReclaimWithSameImmutableEvent() {
        createEvent();
        var expired = claim();
        expire(expired);
        assertCompletionsDenied(expired);
        var replacement = claim();
        assertThat(replacement.eventId()).isEqualTo(expired.eventId());
        assertThat(replacement.outboxId()).isEqualTo(expired.outboxId());
        assertThat(replacement.envelope()).isEqualTo(expired.envelope());
        assertThat(replacement.payloadHash()).isEqualTo(expired.payloadHash());
        assertThat(replacement.fencingToken()).isEqualTo(expired.fencingToken() + 1);
        assertThat(replacement.attempts()).isEqualTo(expired.attempts() + 1);
        assertCompletionsDenied(expired);
        assertThat(store.published(replacement)).isTrue();
        assertThat(status(replacement)).isEqualTo("PUBLISHED");
    }

    @Test void tenantOwnerAndFenceAreAllRequiredForCompletion() {
        createEvent();
        var owned = claim();
        assertCompletionsDenied(copyClaim(owned, "different-tenant", owned.workerId(), owned.fencingToken()));
        assertCompletionsDenied(copyClaim(owned, owned.tenantId(), workerId(), owned.fencingToken()));
        assertCompletionsDenied(copyClaim(owned, owned.tenantId(), owned.workerId(), owned.fencingToken() + 1));
        assertThat(status(owned)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT lease_owner FROM aegisflow.outbox_event WHERE event_id=?", String.class, owned.eventId()))
                .isEqualTo(owned.workerId());
        assertThat(store.published(owned)).isTrue();
    }

    @Test void publishedAndFailedRowsAreTerminalAndReleaseTheirLeases() {
        createEvent();
        createEvent();
        var published = claim();
        assertThat(store.published(published)).isTrue();
        assertThat(status(published)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT published_at IS NOT NULL FROM aegisflow.outbox_event WHERE event_id=?",
                Boolean.class, published.eventId())).isTrue();
        assertLeaseCleared(published);
        assertCompletionsDenied(published);

        var failed = claim();
        assertThat(store.fail(failed, "INVALID_ENVELOPE")).isTrue();
        assertThat(status(failed)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT published_at IS NULL FROM aegisflow.outbox_event WHERE event_id=?",
                Boolean.class, failed.eventId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT last_error_code FROM aegisflow.outbox_event WHERE event_id=?",
                String.class, failed.eventId())).isEqualTo("INVALID_ENVELOPE");
        assertLeaseCleared(failed);
        assertCompletionsDenied(failed);
        assertThat(store.claim(workerId(), LEASE)).isEmpty();
    }

    @Test void retryUsesDatabaseTimeRetainsIntentAndWaitsUntilDue() {
        createEvent();
        var first = claim();
        assertThat(store.retry(first, Duration.ofSeconds(30), "PUBLISH_TIMEOUT")).isTrue();
        assertThat(status(first)).isEqualTo("PENDING");
        assertLeaseCleared(first);
        assertThat(jdbc.queryForObject("""
                SELECT next_attempt_at > clock_timestamp()
                    AND next_attempt_at <= clock_timestamp() + interval '31 seconds'
                    AND published_at IS NULL
                FROM aegisflow.outbox_event WHERE event_id=?
                """, Boolean.class, first.eventId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT last_error_code FROM aegisflow.outbox_event WHERE event_id=?",
                String.class, first.eventId())).isEqualTo("PUBLISH_TIMEOUT");
        assertThat(store.claim(workerId(), LEASE)).isEmpty();
        makeDue(first.eventId());
        var second = claim();
        assertThat(second.eventId()).isEqualTo(first.eventId());
        assertThat(second.envelope()).isEqualTo(first.envelope());
        assertThat(second.payloadHash()).isEqualTo(first.payloadHash());
        assertThat(second.payloadHashVersion()).isEqualTo(first.payloadHashVersion());
        assertThat(second.attempts()).isEqualTo(2);
        assertThat(second.fencingToken()).isEqualTo(2);
        assertCompletionsDenied(first);
    }

    @Test void claimCommitSurvivesRollbackOfAnAmbientTransaction() {
        UUID eventId = createEvent();
        OutboxClaim claimed = new TransactionTemplate(transactions).execute(status -> {
            var result = claim();
            status.setRollbackOnly();
            return result;
        });
        assertThat(claimed).isNotNull();
        assertThat(claimed.eventId()).isEqualTo(eventId);
        assertThat(jdbc.queryForObject("SELECT lease_owner FROM aegisflow.outbox_event WHERE event_id=?", String.class, eventId))
                .isEqualTo(claimed.workerId());
        assertThat(jdbc.queryForObject("SELECT publish_attempts FROM aegisflow.outbox_event WHERE event_id=?", Integer.class, eventId))
                .isEqualTo(1);
        assertThat(store.claim(workerId(), LEASE)).isEmpty();
    }

    @Test void acknowledgedSendThenCompletionCrashReplaysSameIdentityOutsideDatabaseTransaction() {
        UUID eventId = createEvent();
        String tenant = jdbc.queryForObject("SELECT tenant_id FROM aegisflow.outbox_event WHERE event_id=?", String.class, eventId);
        var sent = new java.util.ArrayList<String>();
        WarehouseOutboxRelay.Publisher acknowledgedPublisher = (key, envelope) -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            // A separate connection can lock the row during publishing: the claim transaction has ended.
            try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), RUNTIME, PASSWORD);
                 var lock = connection.prepareStatement("SELECT event_id FROM aegisflow.outbox_event WHERE event_id=? FOR UPDATE NOWAIT")) {
                lock.setObject(1, eventId);
                try (var row = lock.executeQuery()) { assertThat(row.next()).isTrue(); }
            }
            sent.add(key + "\n" + envelope);
        };
        var crashAfterAck = new WarehouseOutboxStore(jdbc, transactions) {
            @Override boolean published(OutboxClaim claim) { throw new IllegalStateException("injected completion crash"); }
        };
        var first = new WarehouseOutboxRelay(crashAfterAck, acknowledgedPublisher, workerId(), 1, java.util.Set.of(tenant));
        assertThatThrownBy(first::poll).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM aegisflow.outbox_event WHERE event_id=?", String.class, eventId))
                .isEqualTo("PENDING");
        jdbc.update("UPDATE aegisflow.outbox_event SET lease_until=clock_timestamp()-interval '1 second' WHERE event_id=?", eventId);
        assertThat(new WarehouseOutboxRelay(store, acknowledgedPublisher, workerId(), 1, java.util.Set.of(tenant)).poll()).isEqualTo(1);
        assertThat(sent).hasSize(2);
        assertThat(sent.get(1)).isEqualTo(sent.getFirst());
        assertThat(jdbc.queryForObject("SELECT status FROM aegisflow.outbox_event WHERE event_id=?", String.class, eventId))
                .isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aegisflow.reservations WHERE tenant_id=?", Long.class, tenant)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aegisflow.outbox_event WHERE event_id=?", Long.class, eventId)).isEqualTo(1);
        // This exercises the real DB boundary with an acknowledged test publisher, not a real Kafka broker.
    }

    private UUID createEvent() {
        String token = UUID.randomUUID().toString();
        var command = new CreateReservationCommand("tenant-" + token, "key-1", UUID.randomUUID(), "txn-" + token, UUID.randomUUID().toString(), "corr-1", ReservationStatus.RESERVED, null, List.of(new ReservationLine("SKU-1", 2)));
        UUID eventId = service.createReservation(command).eventId();
        makeDue(eventId);
        return eventId;
    }

    private void makeDue(UUID eventId) {
        jdbc.update("UPDATE aegisflow.outbox_event SET next_attempt_at=clock_timestamp()-interval '1 second' WHERE event_id=?", eventId);
    }

    private OutboxClaim claim() { return store.claim(workerId(), LEASE).orElseThrow(); }
    private static String workerId() { return "worker-" + UUID.randomUUID(); }

    private void expire(OutboxClaim claim) {
        jdbc.update("UPDATE aegisflow.outbox_event SET lease_until=clock_timestamp()-interval '1 second' WHERE tenant_id=? AND outbox_id=?",
                claim.tenantId(), claim.outboxId());
    }

    private String status(OutboxClaim claim) {
        return jdbc.queryForObject("SELECT status FROM aegisflow.outbox_event WHERE tenant_id=? AND outbox_id=?",
                String.class, claim.tenantId(), claim.outboxId());
    }

    private void assertLeaseCleared(OutboxClaim claim) {
        assertThat(jdbc.queryForObject("SELECT lease_owner IS NULL AND lease_until IS NULL FROM aegisflow.outbox_event WHERE event_id=?",
                Boolean.class, claim.eventId())).isTrue();
    }

    private void assertCompletionsDenied(OutboxClaim claim) {
        assertThat(store.published(claim)).isFalse();
        assertThat(store.retry(claim, Duration.ofSeconds(1), "PUBLISH_TIMEOUT")).isFalse();
        assertThat(store.fail(claim, "INVALID_ENVELOPE")).isFalse();
    }

    private static OutboxClaim copyClaim(OutboxClaim source, String tenant, String worker, long fence) {
        return new OutboxClaim(tenant, source.outboxId(), source.eventId(), source.envelope(), source.payloadHash(),
                source.payloadHashVersion(), worker, fence, source.attempts());
    }
}
