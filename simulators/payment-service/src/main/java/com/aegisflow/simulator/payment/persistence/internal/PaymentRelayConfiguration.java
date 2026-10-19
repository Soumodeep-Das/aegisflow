package com.aegisflow.simulator.payment.persistence.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@Profile("postgres")
@ConditionalOnProperty(name = "aegisflow.payment.relay.enabled", havingValue = "true")
@EnableScheduling
class PaymentRelayConfiguration {
    @Bean PaymentOutboxStore paymentOutboxStore(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        return new PaymentOutboxStore(jdbc, manager);
    }

    @Bean(destroyMethod = "close") KafkaOutboxPublisher paymentKafkaPublisher(Environment environment) {
        return new KafkaOutboxPublisher(new KafkaProducer<>(producerProperties(
                environment.getRequiredProperty("aegisflow.payment.relay.bootstrap-servers"),
                environment.getRequiredProperty("aegisflow.payment.relay.password"))));
    }

    @Bean PaymentOutboxRelay paymentOutboxRelay(PaymentOutboxStore store, KafkaOutboxPublisher publisher, Environment environment) {
        String[] tenants = environment.getRequiredProperty("aegisflow.payment.relay.allowed-tenants").split(",", -1);
        return new PaymentOutboxRelay(store, publisher, UUID.randomUUID().toString(),
                environment.getProperty("aegisflow.payment.relay.max-per-poll", Integer.class, 10), Set.of(tenants));
    }

    static Map<String, Object> producerProperties(String bootstrap, String password) {
        // Deliberately a trusted-local transport only; reject remote/plaintext deployment by default.
        if (bootstrap == null || !bootstrap.matches("(?:127\\.0\\.0\\.1|kafka):[0-9]{1,5}"))
            throw new IllegalArgumentException("Local Kafka listener required");
        int port = Integer.parseInt(bootstrap.substring(bootstrap.indexOf(':') + 1));
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("Invalid Kafka port");
        if (password == null || !password.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Payment Kafka credential required");
        var values = new HashMap<String, Object>();
        values.put("bootstrap.servers", bootstrap);
        values.put("key.serializer", StringSerializer.class);
        values.put("value.serializer", StringSerializer.class);
        values.put("acks", "all"); values.put("enable.idempotence", true);
        values.put("max.in.flight.requests.per.connection", 5);
        values.put("request.timeout.ms", 5000); values.put("delivery.timeout.ms", 15000);
        values.put("max.block.ms", 5000); values.put("linger.ms", 0);
        values.put("max.request.size", 1048576); values.put("client.id", "payment-outbox-relay");
        values.put("security.protocol", "SASL_PLAINTEXT"); values.put("sasl.mechanism", "PLAIN");
        values.put("sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"payment\" password=\"" + password + "\";");
        return values;
    }
}
