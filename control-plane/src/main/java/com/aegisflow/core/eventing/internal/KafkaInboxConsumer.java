package com.aegisflow.core.eventing.internal;

import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.scheduling.annotation.Scheduled;

/** Serial bounded polling: one record per poll avoids advancing past an unprocessed partition/batch member. */
public final class KafkaInboxConsumer implements AutoCloseable {
    private final Consumer<byte[], byte[]> consumer;
    private final Function<ConsumerRecord<byte[], byte[]>, String> durableProcessing;

    public KafkaInboxConsumer(Consumer<byte[], byte[]> consumer, Function<ConsumerRecord<byte[], byte[]>, String> processing) {
        this.consumer = consumer; this.durableProcessing = processing;
        consumer.subscribe(IntakePolicy.SOURCES.keySet());
    }
    public synchronized int poll() {
        var records = consumer.poll(Duration.ofMillis(500));
        if (records.count() > 1) {
            // Fail closed if a caller supplied an incompatible consumer configuration.
            records.partitions().forEach(tp -> consumer.seek(tp, records.records(tp).getFirst().offset()));
            throw new IllegalStateException("Inbox consumer requires max.poll.records=1");
        }
        for (var record : records) {
            var partition = new TopicPartition(record.topic(), record.partition());
            try {
                durableProcessing.apply(record);
                consumer.commitSync(Map.of(partition, new OffsetAndMetadata(record.offset() + 1)), Duration.ofSeconds(5));
            } catch (RuntimeException failure) {
                // Never commit the fetched position on failure. Restart also resumes at the broker's committed offset.
                if (consumer.assignment().contains(partition)) consumer.seek(partition, record.offset());
                throw failure;
            }
        }
        return records.count();
    }
    @Scheduled(fixedDelay = 1000)
    public void tick() {
        try { poll(); }
        catch (RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger(KafkaInboxConsumer.class)
                    .warn("Core inbox pass failed; offset not confirmed; inspect database and broker health");
        }
    }
    @Override public synchronized void close() { consumer.close(Duration.ofSeconds(5)); }
}
