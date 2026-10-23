package com.aegisflow.tools.kafka;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.SaslAuthenticationException;
import org.apache.kafka.common.errors.TopicAuthorizationException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/** Exercises only a fresh, disposable broker provisioned by the verification harness. */
public final class KafkaSmoke {
    private static final List<String> OWNERS = List.of("order", "payment", "warehouse");
    private static final List<String> PRINCIPALS = List.of("admin", "order", "payment", "warehouse", "core");
    private static final Map<String, String> TOPIC_CONFIG = Map.of(
            "retention.ms", "604800000", "cleanup.policy", "delete",
            "min.insync.replicas", "1", "max.message.bytes", "1048576");
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration API_TIMEOUT = Duration.ofSeconds(15);

    private KafkaSmoke() { }

    public static void main(String[] arguments) {
        AtomicReference<String> stage = new AtomicReference<>("configuration");
        // A final bound also covers unexpected blocking in client construction or shutdown.
        Thread.ofPlatform().daemon().name("kafka-smoke-deadline").start(() -> {
            try {
                Thread.sleep(Duration.ofSeconds(180));
                System.out.println("FAIL: Kafka smoke exceeded its overall deadline at " + stage.get());
                System.exit(1);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            if (arguments.length == 1 && "--self-test".equals(arguments[0])) {
                selfTest();
                return;
            }
            boolean readOnly = arguments.length == 1 && "--persistent-read-only".equals(arguments[0]);
            boolean boundaries = arguments.length == 1 && "--persistent-boundaries".equals(arguments[0]);
            require(arguments.length == 0 || readOnly || boundaries);
            Settings settings = Settings.fromEnvironment(System.getenv());
            if (boundaries) {
                stage.set("forbidden metadata operations");
                for (String owner : OWNERS) {
                    Admin client = Admin.create(properties(settings, owner, settings.passwords().get(owner)));
                    try {
                        client.describeTopics(List.of(topic(owner))).allTopicNames().get(15, TimeUnit.SECONDS);
                        for (String sibling : OWNERS) if (!sibling.equals(owner))
                            expectFailure(TopicAuthorizationException.class, () -> client.describeTopics(List.of(topic(sibling))).allTopicNames().get(15, TimeUnit.SECONDS));
                    } finally { client.close(CLOSE_TIMEOUT); }
                }
                Admin core = Admin.create(properties(settings, "core", settings.passwords().get("core")));
                try {
                    core.describeTopics(OWNERS.stream().map(KafkaSmoke::topic).toList()).allTopicNames().get(15, TimeUnit.SECONDS);
                    expectFailure(org.apache.kafka.common.errors.ClusterAuthorizationException.class,
                            () -> core.describeAcls(org.apache.kafka.common.acl.AclBindingFilter.ANY).values().get(15, TimeUnit.SECONDS));
                } finally { core.close(CLOSE_TIMEOUT); }
                System.out.println("PASS: six sibling topic descriptions and Core ACL administration denied explicitly; positive controls passed; no records written");
                return;
            }
            stage.set("broker metadata and topic configuration");
            verifyMetadata(settings);
            System.out.println("PASS: advertised listener and three source topic configurations");

            if (readOnly) {
                stage.set("persistent ACL inventory and principal authentication");
                verifyPersistentReadOnly(settings);
                verifyWrongPassword(settings);
                System.out.println("PASS: exact ACL inventory, source/Core authentication and wrong-password rejection; no records or offsets written");
                return;
            }

            stage.set("source producer acknowledgements");
            Map<TopicPartition, ExpectedRecord> expected = produce(settings);
            System.out.println("PASS: three source-authorized producers received broker acknowledgements");

            stage.set("Core consumption and durable offsets");
            consume(settings, expected);
            System.out.println("PASS: Core received all three exact markers and committed their offsets");

            stage.set("source-topic ACL denials");
            verifyForbiddenWrites(settings);
            System.out.println("PASS: six sibling-topic writes and one Core write denied by topic authorization");

            stage.set("incorrect credential rejection");
            verifyWrongPassword(settings);
            System.out.println("PASS: incorrect password rejected by SASL authentication");
        } catch (Exception failure) {
            // Client exception messages can contain configuration, payloads or credentials.
            System.out.println("FAIL: Kafka smoke at " + stage.get() + " (" + failureTypes(failure) + ")");
            System.exit(1);
        }
    }

    private static String failureTypes(Throwable failure) {
        StringBuilder types = new StringBuilder();
        for (int depth = 0; failure != null && depth < 8; depth++, failure = failure.getCause()) {
            if (depth > 0) types.append(" -> ");
            types.append(failure.getClass().getSimpleName());
        }
        return types.toString();
    }

    private static Set<org.apache.kafka.common.acl.AclBinding> expectedPersistentAcls() {
        var expected = new HashSet<org.apache.kafka.common.acl.AclBinding>();
        for (String owner : OWNERS) {
            for (var operation : List.of(org.apache.kafka.common.acl.AclOperation.WRITE, org.apache.kafka.common.acl.AclOperation.DESCRIBE))
                expected.add(acl(org.apache.kafka.common.resource.ResourceType.TOPIC, topic(owner), org.apache.kafka.common.resource.PatternType.LITERAL, owner, operation));
            expected.add(acl(org.apache.kafka.common.resource.ResourceType.CLUSTER, "kafka-cluster", org.apache.kafka.common.resource.PatternType.LITERAL, owner, org.apache.kafka.common.acl.AclOperation.IDEMPOTENT_WRITE));
            for (var operation : List.of(org.apache.kafka.common.acl.AclOperation.READ, org.apache.kafka.common.acl.AclOperation.DESCRIBE))
                expected.add(acl(org.apache.kafka.common.resource.ResourceType.TOPIC, topic(owner), org.apache.kafka.common.resource.PatternType.LITERAL, "core", operation));
        }
        for (var operation : List.of(org.apache.kafka.common.acl.AclOperation.READ, org.apache.kafka.common.acl.AclOperation.DESCRIBE))
            expected.add(acl(org.apache.kafka.common.resource.ResourceType.GROUP, "aegisflow-core-", org.apache.kafka.common.resource.PatternType.PREFIXED, "core", operation));
        return Set.copyOf(expected);
    }

    private static void verifyPersistentReadOnly(Settings settings) throws Exception {
        var expected = expectedPersistentAcls();
        Admin admin = Admin.create(properties(settings, "admin", settings.passwords().get("admin")));
        try {
            var observed = new HashSet<>(admin.describeAcls(org.apache.kafka.common.acl.AclBindingFilter.ANY).values().get(15, TimeUnit.SECONDS));
            if (!observed.equals(expected)) {
                var missing = new HashSet<>(expected); missing.removeAll(observed);
                var extra = new HashSet<>(observed); extra.removeAll(expected);
                System.out.println("ACL inventory mismatch: missing=" + missing.size() + ", unexpected=" + extra.size());
                require(false);
            }
            System.out.println("IDENTITY: " + admin.describeCluster().clusterId().get(15, TimeUnit.SECONDS));
            var topics = admin.describeTopics(OWNERS.stream().map(KafkaSmoke::topic).toList()).allTopicNames().get(15, TimeUnit.SECONDS);
            for (String owner : OWNERS) System.out.println("TOPIC_ID: " + owner + " " + topics.get(topic(owner)).topicId());
        } finally { admin.close(CLOSE_TIMEOUT); }
        for (String principal : List.of("order", "payment", "warehouse", "core")) {
            Admin client = Admin.create(properties(settings, principal, settings.passwords().get(principal)));
            try {
                var names = principal.equals("core") ? OWNERS.stream().map(KafkaSmoke::topic).toList() : List.of(topic(principal));
                require(client.describeTopics(names).allTopicNames().get(15, TimeUnit.SECONDS).size() == names.size());
            } finally { client.close(CLOSE_TIMEOUT); }
        }
    }

    private static org.apache.kafka.common.acl.AclBinding acl(org.apache.kafka.common.resource.ResourceType type, String name,
            org.apache.kafka.common.resource.PatternType pattern, String principal, org.apache.kafka.common.acl.AclOperation operation) {
        return new org.apache.kafka.common.acl.AclBinding(new org.apache.kafka.common.resource.ResourcePattern(type, name, pattern),
                new org.apache.kafka.common.acl.AccessControlEntry("User:" + principal, "*", operation, org.apache.kafka.common.acl.AclPermissionType.ALLOW));
    }

    private static void verifyMetadata(Settings settings) throws Exception {
        Admin admin = Admin.create(properties(settings, "admin", settings.passwords().get("admin")));
        try {
            Collection<Node> nodes = admin.describeCluster().nodes().get(15, TimeUnit.SECONDS);
            require(nodes.size() == 1);
            Node node = nodes.iterator().next();
            require(settings.expectedHost().equals(node.host()) && settings.expectedPort() == node.port());
            Set<String> names = new HashSet<>(OWNERS.stream().map(KafkaSmoke::topic).toList());
            require(names.equals(admin.listTopics().names().get(15, TimeUnit.SECONDS)));
            Map<String, TopicDescription> descriptions = admin.describeTopics(names).allTopicNames()
                    .get(15, TimeUnit.SECONDS);
            for (TopicDescription description : descriptions.values()) {
                require(description.partitions().size() == 3);
                description.partitions().forEach(partition -> require(partition.replicas().size() == 1
                        && partition.isr().size() == 1 && partition.leader() != null
                        && partition.leader().id() == node.id()));
            }
            List<ConfigResource> resources = names.stream()
                    .map(name -> new ConfigResource(ConfigResource.Type.TOPIC, name)).toList();
            for (Config config : admin.describeConfigs(resources).all().get(15, TimeUnit.SECONDS).values()) {
                TOPIC_CONFIG.forEach((key, value) -> require(config.get(key) != null
                        && value.equals(config.get(key).value())));
            }
        } finally {
            admin.close(CLOSE_TIMEOUT);
        }
    }

    private static Map<TopicPartition, ExpectedRecord> produce(Settings settings) throws Exception {
        Map<TopicPartition, ExpectedRecord> expected = new LinkedHashMap<>();
        for (String owner : OWNERS) {
            KafkaProducer<String, String> producer = producer(settings, owner);
            try {
                String marker = marker(settings, owner);
                RecordMetadata result = producer.send(new ProducerRecord<>(topic(owner), marker, marker))
                        .get(20, TimeUnit.SECONDS);
                require(result.hasOffset() && result.offset() >= 0);
                expected.put(new TopicPartition(result.topic(), result.partition()),
                        new ExpectedRecord(marker, result.offset()));
            } finally {
                producer.close(CLOSE_TIMEOUT);
            }
        }
        require(expected.size() == 3);
        return expected;
    }

    private static void consume(Settings settings, Map<TopicPartition, ExpectedRecord> expected) {
        Properties properties = properties(settings, "core", settings.passwords().get("core"));
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "aegisflow-core-smoke-" + settings.runId());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "none");
        properties.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        properties.put(ConsumerConfig.GROUP_PROTOCOL_CONFIG, "classic");
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);
        try {
            // Exact acknowledged offsets bound the work independently of old smoke markers.
            consumer.assign(expected.keySet());
            expected.forEach((partition, record) -> consumer.seek(partition, record.offset()));
            Map<TopicPartition, OffsetAndMetadata> consumed = new HashMap<>();
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (consumed.size() != expected.size() && System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofSeconds(1))) {
                    TopicPartition partition = new TopicPartition(record.topic(), record.partition());
                    ExpectedRecord wanted = expected.get(partition);
                    if (wanted != null && wanted.offset() == record.offset()) {
                        require(wanted.marker().equals(record.key()) && wanted.marker().equals(record.value()));
                        consumed.put(partition, new OffsetAndMetadata(record.offset() + 1));
                    }
                }
            }
            require(consumed.size() == expected.size());
            consumer.commitSync(consumed, API_TIMEOUT);
            Map<TopicPartition, OffsetAndMetadata> committed = consumer.committed(expected.keySet(), API_TIMEOUT);
            consumed.forEach((partition, offset) -> require(committed.get(partition) != null
                    && committed.get(partition).offset() >= offset.offset()));
        } finally {
            consumer.close(CLOSE_TIMEOUT);
        }
    }

    private static void verifyForbiddenWrites(Settings settings) throws Exception {
        for (String source : OWNERS) {
            for (String target : OWNERS) {
                if (!source.equals(target)) {
                    assertForbiddenWrite(settings, source, target);
                }
            }
        }
        assertForbiddenWrite(settings, "core", "order");
    }

    private static void assertForbiddenWrite(Settings settings, String principal, String owner) throws Exception {
        KafkaProducer<String, String> producer = producer(settings, principal);
        try {
            String marker = marker(settings, owner);
            expectFailure(TopicAuthorizationException.class,
                    () -> producer.send(new ProducerRecord<>(topic(owner), marker, marker)).get(20, TimeUnit.SECONDS));
        } finally {
            producer.close(CLOSE_TIMEOUT);
        }
    }

    private static void verifyWrongPassword(Settings settings) throws Exception {
        Admin admin = Admin.create(properties(settings, "admin", settings.passwords().get("admin") + "-wrong"));
        try {
            expectFailure(SaslAuthenticationException.class,
                    () -> admin.describeCluster().nodes().get(20, TimeUnit.SECONDS));
        } finally {
            admin.close(CLOSE_TIMEOUT);
        }
    }

    private static KafkaProducer<String, String> producer(Settings settings, String principal) {
        Properties properties = properties(settings, principal, settings.passwords().get(principal));
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        // Core has no IdempotentWrite privilege; its negative probe must reach topic authorization.
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, !"core".equals(principal));
        properties.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 15000);
        properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 15000);
        return new KafkaProducer<>(properties);
    }

    private static Properties properties(Settings settings, String principal, String password) {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", settings.bootstrapServers());
        properties.put("client.id", "aegisflow-smoke-" + principal + "-" + settings.runId());
        properties.put("security.protocol", "SASL_PLAINTEXT");
        properties.put("sasl.mechanism", "PLAIN");
        properties.put("sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required "
                + "username=\"" + principal + "\" password=\"" + password + "\";");
        properties.put("request.timeout.ms", 5000);
        properties.put("default.api.timeout.ms", 15000);
        properties.put("socket.connection.setup.timeout.ms", 3000);
        properties.put("socket.connection.setup.timeout.max.ms", 5000);
        return properties;
    }

    private static String topic(String owner) {
        return "aegisflow." + owner + ".events.v1";
    }

    private static String marker(Settings settings, String owner) {
        return "kafka-infrastructure-smoke:" + settings.runId() + ":" + settings.expectedHost()
                + ":" + settings.expectedPort() + ":" + owner;
    }

    private static void expectFailure(Class<? extends Exception> expected, CheckedAction action) throws Exception {
        Exception failure = null;
        try {
            action.run();
        } catch (Exception observed) {
            failure = observed;
        }
        // Only Kafka's explicit authentication/authorization response is evidence of denial.
        // A network timeout is an unknown outcome and must fail this verification.
        require(failure != null && isExpectedFailure(failure, expected));
    }

    private static boolean isExpectedFailure(Throwable failure, Class<? extends Exception> expected) {
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 8; depth++) {
            if (expected.isInstance(cause)) {
                return true;
            }
            if (!(cause instanceof ExecutionException)) {
                return false;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new VerificationFailure();
        }
    }

    private record ExpectedRecord(String marker, long offset) { }

    private record Settings(String bootstrapServers, String runId, String expectedHost, int expectedPort,
                            Map<String, String> passwords) {
        private static Settings fromEnvironment(Map<String, String> environment) {
            String runId = environment.getOrDefault("KAFKA_SMOKE_RUN_ID", "");
            String host = environment.getOrDefault("KAFKA_SMOKE_EXPECTED_HOST", "");
            String portText = environment.getOrDefault("KAFKA_SMOKE_EXPECTED_PORT", "");
            String bootstrap = environment.getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "");
            require(runId.matches("[0-9a-f]{32}"));
            require(host.matches("[A-Za-z0-9][A-Za-z0-9.-]{0,252}"));
            require(portText.matches("[1-9][0-9]{0,4}"));
            int port = Integer.parseInt(portText);
            require(port <= 65535 && bootstrap.equals(host + ":" + port));
            Map<String, String> passwords = new HashMap<>();
            for (String principal : PRINCIPALS) {
                String password = environment.getOrDefault("KAFKA_" + principal.toUpperCase(java.util.Locale.ROOT)
                        + "_PASSWORD", "");
                require(password.matches("[A-Za-z0-9_-]{24,128}"));
                passwords.put(principal, password);
            }
            require(new HashSet<>(passwords.values()).size() == PRINCIPALS.size());
            return new Settings(bootstrap, runId, host, port, Map.copyOf(passwords));
        }
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }

    private static final class VerificationFailure extends IllegalStateException { }

    private static void selfTest() throws Exception {
        int checks = 0;
        require(failureTypes(new ExecutionException(new SaslAuthenticationException("private-value")))
                .equals("ExecutionException -> SaslAuthenticationException"));
        checks++;
        var inventory = expectedPersistentAcls();
        require(inventory.size() == 17);
        checks++;
        for (var operation : List.of(org.apache.kafka.common.acl.AclOperation.READ, org.apache.kafka.common.acl.AclOperation.DESCRIBE)) {
            require(inventory.contains(acl(org.apache.kafka.common.resource.ResourceType.GROUP, "aegisflow-core-",
                    org.apache.kafka.common.resource.PatternType.PREFIXED, "core", operation)));
            checks++;
        }
        require(isExpectedFailure(new TopicAuthorizationException(Set.of("synthetic")), TopicAuthorizationException.class));
        checks++;
        require(isExpectedFailure(new ExecutionException(new TopicAuthorizationException(Set.of("synthetic"))),
                TopicAuthorizationException.class));
        checks++;
        require(isExpectedFailure(new ExecutionException(new SaslAuthenticationException("synthetic")),
                SaslAuthenticationException.class));
        checks++;
        require(!isExpectedFailure(new org.apache.kafka.common.errors.TimeoutException("synthetic"),
                TopicAuthorizationException.class));
        checks++;
        require(!isExpectedFailure(new ExecutionException(new java.util.concurrent.TimeoutException()),
                SaslAuthenticationException.class));
        checks++;
        require(!isExpectedFailure(new SaslAuthenticationException("synthetic"), TopicAuthorizationException.class));
        checks++;
        expectFailure(VerificationFailure.class, () -> expectFailure(TopicAuthorizationException.class, () -> { }));
        checks++;
        expectFailure(VerificationFailure.class, () -> expectFailure(TopicAuthorizationException.class,
                () -> { throw new java.util.concurrent.TimeoutException(); }));
        checks++;
        Map<String, String> environment = syntheticEnvironment();
        Settings.fromEnvironment(environment);
        checks++;
        List<Map<String, String>> invalid = new ArrayList<>();
        invalid.add(replaced(environment, "KAFKA_ORDER_PASSWORD", "unsafe\"credential0123456789"));
        invalid.add(replaced(environment, "KAFKA_PAYMENT_PASSWORD", environment.get("KAFKA_ORDER_PASSWORD")));
        invalid.add(replaced(environment, "KAFKA_CORE_PASSWORD", ""));
        invalid.add(replaced(environment, "KAFKA_SMOKE_RUN_ID", "not-an-isolated-run-id"));
        invalid.add(replaced(environment, "KAFKA_BOOTSTRAP_SERVERS", "different-broker:9092"));
        invalid.add(replaced(environment, "KAFKA_SMOKE_EXPECTED_PORT", "65536"));
        for (Map<String, String> inputs : invalid) {
            expectFailure(VerificationFailure.class, () -> Settings.fromEnvironment(inputs));
            checks++;
        }
        System.out.println("PASS: " + checks + " infrastructure-free smoke guard checks (no broker tested)");
    }

    private static Map<String, String> syntheticEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put("KAFKA_SMOKE_RUN_ID", "0123456789abcdef0123456789abcdef");
        environment.put("KAFKA_SMOKE_EXPECTED_HOST", "127.0.0.1");
        environment.put("KAFKA_SMOKE_EXPECTED_PORT", "19092");
        environment.put("KAFKA_BOOTSTRAP_SERVERS", "127.0.0.1:19092");
        for (String principal : PRINCIPALS) {
            environment.put("KAFKA_" + principal.toUpperCase(java.util.Locale.ROOT) + "_PASSWORD",
                    "synthetic-self-test-password-" + principal);
        }
        return environment;
    }

    private static Map<String, String> replaced(Map<String, String> source, String key, String value) {
        Map<String, String> copy = new HashMap<>(source);
        copy.put(key, value);
        return copy;
    }
}
