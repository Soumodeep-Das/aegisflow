package com.aegisflow.simulator.warehouse.reservation;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

final class KafkaOutboxPublisher implements WarehouseOutboxRelay.Publisher, AutoCloseable {
    private final Producer<String, String> producer;
    KafkaOutboxPublisher(Producer<String, String> producer) { this.producer = producer; }

    @Override public void publish(String key, String envelope) throws Exception {
        // send can block up to max.block.ms, then wait is bounded separately, below the 60s lease.
        producer.send(new ProducerRecord<>(WarehouseOutboxRelay.TOPIC, key, envelope)).get(20, TimeUnit.SECONDS);
    }

    @Override public void close() { producer.close(Duration.ofSeconds(5)); }
}
