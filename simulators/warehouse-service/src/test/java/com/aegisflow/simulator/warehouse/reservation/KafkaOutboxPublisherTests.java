package com.aegisflow.simulator.warehouse.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

class KafkaOutboxPublisherTests {
    @Test void realClientTestDoublePreservesExactTopicKeyAndPayload() throws Exception {
        var producer = new MockProducer<String, String>(true, null, new StringSerializer(), new StringSerializer());
        try (var publisher = new KafkaOutboxPublisher(producer)) {
            publisher.publish("tenant:transaction", "{\"eventId\":\"stable\"}");
            assertThat(producer.history()).hasSize(1);
            var sent = producer.history().getFirst();
            assertThat(sent.topic()).isEqualTo(WarehouseOutboxRelay.TOPIC);
            assertThat(sent.key()).isEqualTo("tenant:transaction");
            assertThat(sent.value()).isEqualTo("{\"eventId\":\"stable\"}");
        }
        assertThat(producer.closed()).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test void publisherWaitsForAcknowledgementRatherThanSendEnqueue() throws Exception {
        Producer<String, String> producer = mock(Producer.class);
        var acknowledgement = new CompletableFuture<RecordMetadata>();
        var sent = new CountDownLatch(1);
        when(producer.send(any())).thenAnswer(invocation -> { sent.countDown(); return acknowledgement; });
        try (var publisher = new KafkaOutboxPublisher(producer); var executor = Executors.newSingleThreadExecutor()) {
            var result = executor.submit(() -> { publisher.publish("key", "payload"); return true; });
            try {
                assertThat(sent.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(result.isDone()).isFalse();
                acknowledgement.complete(new RecordMetadata(new TopicPartition(WarehouseOutboxRelay.TOPIC, 0), 4, 0, 1, 3, 7));
                assertThat(result.get(5, TimeUnit.SECONDS)).isTrue();
            } finally { acknowledgement.completeExceptionally(new IllegalStateException("test cleanup")); }
        }
        verify(producer).close(Duration.ofSeconds(5));
    }

    @Test void brokerFailureDoesNotReturnSuccess() {
        var producer = new MockProducer<String, String>(true, null, new StringSerializer(), new StringSerializer());
        producer.sendException = new org.apache.kafka.common.errors.TimeoutException("synthetic unavailable broker");
        try (var publisher = new KafkaOutboxPublisher(producer)) {
            assertThatThrownBy(() -> publisher.publish("key", "payload"))
                    .isInstanceOf(org.apache.kafka.common.errors.TimeoutException.class);
        }
    }
}
