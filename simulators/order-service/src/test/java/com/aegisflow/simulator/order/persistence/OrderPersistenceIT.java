package com.aegisflow.simulator.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderPersistenceIT {
    private static final String RUNTIME = "aegisflow_order_runtime";
    private static final String MIGRATOR = "aegisflow_order_migrator";
    private static final String PASSWORD = "synthetic-test-only";
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            "postgres:18.6@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336")
            .withDatabaseName("aegisflow_order").withUsername("inspection_admin").withPassword(PASSWORD);
    @DynamicPropertySource static void configure(DynamicPropertyRegistry properties) throws SQLException {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + MIGRATOR + " LOGIN PASSWORD '" + PASSWORD + "'");
            statement.execute("CREATE ROLE " + RUNTIME + " LOGIN PASSWORD '" + PASSWORD + "'");
            statement.execute("REVOKE ALL ON DATABASE aegisflow_order FROM PUBLIC");
            statement.execute("GRANT CONNECT ON DATABASE aegisflow_order TO " + MIGRATOR + ", " + RUNTIME);
            statement.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC");
            statement.execute("CREATE SCHEMA aegisflow AUTHORIZATION " + MIGRATOR);
        }
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", () -> RUNTIME);
        properties.add("spring.datasource.password", () -> PASSWORD);
        properties.add("spring.flyway.user", () -> MIGRATOR);
        properties.add("spring.flyway.password", () -> PASSWORD);
        properties.add("spring.flyway.placeholders.runtimeRole", () -> RUNTIME);
    }
    @Autowired OrderPersistenceService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderRepository orders;
    @Autowired Flyway flyway;
    @MockitoSpyBean OrderOutbox outbox;
    @LocalServerPort int port;

    private CreateOrder command() {
        String token = UUID.randomUUID().toString();
        return new CreateOrder("tenant-" + token, UUID.randomUUID(), "txn-" + token, "corr-1", "key-1",
                new BigDecimal("1499.00"), "INR", List.of(new CreateOrder.Line("SKU-1",2)));
    }
    private long count(String table, String tenant) {
        return jdbc.queryForObject("SELECT count(*) FROM aegisflow." + table + " WHERE tenant_id=?", Long.class, tenant);
    }
    @Test void migrationsAndPostgresHealthWork() throws Exception {
        assertThat(flyway.info().applied()).hasSize(1);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/actuator/health")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }
    @Test void commitPersistsStateAndContractEnvelopeTogether() {
        var command = command(); var result = service.create(command);
        assertThat(result.version()).isEqualTo(1);
        assertThat(count("orders",command.tenantId())).isEqualTo(1);
        assertThat(count("order_lines",command.tenantId())).isEqualTo(1);
        assertThat(count("idempotency_record",command.tenantId())).isEqualTo(1);
        String envelope = jdbc.queryForObject("SELECT envelope::text FROM aegisflow.outbox_event WHERE tenant_id=?", String.class, command.tenantId());
        var json = JsonMapper.builder().build().readTree(envelope);
        assertThat(json.properties().stream().map(java.util.Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder("tenantId","eventId","sourceSystem","businessTransactionId","schemaVersion","occurredAt","correlationId","eventType","details");
        assertThat(json.path("eventId").asText()).isEqualTo(result.eventId().toString());
        assertThat(json.path("eventType").asText()).isEqualTo("OrderCreated");
        assertThat(json.path("details").path("amount").asText()).isEqualTo("1499.00");
        assertThat(json.path("details").path("orderVersion").asLong()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM aegisflow.outbox_event WHERE tenant_id=?", String.class, command.tenantId())).isEqualTo("PENDING");
    }
    @Test void failureAfterOutboxInsertRollsBackAllRows() {
        var command = command();
        doAnswer(invocation -> { invocation.callRealMethod(); throw new IllegalStateException("injected after durable insert before commit"); })
                .when(outbox).append(any(),any(),anyLong(),any());
        assertThatThrownBy(() -> service.create(command)).isInstanceOf(IllegalStateException.class);
        for (String table : List.of("orders","order_lines","outbox_event","idempotency_record"))
            assertThat(count(table,command.tenantId())).as(table).isZero();
    }
    @Test void sameRequestReplaysStoredIdentityAndChangedRequestConflicts() {
        var command = command(); var result = service.create(command);
        assertThat(service.create(command)).isEqualTo(result);
        var changed = new CreateOrder(command.tenantId(), command.orderId(), command.businessTransactionId(), "corr-retry", command.idempotencyKey(),
                new BigDecimal("1500.00"), command.currency(), command.lines());
        assertThatThrownBy(() -> service.create(changed)).isInstanceOf(OrderPersistenceService.IdempotencyConflictException.class);
        assertThat(count("orders",command.tenantId())).isEqualTo(1);
        assertThat(count("outbox_event",command.tenantId())).isEqualTo(1);
    }
    @Test void concurrentReplayCommitsOneStateAndOneStableEvent() throws Exception {
        var command = command(); var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { gate.await(); return service.create(command); });
            var second = executor.submit(() -> { gate.await(); return service.create(command); });
            gate.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS)).isEqualTo(second.get(30, TimeUnit.SECONDS));
        }
        assertThat(count("orders",command.tenantId())).isEqualTo(1);
        assertThat(count("outbox_event",command.tenantId())).isEqualTo(1);
    }
    @Test void tenantForeignKeysAndEventUniquenessAreDatabaseEnforced() throws Exception {
        var command = command(); var result = service.create(command);
        assertThatThrownBy(() -> service.status("different-tenant", command.orderId())).isInstanceOf(java.util.NoSuchElementException.class);
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), PASSWORD);
             var statement = connection.prepareStatement("INSERT INTO aegisflow.order_lines VALUES (?,?,1,'other',1)")) {
            statement.setString(1,"different-tenant"); statement.setObject(2,command.orderId());
            assertThatThrownBy(statement::executeUpdate).isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("23503");
        }
        var otherTenant = command(); service.create(otherTenant);
        assertThatThrownBy(() -> outbox.append(otherTenant,result.eventId(),2,Instant.now()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                .hasMessageContaining("uq_order_outbox_global_event");
    }
    @Test void optimisticLockPreventsStaleAggregateOverwrite() {
        var command = command(); service.create(command);
        var key = new OrderKey(command.tenantId(),command.orderId());
        var first = orders.findById(key).orElseThrow(); var stale = orders.findById(key).orElseThrow();
        first.touch(Instant.now()); orders.saveAndFlush(first);
        stale.touch(Instant.now());
        assertThatThrownBy(() -> orders.saveAndFlush(stale)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(orders.findById(key).orElseThrow().businessVersion()).isEqualTo(2);
    }
    @Test void runtimeCannotAlterImmutableIntentOrCreateSchema() throws Exception {
        var command = command(); service.create(command);
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), RUNTIME, PASSWORD);
             var statement = connection.createStatement()) {
            for (String sql : List.of("CREATE TABLE aegisflow.forbidden(id integer)",
                    "UPDATE aegisflow.outbox_event SET envelope='{}'::jsonb", "UPDATE aegisflow.idempotency_record SET result_code='CREATED'",
                    "DELETE FROM aegisflow.flyway_schema_history")) {
                assertThatThrownBy(() -> statement.execute(sql)).isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("42501");
            }
        }
        assertThat(jdbc.update("UPDATE aegisflow.outbox_event SET publish_attempts=publish_attempts+1 WHERE tenant_id=?",command.tenantId())).isEqualTo(1);
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), MIGRATOR, PASSWORD);
             var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.execute("UPDATE aegisflow.outbox_event SET envelope='{}'::jsonb"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("immutable");
        }
    }
}
