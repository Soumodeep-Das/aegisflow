package com.aegisflow.core.eventing.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

class KafkaInboxConsumerTests {
    private final Consumer<byte[],byte[]> client = mock(Consumer.class);
    private final TopicPartition partition = new TopicPartition("aegisflow.order.events.v1",0);
    private final ConsumerRecord<byte[],byte[]> record = new ConsumerRecord<>(partition.topic(),0,7,new byte[0],new byte[0]);
    private void fetched() {
        when(client.poll(Duration.ofMillis(500))).thenReturn(new ConsumerRecords<>(Map.of(partition,List.of(record)),Map.of()));
        when(client.assignment()).thenReturn(Set.of(partition));
    }
    @Test void commitsOnlyAfterDurableProcessingReturns() {
        fetched();
        var worker = new KafkaInboxConsumer(client, value -> {
            verify(client, never()).commitSync(anyMap(),any(Duration.class)); return "ACCEPTED";
        });
        assertThat(worker.poll()).isEqualTo(1);
        verify(client).commitSync(Map.of(partition,new OffsetAndMetadata(8)),Duration.ofSeconds(5));
    }
    @Test void databaseFailureRewindsAndNeverAcknowledges() {
        fetched(); var worker = new KafkaInboxConsumer(client, value -> { throw new IllegalStateException("database unavailable"); });
        assertThatThrownBy(worker::poll).isInstanceOf(IllegalStateException.class);
        verify(client, never()).commitSync(anyMap(),any(Duration.class));
        verify(client).seek(partition,7);
    }
    @Test void ambiguousOffsetCommitRewindsForIdempotentReplay() {
        fetched(); doThrow(new org.apache.kafka.common.errors.TimeoutException()).when(client).commitSync(anyMap(),any(Duration.class));
        var worker = new KafkaInboxConsumer(client, value -> "DUPLICATE");
        assertThatThrownBy(worker::poll).isInstanceOf(org.apache.kafka.common.errors.TimeoutException.class);
        verify(client).seek(partition,7);
    }
    @Test void invalidBatchIsRewoundRatherThanPartiallyLost() {
        when(client.poll(any(Duration.class))).thenReturn(new ConsumerRecords<>(Map.of(partition,List.of(record,record)),Map.of()));
        var worker = new KafkaInboxConsumer(client,value -> { throw new AssertionError("Must not process"); });
        assertThatThrownBy(worker::poll).hasMessageContaining("max.poll.records");
        verify(client).seek(partition,7);
    }
    @Test void productionConfigurationRequiresScopedIdentityAndManualAcknowledgement() {
        var props = InboxConfiguration.consumerProperties("127.0.0.1:19092","a".repeat(64),"aegisflow-core-inbox-it");
        assertThat(props).containsEntry("enable.auto.commit",false).containsEntry("max.poll.records",1)
                .containsEntry("auto.offset.reset","earliest");
        assertThat(props.get("sasl.jaas.config").toString()).contains("username=\"core\"");
        assertThatThrownBy(() -> InboxConfiguration.consumerProperties("remote:9092","a".repeat(64),"aegisflow-core-test")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InboxConfiguration.consumerProperties("127.0.0.1:19092","a".repeat(64),"unscoped")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IntakePolicy(Map.of("order-service",Set.of()))).isInstanceOf(IllegalArgumentException.class);
    }
}
