package com.aegisflow.simulator.warehouse;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
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

/** Fixture safety gate only; this is not persistent-deployment or Kafka acceptance. */
@Testcontainers
@ActiveProfiles("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "aegisflow.warehouse.relay.enabled=false")
class LocalDemoIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18.6@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("warehouse_p02").withUsername("warehouse_test_admin")
            .withPassword("synthetic-admin-test-only")
            .withInitScript("db/test/warehouse-test-bootstrap.sql");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", () -> "aegisflow_warehouse_runtime");
        properties.add("spring.datasource.password", () -> "synthetic-runtime-test-only");
        properties.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        properties.add("spring.flyway.user", () -> "aegisflow_warehouse_migrator");
        properties.add("spring.flyway.password", () -> "synthetic-migrator-test-only");
        properties.add("spring.flyway.placeholders.runtimeRole", () -> "aegisflow_warehouse_runtime");
    }

    @Autowired ConfigurableApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    record Fixture(String tenant, String tx, UUID order, UUID reservation) {
        static Fixture fresh() {
            return new Fixture("demo-" + UUID.randomUUID(), "missing-" + UUID.randomUUID(),
                    UUID.randomUUID(), UUID.randomUUID());
        }
    }

    UUID execute(Fixture fixture, String mode) {
        return LocalDemo.execute(context, fixture.tenant(), fixture.tx(), fixture.order(),
                fixture.reservation(), mode);
    }

    int count(String table, Fixture fixture) {
        return jdbc.queryForObject("SELECT count(*) FROM aegisflow." + table + " WHERE tenant_id=?",
                Integer.class, fixture.tenant());
    }

    @Test
    void holdReplayAndRepeatedReleasePreserveOriginalEventAndOccurrence() {
        var fixture = Fixture.fresh();
        var event = execute(fixture, "hold");
        var original = jdbc.queryForMap("SELECT envelope::text, payload_hash, created_at FROM aegisflow.outbox_event WHERE tenant_id=? AND event_id=?",
                fixture.tenant(), event);
        assertThat(jdbc.queryForObject("SELECT next_attempt_at > clock_timestamp()+interval '23 hours' FROM aegisflow.outbox_event WHERE tenant_id=? AND event_id=?",
                Boolean.class, fixture.tenant(), event)).isTrue();
        assertThat(execute(fixture, "hold")).isEqualTo(event);
        assertThat(execute(fixture, "release")).isEqualTo(event);
        assertThat(execute(fixture, "release")).isEqualTo(event);
        assertThat(jdbc.queryForMap("SELECT envelope::text, payload_hash, created_at FROM aegisflow.outbox_event WHERE tenant_id=? AND event_id=?",
                fixture.tenant(), event)).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT status='PENDING' AND next_attempt_at<=clock_timestamp() AND publish_attempts=0 FROM aegisflow.outbox_event WHERE tenant_id=? AND event_id=?",
                Boolean.class, fixture.tenant(), event)).isTrue();
        assertThat(count("reservations", fixture)).isEqualTo(1);
        assertThat(count("outbox_event", fixture)).isEqualTo(1);
    }

    @Test
    void outerRollbackRemovesBusinessStateOutboxAndIdempotencyTogether() {
        var fixture = Fixture.fresh();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
            execute(fixture, "hold");
            throw new IllegalStateException("intentional fixture rollback");
        })).isInstanceOf(IllegalStateException.class).hasMessage("intentional fixture rollback");
        for (var table : java.util.List.of("reservations", "reservation_lines", "outbox_event", "idempotency_record")) {
            assertThat(count(table, fixture)).as(table).isZero();
        }
    }

    @Test
    void releaseCannotTouchSameAggregateInAnotherTenant() {
        var first = Fixture.fresh();
        var second = new Fixture("demo-" + UUID.randomUUID(), first.tx(), first.order(), first.reservation());
        var firstEvent = execute(first, "hold");
        var secondEvent = execute(second, "hold");
        assertThat(secondEvent).isNotEqualTo(firstEvent);
        execute(first, "release");
        assertThat(jdbc.queryForObject("SELECT next_attempt_at > clock_timestamp()+interval '23 hours' FROM aegisflow.outbox_event WHERE tenant_id=? AND event_id=?",
                Boolean.class, second.tenant(), secondEvent)).isTrue();
    }

    @Test
    void releaseWithoutCommittedOutcomeFailsWithoutCreatingAnything() {
        var fixture = Fixture.fresh();
        assertThatThrownBy(() -> execute(fixture, "release"))
                .isInstanceOf(org.springframework.dao.EmptyResultDataAccessException.class);
        assertThat(count("reservations", fixture)).isZero();
        assertThat(count("outbox_event", fixture)).isZero();
    }
}
