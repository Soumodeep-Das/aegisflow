package com.aegisflow.core.eventing.internal;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

/** Explicit pipeline-tests gate: actual simulator applications/transactions/relays and Core code. */
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value=90,unit=TimeUnit.SECONDS)
class CorePipelineScenario {
    private static final Network NETWORK = Network.newNetwork();
    @Container static final CoreKafkaFixture KAFKA = new CoreKafkaFixture(NETWORK);
    @Container static final PostgreSQLContainer CORE = database("core");
    @Container static final PostgreSQLContainer ORDER = database("order");
    @Container static final PostgreSQLContainer PAYMENT = database("payment");
    @Container static final PostgreSQLContainer WAREHOUSE = database("warehouse");
    private static ConfigurableApplicationContext core;
    private static final Map<String,ConfigurableApplicationContext> apps = new LinkedHashMap<>();
    private static InboxStore store;
    private static JdbcTemplate jdbc;
    private static final JsonMapper JSON = new JsonMapper();

    static PostgreSQLContainer database(String owner) {
        return new PostgreSQLContainer(DockerImageName.parse(
                "postgres:18.6@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336")
                .asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("pipeline_" + owner).withUsername("inspection_admin").withPassword(PipelineApplications.PASSWORD);
    }
    @BeforeAll static void startApplications() throws Exception {
        try {
            core = PipelineApplications.start("core",CORE,KAFKA);
            assertThat(core.getBean(org.springframework.transaction.PlatformTransactionManager.class))
                    .isInstanceOf(org.springframework.jdbc.datasource.DataSourceTransactionManager.class);
            store = core.getBean(InboxStore.class); jdbc = core.getBean(JdbcTemplate.class);
            // Enabled configuration was instantiated; deterministic cases create and close their own workers/groups.
            core.getBean(KafkaInboxConsumer.class).close();
            for (var pair : Map.of("order",ORDER,"payment",PAYMENT,"warehouse",WAREHOUSE).entrySet())
                apps.put(pair.getKey(),PipelineApplications.start(pair.getKey(),pair.getValue(),KAFKA));
        } catch (Exception failure) { stopApplications(); throw failure; }
    }
    @AfterAll static void stopApplications() {
        apps.values().forEach(ConfigurableApplicationContext::close);
        if (core != null) core.close();
    }
    @ParameterizedTest @ValueSource(strings={"order","payment","warehouse"}) @Order(1)
    void actualOwnerBusinessTransactionOutboxRelayKafkaAndCoreInbox(String owner) throws Exception {
        UUID id = createAndPublish(owner,"positive");
        try (var worker = worker(group(),store::accept)) { until(worker,() -> count(id)==1); }
        assertThat(count(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT source_system FROM aegisflow.inbox_message WHERE event_id=?",String.class,id))
                .isEqualTo(owner + "-service");
        assertThat(jdbc.queryForObject("SELECT status FROM aegisflow.inbox_message WHERE event_id=?",String.class,id)).isEqualTo("ACCEPTED");
    }
    @Test @Order(2) void bothNegativeOutcomesTravelThroughActualOwnerRelays() throws Exception {
        UUID payment = createAndPublish("payment","negative"), warehouse = createAndPublish("warehouse","negative");
        try (var worker=worker(group(),store::accept)) { until(worker,() -> count(payment)==1 && count(warehouse)==1); }
        assertThat(jdbc.queryForList("SELECT event_type FROM aegisflow.inbox_message WHERE event_id IN (?,?)",String.class,payment,warehouse))
                .containsExactlyInAnyOrder("PaymentDeclined","InventoryRejected");
    }
    @Test @Order(3) void equivalentDuplicatesAreIdempotentAndChangedContentIsDurablyQuarantined() throws Exception {
        UUID id = createAndPublish("order","positive");
        try (var worker=worker(group(),store::accept)) { until(worker,() -> count(id)==1); }
        String originalHash = jdbc.queryForObject("SELECT payload_hash FROM aegisflow.inbox_message WHERE event_id=?",String.class,id);
        var event = event("order",id);
        var duplicate = publish("order",event,JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(event));
        ((Map<String,Object>)event.get("details")).put("amount","13.00");
        var conflict = publish("order",event,JSON.writeValueAsBytes(event));
        try (var consumer=client(group());var worker=new KafkaInboxConsumer(consumer,store::accept)) {
            until(worker,() -> quarantined(conflict)==1 && committed(consumer,duplicate));
        }
        assertThat(count(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT payload_hash FROM aegisflow.inbox_message WHERE event_id=?",String.class,id)).isEqualTo(originalHash);
        assertThat(reason(conflict)).isEqualTo("EVENT_ID_CONFLICT");
    }
    @Test @Order(4) void restartRedeliversAfterDatabaseCommitBeforeOffsetAcknowledgement() throws Exception {
        UUID id = createAndPublish("order","positive");
        String group = group(); AtomicReference<ConsumerRecord<byte[],byte[]>> interrupted = new AtomicReference<>();
        try (var consumer=client(group);var worker=new KafkaInboxConsumer(consumer,record -> {
            String result=store.accept(record);
            if (contains(record,id)) { interrupted.set(record); throw new IllegalStateException("Simulated commit-before-ack interruption"); }
            return result;
        })) {
            assertThatThrownBy(() -> until(worker,() -> interrupted.get()!=null)).hasMessageContaining("commit-before-ack");
            assertThat(count(id)).isEqualTo(1);
            assertThat(committed(consumer,interrupted.get())).isFalse();
        }
        var duplicate = new AtomicReference<String>();
        try (var worker=worker(group,record -> {
            String result=store.accept(record); if (contains(record,id)) duplicate.set(result); return result;
        })) { until(worker,() -> duplicate.get()!=null); }
        assertThat(duplicate.get()).isEqualTo("DUPLICATE"); assertThat(count(id)).isEqualTo(1);
    }
    @Test @Order(5) void actualDatabaseRollbackLeavesOffsetUncommittedAndRestartRetries() throws Exception {
        UUID id = createAndPublish("order","positive"); String group = group();
        admin("CREATE FUNCTION aegisflow.reject_test_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.event_id='"
                + id + "'::uuid THEN RAISE EXCEPTION 'synthetic rollback'; END IF; RETURN NEW; END $$");
        admin("CREATE TRIGGER reject_test_insert AFTER INSERT ON aegisflow.inbox_message FOR EACH ROW EXECUTE FUNCTION aegisflow.reject_test_insert()");
        var failed = new AtomicReference<ConsumerRecord<byte[],byte[]>>();
        try {
            try (var consumer=client(group);var worker=new KafkaInboxConsumer(consumer,record -> {
                if (contains(record,id)) failed.set(record); return store.accept(record);
            })) {
                assertThatThrownBy(() -> until(worker,() -> count(id)==1)).isInstanceOf(org.springframework.dao.DataAccessException.class);
                assertThat(failed.get()).isNotNull(); assertThat(count(id)).isZero();
                assertThat(committed(consumer,failed.get())).isFalse();
            }
        } finally {
            admin("DROP TRIGGER reject_test_insert ON aegisflow.inbox_message"); admin("DROP FUNCTION aegisflow.reject_test_insert()");
        }
        try (var worker=worker(group,store::accept)) { until(worker,() -> count(id)==1); }
        assertThat(count(id)).isEqualTo(1);
    }
    @Test @Order(6) void poisonUnsupportedAndSpoofedClaimsAreQuarantinedWithoutBlockingNextEvent() throws Exception {
        UUID original = createAndPublish("order","positive");
        var envelope=event("order",original); var receipts=new ArrayList<RecordMetadata>();
        receipts.add(publish("order",envelope,"{".getBytes(StandardCharsets.UTF_8)));
        var unsupported=new LinkedHashMap<>(envelope); unsupported.put("schemaVersion","2.0");
        receipts.add(publish("order",unsupported,JSON.writeValueAsBytes(unsupported)));
        var source=new LinkedHashMap<>(envelope); source.put("sourceSystem","payment-service");
        receipts.add(publish("order",source,JSON.writeValueAsBytes(source)));
        // This tenant is trusted for Payment only; Order's topic/principal must not gain that authority.
        var tenant=new LinkedHashMap<>(envelope); tenant.put("tenantId","pipeline-payment-only");
        receipts.add(publish("order",tenant,JSON.writeValueAsBytes(tenant)));
        var missing=new LinkedHashMap<>(envelope); missing.remove("eventId");
        receipts.add(publish("order",missing,JSON.writeValueAsBytes(missing)));
        String valid=JSON.writeValueAsString(envelope);
        receipts.add(publish("order",envelope,(valid.substring(0,valid.length()-1)+",\"optional\":\"\\u0000\"}").getBytes(StandardCharsets.UTF_8)));
        receipts.add(publish("order",envelope,(valid.substring(0,valid.length()-1)+",\"optional\":1e999999}").getBytes(StandardCharsets.UTF_8)));
        UUID after=createAndPublish("order","positive");
        try (var consumer=client(group());var worker=new KafkaInboxConsumer(consumer,store::accept)) {
            until(worker,() -> count(after)==1 && receipts.stream().allMatch(r -> quarantined(r)==1));
            assertThat(receipts).allMatch(r -> committed(consumer,r));
        }
        assertThat(receipts.stream().map(CorePipelineScenario::reason).toList())
                .containsExactly("MALFORMED_JSON","SCHEMA_UNSUPPORTED","SOURCE_NOT_TRUSTED","TENANT_NOT_TRUSTED","SCHEMA_INVALID",
                        "SCHEMA_INVALID","SCHEMA_INVALID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aegisflow.inbox_message WHERE tenant_id='pipeline-payment-only'",Integer.class)).isZero();
    }
    @Test @Order(7) void concurrentDurableProcessingOfSameRealRecordArbitratesInPostgres() throws Exception {
        UUID id=createAndPublish("order","positive"); var simultaneous=new CyclicBarrier(2);
        // Two real Kafka clients in independent groups model normal intake racing an operator replay.
        Callable<String> consume = () -> {
            var result = new AtomicReference<String>();
            try (var worker=worker(group(),record -> {
                if (!contains(record,id)) return store.accept(record);
                try { simultaneous.await(20,TimeUnit.SECONDS); }
                catch (Exception failure) { throw new IllegalStateException("Concurrent consumer rendezvous failed",failure); }
                String accepted=store.accept(record); result.set(accepted); return accepted;
            })) { until(worker,() -> result.get()!=null); }
            return result.get();
        };
        try (var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(consume); var b=pool.submit(consume);
            assertThat(List.of(a.get(60,TimeUnit.SECONDS),b.get(60,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("ACCEPTED","DUPLICATE");
        }
        assertThat(count(id)).isEqualTo(1);
    }
    @Test @Order(8) void runtimeCannotOverwriteAcceptedEvidenceAndQuarantineReplayIsUnique() throws Exception {
        UUID id=createAndPublish("order","positive");
        try (var worker=worker(group(),store::accept)) { until(worker,() -> count(id)==1); }
        assertThatThrownBy(() -> jdbc.update("UPDATE aegisflow.inbox_message SET status='ACCEPTED' WHERE event_id=?",id))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM aegisflow.inbox_message WHERE event_id=?",id))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO aegisflow.inbox_message
                SELECT tenant_id || '-other',source_system,event_id,event_type,business_transaction_id,schema_version,
                       occurred_at,received_at,correlation_id,channel_identity,topic,partition_id,offset_id,
                       payload,payload_hash,payload_hash_version,raw_payload_hash,status
                FROM aegisflow.inbox_message WHERE event_id=?
                """,id)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        var record=new ConsumerRecord<byte[],byte[]>("aegisflow.order.events.v1",0,999999,new byte[0],new byte[]{123});
        assertThat(store.accept(record)).isEqualTo("QUARANTINED"); assertThat(store.accept(record)).isEqualTo("QUARANTINED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aegisflow.quarantine_record WHERE offset_id=999999",Integer.class)).isEqualTo(1);
    }

    static UUID createAndPublish(String owner,String outcome) throws Exception {
        var app=apps.get(owner); UUID id=PipelineApplications.create(app,owner,outcome);
        var db=app.getBean(JdbcTemplate.class);
        assertThat(db.queryForObject("SELECT status FROM aegisflow.outbox_event WHERE event_id=?",String.class,id)).isEqualTo("PENDING");
        PipelineApplications.relay(app,owner);
        assertThat(db.queryForObject("SELECT status FROM aegisflow.outbox_event WHERE event_id=?",String.class,id)).isEqualTo("PUBLISHED");
        return id;
    }
    static Map<String,Object> event(String owner,UUID id) {
        return JSON.readValue(apps.get(owner).getBean(JdbcTemplate.class).queryForObject(
                "SELECT envelope::text FROM aegisflow.outbox_event WHERE event_id=?",String.class,id),Map.class);
    }
    static String group() { return "aegisflow-core-pipeline-" + UUID.randomUUID(); }
    static KafkaConsumer<byte[],byte[]> client(String group) {
        return new KafkaConsumer<>(InboxConfiguration.consumerProperties(KAFKA.bootstrap(),KAFKA.password("core"),group));
    }
    static KafkaInboxConsumer worker(String group,java.util.function.Function<ConsumerRecord<byte[],byte[]>,String> fn) {
        return new KafkaInboxConsumer(client(group),fn);
    }
    static void until(KafkaInboxConsumer worker,BooleanSupplier condition) {
        long end=System.nanoTime()+Duration.ofSeconds(45).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime()<end) worker.poll();
        assertThat(condition.getAsBoolean()).as("bounded durable intake condition").isTrue();
    }
    static int count(UUID id) { return jdbc.queryForObject("SELECT count(*) FROM aegisflow.inbox_message WHERE event_id=?",Integer.class,id); }
    static boolean contains(ConsumerRecord<byte[],byte[]> record,UUID id) {
        return record.value()!=null && new String(record.value(),StandardCharsets.UTF_8).contains(id.toString());
    }
    static boolean committed(KafkaConsumer<byte[],byte[]> client,ConsumerRecord<byte[],byte[]> record) {
        var tp=new TopicPartition(record.topic(),record.partition());
        var offset=client.committed(Set.of(tp)).get(tp); return offset!=null && offset.offset()>record.offset();
    }
    static boolean committed(KafkaConsumer<byte[],byte[]> client,RecordMetadata record) {
        var tp=new TopicPartition(record.topic(),record.partition());
        var offset=client.committed(Set.of(tp)).get(tp); return offset!=null && offset.offset()>record.offset();
    }
    static RecordMetadata publish(String owner,Map<String,Object> event,byte[] bytes) throws Exception {
        var props=new HashMap<String,Object>(); props.put("bootstrap.servers",KAFKA.bootstrap());
        props.put("security.protocol","SASL_PLAINTEXT"); props.put("sasl.mechanism","PLAIN");
        props.put("sasl.jaas.config","org.apache.kafka.common.security.plain.PlainLoginModule required username=\"" + owner
                + "\" password=\"" + KAFKA.password(owner) + "\";");
        props.put("key.serializer",ByteArraySerializer.class); props.put("value.serializer",ByteArraySerializer.class);
        props.put("acks","all"); props.put("max.block.ms",5000); props.put("delivery.timeout.ms",15000); props.put("request.timeout.ms",5000);
        byte[] key=(event.get("tenantId")+":"+event.get("businessTransactionId")).getBytes(StandardCharsets.UTF_8);
        try (var producer=new KafkaProducer<byte[],byte[]>(props)) {
            return producer.send(new ProducerRecord<>("aegisflow."+owner+".events.v1",0,key,bytes)).get(20,TimeUnit.SECONDS);
        }
    }
    static int quarantined(RecordMetadata r) {
        return jdbc.queryForObject("SELECT count(*) FROM aegisflow.quarantine_record WHERE topic=? AND partition_id=? AND offset_id=?",
                Integer.class,r.topic(),r.partition(),r.offset());
    }
    static String reason(RecordMetadata r) {
        return jdbc.queryForObject("SELECT reason_code FROM aegisflow.quarantine_record WHERE topic=? AND partition_id=? AND offset_id=?",
                String.class,r.topic(),r.partition(),r.offset());
    }
    static void admin(String sql) throws Exception {
        try(var c=DriverManager.getConnection(CORE.getJdbcUrl(),CORE.getUsername(),CORE.getPassword());var s=c.createStatement()) { s.execute(sql); }
    }
}
