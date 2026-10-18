package com.aegisflow.simulator.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

class OrderOutboxRelayTests {
    private final OrderOutboxStore store = mock(OrderOutboxStore.class);
    private final OrderOutboxRelay.Publisher publisher = mock(OrderOutboxRelay.Publisher.class);

    static OutboxClaim fixture() {
        var command = new CreateOrder("tenant-a", UUID.randomUUID(), "txn-a", "corr-a", "key-a",
                new BigDecimal("12.00"), "INR", List.of(new CreateOrder.Line("SKU", 2)));
        var eventId = UUID.randomUUID();
        var envelope = OrderOutbox.envelope(command, eventId, 1, Instant.parse("2026-10-17T00:00:00Z"));
        return new OutboxClaim("tenant-a", UUID.randomUUID(), eventId, envelope, CanonicalJson.hash(envelope),
                CanonicalJson.PROFILE, "worker-a", 1, 1);
    }

    private OrderOutboxRelay relay(int limit) {
        return new OrderOutboxRelay(store, publisher, "worker-a", limit, Set.of("tenant-a"));
    }

    @Test void canonicalizesJsonbRepresentationWithoutChangingIdentityOrHash() {
        var original = fixture();
        String pretty = JsonMapper.builder().build().writerWithDefaultPrettyPrinter()
                .writeValueAsString(JsonMapper.builder().build().readValue(original.envelope(), Map.class));
        var reformatted = new OutboxClaim(original.tenantId(), original.outboxId(), original.eventId(), pretty,
                original.payloadHash(), original.payloadHashVersion(), original.workerId(), 1, 1);
        var message = OrderOutboxRelay.message(reformatted);
        assertThat(message.envelope()).isEqualTo(original.envelope());
        assertThat(message.key()).isEqualTo("tenant-a:txn-a");
        assertThat(message.toString()).doesNotContain(original.eventId().toString(), "tenant-a");
        assertThat(original.toString()).doesNotContain(original.envelope());
    }

    @Test void rejectsTamperedHashAndIdentityAndUnsupportedVersion() {
        var original = fixture();
        for (String value : List.of(original.envelope().replace("12.00", "13.00"),
                original.envelope().replace("order-service", "payment-service"),
                original.envelope().replace("tenant-a", "tenant-b"),
                original.envelope().replace(original.eventId().toString(), UUID.randomUUID().toString()),
                original.envelope().replace("OrderCreated", "OrderConfirmed"),
                original.envelope().replace("1.0", "2.0"))) {
            var changed = new OutboxClaim(original.tenantId(), original.outboxId(), original.eventId(), value,
                    CanonicalJson.hash(value), original.payloadHashVersion(), original.workerId(), 1, 1);
            // A changed amount with an unchanged digest must fail; other cases test identity even with a new digest.
            if (value.contains("13.00")) changed = new OutboxClaim(original.tenantId(), original.outboxId(), original.eventId(),
                    value, original.payloadHash(), original.payloadHashVersion(), original.workerId(), 1, 1);
            var invalid = changed;
            assertThatThrownBy(() -> OrderOutboxRelay.message(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void rejectsOversizedStoredEnvelopeBeforeParsing() {
        var original = fixture();
        var large = new OutboxClaim(original.tenantId(), original.outboxId(), original.eventId(), " ".repeat(262_145),
                original.payloadHash(), original.payloadHashVersion(), original.workerId(), 1, 1);
        assertThatThrownBy(() -> OrderOutboxRelay.message(large)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void acknowledgementPrecedesFencedPublishedUpdate() throws Exception {
        var claim = fixture();
        when(store.claim(anyString(), any())).thenReturn(Optional.of(claim), Optional.empty());
        when(store.published(claim)).thenReturn(true);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            verify(store, never()).published(any());
            return null;
        }).when(publisher).publish(anyString(), anyString());
        assertThat(relay(10).poll()).isEqualTo(1);
        var order = inOrder(store, publisher);
        order.verify(store).claim("worker-a", Duration.ofSeconds(60));
        order.verify(publisher).publish("tenant-a:txn-a", claim.envelope());
        order.verify(store).published(claim);
    }

    @Test void unconfirmedSendRetainsIntentAndSchedulesBoundedRetry() throws Exception {
        var claim = fixture();
        when(store.claim(anyString(), any())).thenReturn(Optional.of(claim));
        doThrow(new java.util.concurrent.TimeoutException("sensitive detail must not be persisted"))
                .when(publisher).publish(anyString(), anyString());
        assertThat(relay(1).poll()).isZero();
        verify(store).retry(claim, Duration.ofSeconds(1), "PUBLISH_UNCONFIRMED");
        verify(store, never()).published(any());
        verify(store, never()).fail(any(), anyString());
    }

    @Test void staleCompletionIsNotReportedAsPublished() {
        var claim = fixture();
        when(store.claim(anyString(), any())).thenReturn(Optional.of(claim));
        when(store.published(claim)).thenReturn(false);
        assertThat(relay(1).poll()).isZero();
    }

    @Test void crashWindowAfterAcknowledgementPropagatesDatabaseFailure() throws Exception {
        var claim = fixture();
        when(store.claim(anyString(), any())).thenReturn(Optional.of(claim));
        when(store.published(claim)).thenThrow(new IllegalStateException("injected DB completion failure"));
        assertThatThrownBy(() -> relay(1).poll()).isInstanceOf(IllegalStateException.class);
        verify(publisher).publish("tenant-a:txn-a", claim.envelope());
        verify(store, never()).retry(any(), any(), anyString());
    }

    @Test void boundedPollClaimsIncrementallyInsteadOfLeasingAnEntireBatch() throws Exception {
        var claim = fixture();
        when(store.claim(anyString(), any())).thenReturn(Optional.of(claim));
        when(store.published(claim)).thenReturn(true);
        assertThat(relay(2).poll()).isEqualTo(2);
        var order = inOrder(store, publisher);
        for (int i = 0; i < 2; i++) {
            order.verify(store).claim("worker-a", OrderOutboxRelay.LEASE);
            order.verify(publisher).publish("tenant-a:txn-a", claim.envelope());
            order.verify(store).published(claim);
        }
        verify(store, times(2)).claim(anyString(), any());
    }

    @Test void malformedIntentIsFailedWithoutBlockingNextValidRow() throws Exception {
        var valid = fixture();
        var malformed = new OutboxClaim(valid.tenantId(), UUID.randomUUID(), UUID.randomUUID(), "{",
                "0".repeat(64), CanonicalJson.PROFILE, "worker-a", 1, 1);
        when(store.claim(anyString(), any())).thenReturn(Optional.of(malformed), Optional.of(valid));
        when(store.published(valid)).thenReturn(true);
        assertThat(relay(2).poll()).isEqualTo(1);
        verify(store).fail(malformed, "INVALID_STORED_EVENT");
        verify(publisher, times(1)).publish("tenant-a:txn-a", valid.envelope());
    }

    @Test void unconfiguredTenantNeverReachesKafka() throws Exception {
        var claim = fixture();
        when(store.claim(anyString(), any())).thenReturn(Optional.of(claim));
        var relay = new OrderOutboxRelay(store, publisher, "worker-a", 1, Set.of("different-tenant"));
        assertThat(relay.poll()).isZero();
        verify(store).fail(claim, "TENANT_NOT_ALLOWED");
        verifyNoInteractions(publisher);
    }

    @Test void rejectsAmbientTransactionBeforeClaimingOrSending() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThatThrownBy(() -> relay(1).poll()).isInstanceOf(IllegalStateException.class); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verifyNoInteractions(store, publisher);
    }

    @Test void interruptionLeavesClaimForExpiryAndRestoresInterruptFlag() throws Exception {
        var claim = fixture();
        when(store.claim(anyString(), any())).thenReturn(Optional.of(claim));
        doThrow(new InterruptedException()).when(publisher).publish(anyString(), anyString());
        try {
            assertThat(relay(10).poll()).isZero();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(store, never()).published(any());
            verify(store, never()).retry(any(), any(), anyString());
        } finally { Thread.interrupted(); }
    }

    @Test void backoffSaturatesWithoutOverflowOrAbandoningIntent() {
        assertThat(OrderOutboxRelay.backoff(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(OrderOutboxRelay.backoff(3)).isEqualTo(Duration.ofSeconds(4));
        assertThat(OrderOutboxRelay.backoff(7)).isEqualTo(Duration.ofSeconds(60));
        assertThat(OrderOutboxRelay.backoff(Integer.MAX_VALUE)).isEqualTo(Duration.ofSeconds(60));
        assertThatThrownBy(() -> OrderOutboxRelay.backoff(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> relay(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> relay(101)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void foundationEvenWithOptInAndPlainPostgresCreateNoKafkaOrRelayBeans() {
        for (String profile : List.of("foundation", "postgres")) {
            try (var context = new AnnotationConfigApplicationContext()) {
                context.getEnvironment().setActiveProfiles(profile);
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                        "aegisflow.order.relay.enabled", profile.equals("foundation") ? "true" : "false")));
                context.register(OrderRelayConfiguration.class);
                context.refresh();
                assertThat(context.getBeansOfType(OrderOutboxRelay.class)).isEmpty();
                assertThat(context.getBeansOfType(KafkaOutboxPublisher.class)).isEmpty();
            }
        }
    }

    @Test void producerConfigurationPinsOrderIdentityAndBoundsDelivery() {
        var props = OrderRelayConfiguration.producerProperties("127.0.0.1:19092", "a".repeat(64));
        assertThat(props).containsEntry("acks", "all").containsEntry("enable.idempotence", true)
                .containsEntry("max.block.ms", 5000).containsEntry("delivery.timeout.ms", 15000);
        assertThat(props.get("sasl.jaas.config").toString()).contains("username=\"order\"");
        assertThat(OrderRelayConfiguration.producerProperties("kafka:9092", "a".repeat(64))).isNotEmpty();
        for (String endpoint : List.of("remote:9092", "127.0.0.1:99999", "kafka:80", "kafka:9092,evil:9092"))
            assertThatThrownBy(() -> OrderRelayConfiguration.producerProperties(endpoint, "a".repeat(64)))
                    .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OrderRelayConfiguration.producerProperties("kafka:9092", "bad\"credential"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
