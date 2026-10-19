package com.aegisflow.simulator.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

/** FM-003/004: actual PostgreSQL -> production relay -> authenticated Kafka -> Core-identity observer.
 * The observer is a test consumer, NOT the unfinished durable Core inbox. */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "aegisflow.order.relay.enabled=true", "aegisflow.order.relay.allowed-tenants=relay-kafka-it",
        "aegisflow.order.relay.max-per-poll=1"})
@Import(OrderKafkaDeliveryIT.ManualPolling.class)
@Timeout(value = 150, unit = TimeUnit.SECONDS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderKafkaDeliveryIT {
    private static final String TENANT = "relay-kafka-it";
    private static final String DATABASE = "aegisflow_order_kafka";
    private static final String RUNTIME = "aegisflow_order_runtime";
    private static final String MIGRATOR = "aegisflow_order_migrator";
    private static final String PASSWORD = "synthetic-kafka-gate-db-only";
    private static final Network NETWORK = Network.newNetwork();
    @Container static final OrderKafkaFixture KAFKA = new OrderKafkaFixture(NETWORK);
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse(
            "postgres:18.6@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336")
            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName(DATABASE).withUsername("inspection_admin").withPassword(PASSWORD);

    @DynamicPropertySource static void configure(DynamicPropertyRegistry properties) throws SQLException {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), PASSWORD);
             var sql = connection.createStatement()) {
            sql.execute("CREATE ROLE " + MIGRATOR + " LOGIN PASSWORD '" + PASSWORD + "'");
            sql.execute("CREATE ROLE " + RUNTIME + " LOGIN PASSWORD '" + PASSWORD + "'");
            sql.execute("REVOKE ALL ON DATABASE " + DATABASE + " FROM PUBLIC");
            sql.execute("GRANT CONNECT ON DATABASE " + DATABASE + " TO " + MIGRATOR + "," + RUNTIME);
            sql.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC");
            sql.execute("CREATE SCHEMA aegisflow AUTHORIZATION " + MIGRATOR);
        }
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", () -> RUNTIME);
        properties.add("spring.datasource.password", () -> PASSWORD);
        properties.add("spring.flyway.user", () -> MIGRATOR);
        properties.add("spring.flyway.password", () -> PASSWORD);
        properties.add("spring.flyway.placeholders.runtimeRole", () -> RUNTIME);
        properties.add("aegisflow.order.relay.bootstrap-servers", KAFKA::bootstrap);
        properties.add("aegisflow.order.relay.password", KAFKA::orderPassword);
    }

    // Instantiate real opt-in configuration/producer, but call poll deterministically to inject exact faults.
    // This does not claim coverage of wall-clock scheduled invocation or a separately packaged application.
    @TestConfiguration(proxyBeanMethods = false)
    static class ManualPolling {
        @Bean TaskScheduler taskScheduler() { return mock(TaskScheduler.class); }
    }

    @Autowired OrderPersistenceService orders;
    @Autowired OrderOutboxStore store;
    @Autowired OrderOutboxRelay configuredRelay;
    @Autowired KafkaOutboxPublisher publisher;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach void isolatePendingRows() {
        jdbc.update("UPDATE aegisflow.outbox_event SET status='FAILED',lease_owner=NULL,lease_until=NULL,last_error_code='TEST_RESET' WHERE status='PENDING'");
    }

    @Test @Order(1) void committedOutboxReachesRealKafkaBeforeDurablePublishedCompletion() throws Exception {
        var event = create();
        assertThat(status(event.id())).isEqualTo("PENDING");
        try (var consumer = observerAtEnd()) {
            // The real producer waits for broker ack. Observe DB state from another connection on both sides.
            var observed = new OrderOutboxRelay(store, (key, envelope) -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                assertThat(independentStatus(event.id())).isEqualTo("PENDING");
                publisher.publish(key, envelope);
                assertThat(independentStatus(event.id())).isEqualTo("PENDING");
            }, UUID.randomUUID().toString(), 1, Set.of(TENANT));
            assertThat(observed.poll()).isEqualTo(1);
            assertThat(independentStatus(event.id())).isEqualTo("PUBLISHED");
            assertThat(jdbc.queryForObject("SELECT published_at IS NOT NULL FROM aegisflow.outbox_event WHERE event_id=?", Boolean.class, event.id())).isTrue();
            var received = receive(consumer, event, 1);
            commitAndVerify(consumer, received);
            assertThat(configuredRelay.poll()).isZero();
            assertOneStateAndIntent(event);
        }
    }

    // Last because all three methods share this disposable broker, not because an outage is optional.
    @Test @Order(3) void brokerOutagePreservesCommittedIntentAndRecoversWithSameIdentityAfterRestart() throws Exception {
        // This marker proves broker storage survived the restart, independently of the later retry.
        var beforeRestart = create();
        try (var consumer = observerAtEnd()) {
            assertThat(configuredRelay.poll()).isEqualTo(1);
            var durableMarker = receive(consumer, beforeRestart, 1).getFirst();
            var pending = create();
            KAFKA.stopBrokerForOutage();
            Throwable primaryFailure = null;
            try {
                var beforeAttempt = jdbc.queryForObject("SELECT clock_timestamp()", java.time.OffsetDateTime.class);
                long start = System.nanoTime();
                assertThat(configuredRelay.poll()).isZero();
                var afterAttempt = jdbc.queryForObject("SELECT clock_timestamp()", java.time.OffsetDateTime.class);
                assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(35));
                assertThat(independentStatus(pending.id())).isEqualTo("PENDING");
                assertThat(jdbc.queryForObject("SELECT publish_attempts FROM aegisflow.outbox_event WHERE event_id=?", Integer.class, pending.id())).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT last_error_code FROM aegisflow.outbox_event WHERE event_id=?", String.class, pending.id())).isEqualTo("PUBLISH_UNCONFIRMED");
                assertThat(jdbc.queryForObject("SELECT published_at IS NULL AND lease_owner IS NULL FROM aegisflow.outbox_event WHERE event_id=?", Boolean.class, pending.id())).isTrue();
                assertThat(jdbc.queryForObject("SELECT next_attempt_at FROM aegisflow.outbox_event WHERE event_id=?", java.time.OffsetDateTime.class, pending.id()))
                        .isBetween(beforeAttempt.plusSeconds(1), afterAttempt.plusSeconds(1));
                // Deterministically prove the worker obeys future retry time, without a timing race on the 1s backoff.
                jdbc.update("UPDATE aegisflow.outbox_event SET next_attempt_at=clock_timestamp()+interval '30 seconds' WHERE event_id=?", pending.id());
                for (int i = 0; i < 3; i++) assertThat(configuredRelay.poll()).isZero();
                assertThat(jdbc.queryForObject("SELECT publish_attempts FROM aegisflow.outbox_event WHERE event_id=?", Integer.class, pending.id())).isEqualTo(1);
                assertOneStateAndIntent(pending);
            } catch (RuntimeException | Error failure) {
                primaryFailure = failure;
                throw failure;
            } finally {
                try { KAFKA.restartSameBroker(); }
                catch (RuntimeException restartFailure) {
                    if (primaryFailure != null) primaryFailure.addSuppressed(restartFailure);
                    else throw restartFailure;
                }
            }
            consumer.seek(new TopicPartition(durableMarker.topic(), durableMarker.partition()), durableMarker.offset());
            assertThat(receive(consumer, beforeRestart, 1).getFirst().value()).isEqualTo(durableMarker.value());
            jdbc.update("UPDATE aegisflow.outbox_event SET next_attempt_at=clock_timestamp()-interval '1 second' WHERE event_id=?", pending.id());
            assertThat(configuredRelay.poll()).isEqualTo(1);
            commitAndVerify(consumer, receive(consumer, pending, 1));
            assertThat(independentStatus(pending.id())).isEqualTo("PUBLISHED");
            assertThat(jdbc.queryForObject("SELECT publish_attempts FROM aegisflow.outbox_event WHERE event_id=?", Integer.class, pending.id())).isEqualTo(2);
            assertOneStateAndIntent(pending);
        }
    }

    @Test @Order(2) void actualKafkaAckThenLocalCompletionFailureRedeliversOriginalEvent() throws Exception {
        var event = create();
        try (var consumer = observerAtEnd()) {
            var crashAfterAck = new OrderOutboxStore(jdbc, transactions) {
                @Override boolean published(OutboxClaim claim) {
                    // A real completion transaction updates, then rolls back: broker ack is already durable.
                    new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx -> {
                        jdbc.update("UPDATE aegisflow.outbox_event SET status='PUBLISHED',published_at=clock_timestamp(),lease_owner=NULL,lease_until=NULL WHERE tenant_id=? AND outbox_id=? AND fencing_token=?", claim.tenantId(), claim.outboxId(), claim.fencingToken());
                        throw new IllegalStateException("injected completion transaction failure");
                    });
                    return false;
                }
            };
            var crashing = new OrderOutboxRelay(crashAfterAck, publisher, UUID.randomUUID().toString(), 1, Set.of(TENANT));
            assertThatThrownBy(crashing::poll).isInstanceOf(IllegalStateException.class);
            var first = receive(consumer, event, 1).getFirst();
            assertThat(independentStatus(event.id())).isEqualTo("PENDING");
            // Expire only this disposable lease; avoid a 60s sleep and do not alter the envelope/identity.
            jdbc.update("UPDATE aegisflow.outbox_event SET lease_until=clock_timestamp()-interval '1 second' WHERE event_id=?", event.id());
            assertThat(configuredRelay.poll()).isEqualTo(1);
            var second = receive(consumer, event, 1).getFirst();
            assertThat(second.partition()).isEqualTo(first.partition());
            assertThat(second.offset()).isGreaterThan(first.offset());
            assertThat(second.key()).isEqualTo(first.key());
            assertThat(second.value()).isEqualTo(first.value());
            assertThat(independentStatus(event.id())).isEqualTo("PUBLISHED");
            assertThat(jdbc.queryForObject("SELECT publish_attempts FROM aegisflow.outbox_event WHERE event_id=?", Integer.class, event.id())).isEqualTo(2);
            commitAndVerify(consumer, List.of(second));
            assertOneStateAndIntent(event);
        }
    }

    private Event create() {
        String transaction = UUID.randomUUID().toString();
        var command = new CreateOrder(TENANT, UUID.randomUUID(), transaction, "corr-" + transaction,
                "key-" + transaction, new BigDecimal("12.00"), "INR", List.of(new CreateOrder.Line("SKU-1", 2)));
        UUID id = orders.create(command).eventId();
        jdbc.update("UPDATE aegisflow.outbox_event SET next_attempt_at=clock_timestamp()-interval '1 second' WHERE event_id=?", id);
        return new Event(id, command.orderId(), TENANT + ":" + transaction,
                jdbc.queryForObject("SELECT payload_hash FROM aegisflow.outbox_event WHERE event_id=?", String.class, id));
    }

    private KafkaConsumer<String, String> observerAtEnd() {
        var properties = KAFKA.coreProperties();
        properties.put("key.deserializer", StringDeserializer.class);
        properties.put("value.deserializer", StringDeserializer.class);
        properties.put("group.id", "aegisflow-core-relay-it-" + UUID.randomUUID());
        properties.put("enable.auto.commit", false);
        properties.put("allow.auto.create.topics", false);
        properties.put("auto.offset.reset", "none");
        properties.put("default.api.timeout.ms", 10000);
        properties.put("request.timeout.ms", 5000);
        var consumer = new KafkaConsumer<String, String>(properties);
        var partitions = List.of(new TopicPartition(OrderOutboxRelay.TOPIC, 0), new TopicPartition(OrderOutboxRelay.TOPIC, 1), new TopicPartition(OrderOutboxRelay.TOPIC, 2));
        try {
            consumer.assign(partitions);
            consumer.endOffsets(partitions).forEach(consumer::seek);
            return consumer;
        } catch (RuntimeException failure) { consumer.close(Duration.ofSeconds(5)); throw failure; }
    }

    private List<ConsumerRecord<String, String>> receive(KafkaConsumer<String, String> consumer, Event expected, int count) {
        var records = new ArrayList<ConsumerRecord<String, String>>();
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (records.size() < count && System.nanoTime() < deadline) {
            for (var record : consumer.poll(Duration.ofMillis(300))) {
                var tree = JsonMapper.builder().build().readTree(record.value());
                if (!expected.id().toString().equals(tree.path("eventId").asText())) continue;
                assertThat(record.key()).isEqualTo(expected.key());
                assertThat(tree.path("tenantId").asText()).isEqualTo(TENANT);
                assertThat(tree.path("sourceSystem").asText()).isEqualTo("order-service");
                assertThat(CanonicalJson.hash(record.value())).isEqualTo(expected.hash());
                records.add(record);
            }
        }
        assertThat(records).hasSize(count);
        return records;
    }

    private void commitAndVerify(KafkaConsumer<String, String> consumer, List<ConsumerRecord<String, String>> records) {
        var last = records.getLast();
        var partition = new TopicPartition(last.topic(), last.partition());
        consumer.commitSync(java.util.Map.of(partition, new OffsetAndMetadata(last.offset() + 1)));
        assertThat(consumer.committed(Set.of(partition)).get(partition).offset()).isEqualTo(last.offset() + 1);
    }

    private String status(UUID id) {
        return jdbc.queryForObject("SELECT status FROM aegisflow.outbox_event WHERE event_id=?", String.class, id);
    }

    private String independentStatus(UUID id) throws SQLException {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), RUNTIME, PASSWORD);
             var statement = connection.prepareStatement("SELECT status FROM aegisflow.outbox_event WHERE event_id=?")) {
            statement.setObject(1, id);
            try (var row = statement.executeQuery()) { assertThat(row.next()).isTrue(); return row.getString(1); }
        }
    }

    private void assertOneStateAndIntent(Event event) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aegisflow.orders WHERE tenant_id=? AND order_id=?", Long.class, TENANT, event.orderId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aegisflow.outbox_event WHERE event_id=? AND payload_hash=?", Long.class, event.id(), event.hash())).isEqualTo(1);
    }

    private record Event(UUID id, UUID orderId, String key, String hash) { }
}
