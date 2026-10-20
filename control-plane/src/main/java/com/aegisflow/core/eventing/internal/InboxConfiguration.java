package com.aegisflow.core.eventing.internal;

import java.util.*;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@Profile("postgres")
@ConditionalOnProperty(name = "aegisflow.core.inbox.enabled", havingValue = "true")
@EnableScheduling
class InboxConfiguration {
    @Bean IntakePolicy intakePolicy(Environment env) {
        Map<String, Set<String>> bindings = new HashMap<>();
        for (String owner : List.of("order", "payment", "warehouse")) {
            String value = env.getRequiredProperty("aegisflow.core.inbox." + owner + "-tenants");
            if (!value.isBlank()) bindings.put(owner + "-service", Set.of(value.split(",", -1)));
        }
        return new IntakePolicy(bindings);
    }
    @Bean EventValidator eventValidator(IntakePolicy policy) { return new EventValidator(policy); }
    @Bean InboxStore inboxStore(JdbcTemplate jdbc, PlatformTransactionManager manager, EventValidator validator, Environment env) {
        return new InboxStore(jdbc, manager, validator, env.getRequiredProperty("aegisflow.core.inbox.channel"));
    }
    @Bean(destroyMethod = "close") KafkaInboxConsumer kafkaInboxConsumer(InboxStore store, Environment env) {
        return new KafkaInboxConsumer(new KafkaConsumer<>(consumerProperties(
                env.getRequiredProperty("aegisflow.core.inbox.bootstrap-servers"),
                env.getRequiredProperty("aegisflow.core.inbox.password"),
                env.getRequiredProperty("aegisflow.core.inbox.group"))), store::accept);
    }
    static Map<String, Object> consumerProperties(String bootstrap, String password, String group) {
        if (bootstrap == null || !bootstrap.matches("(?:127\\.0\\.0\\.1|kafka):[0-9]{1,5}"))
            throw new IllegalArgumentException("Local Kafka listener required");
        int port = Integer.parseInt(bootstrap.substring(bootstrap.indexOf(':') + 1));
        if (port < 1024 || port > 65535 || password == null || !password.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Core Kafka credential/listener required");
        if (group == null || !group.matches("aegisflow-core-[A-Za-z0-9-]{1,80}"))
            throw new IllegalArgumentException("Core ACL-scoped group required");
        Map<String, Object> values = new HashMap<>();
        values.put("bootstrap.servers", bootstrap);
        values.put("group.id", group);
        values.put("client.id", "core-durable-inbox");
        values.put("key.deserializer", ByteArrayDeserializer.class);
        values.put("value.deserializer", ByteArrayDeserializer.class);
        values.put("enable.auto.commit", false);
        values.put("auto.offset.reset", "earliest");
        values.put("max.poll.records", 1);
        values.put("max.partition.fetch.bytes", 1048576);
        values.put("fetch.max.bytes", 1048576);
        values.put("max.poll.interval.ms", 120000);
        values.put("default.api.timeout.ms", 10000);
        values.put("request.timeout.ms", 5000);
        values.put("allow.auto.create.topics", false);
        values.put("security.protocol", "SASL_PLAINTEXT"); values.put("sasl.mechanism", "PLAIN");
        values.put("sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"core\" password=\"" + password + "\";");
        return values;
    }
}
