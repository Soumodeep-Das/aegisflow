package com.aegisflow.core.eventing.internal;

import static org.assertj.core.api.Assertions.*;
import com.aegisflow.core.correlation.internal.PipelineProjectionDriver;
import com.aegisflow.core.detection.internal.PipelineDetectionDriver;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Real owner operations/transactions, broker transport and Core durable detection. Manual worker driving is explicit. */
@Testcontainers(disabledWithoutDocker=true)
@Timeout(value=120,unit=TimeUnit.SECONDS)
class DetectionPipelineScenario {
    private static final Network NETWORK=Network.newNetwork();
    @Container static final CoreKafkaFixture KAFKA=new CoreKafkaFixture(NETWORK);
    @Container static final PostgreSQLContainer CORE=CorePipelineScenario.database("detection_core");
    @Container static final PostgreSQLContainer ORDER=CorePipelineScenario.database("detection_order");
    @Container static final PostgreSQLContainer PAYMENT=CorePipelineScenario.database("detection_payment");
    @Container static final PostgreSQLContainer WAREHOUSE=CorePipelineScenario.database("detection_warehouse");
    static ConfigurableApplicationContext core;
    static final Map<String,ConfigurableApplicationContext> apps=new LinkedHashMap<>();
    static JdbcTemplate jdbc;

    @BeforeAll static void start() throws Exception {
        try {
            core=PipelineApplications.start("core",CORE,KAFKA);
            core.getBean(KafkaInboxConsumer.class).close();
            jdbc=core.getBean(JdbcTemplate.class);
            for(var entry:Map.of("order",ORDER,"payment",PAYMENT,"warehouse",WAREHOUSE).entrySet())
                apps.put(entry.getKey(),PipelineApplications.start(entry.getKey(),entry.getValue(),KAFKA));
        } catch(Exception failure) { close(); throw failure; }
    }
    @AfterAll static void close() {
        apps.values().forEach(ConfigurableApplicationContext::close);
        if(core!=null) core.close();
    }
    @Test void healthyAllOwnersRemainSatisfiedAfterEnforcementAndKafkaReplay() throws Exception {
        UUID order=UUID.randomUUID(); String tx="healthy-"+order;
        var events=List.of(create("order","positive",order,tx,2),create("payment","positive",order,tx,2),
                create("warehouse","positive",order,tx,2));
        intake(events); project(); Clock after=after(tx); evaluate(after);
        assertThat(result(tx)).isEqualTo("SATISFIED"); assertThat(count("incident",tx)).isZero();
        // New group replays the actual broker records, not manually inserted inbox rows.
        replay(events); project(); evaluate(after);
        assertThat(count("observed_fact",tx)).isEqualTo(3);
        assertThat(count("incident",tx)).isZero();
    }
    @Test void missingOutcomeCreatesOneExplainableIncidentAndLateEvidenceNeverResolvesIt() throws Exception {
        UUID order=UUID.randomUUID(); String tx="missing-"+order;
        var events=List.of(create("order","positive",order,tx,2),create("payment","positive",order,tx,2));
        intake(events); project(); Clock after=after(tx);
        assertThat(count("incident",tx)).isZero();
        PipelineDetectionDriver.evaluate(core,after);
        // Evaluation is already committed; newly constructed competing workers recover its incident.
        try(var pool=Executors.newFixedThreadPool(2)) {
            var tasks=List.of(pool.submit(() -> PipelineDetectionDriver.record(core,after)),
                    pool.submit(() -> PipelineDetectionDriver.record(core,after)));
            for(var task:tasks) task.get(15,TimeUnit.SECONDS);
        }
        assertThat(count("incident",tx)).isEqualTo(1);
        var incident=jdbc.queryForMap("SELECT * FROM aegisflow.incident WHERE business_transaction_id=?",tx);
        assertThat(incident).containsEntry("state","DETECTED").containsEntry("reason_code","MISSING_OBSERVED_OUTCOME")
                .containsEntry("severity","MEDIUM").containsEntry("invariant_id","INV-002");
        assertThat(count("incident_fact_reference",tx)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aegisflow.incident_fact_reference r JOIN aegisflow.observed_fact f USING (tenant_id,business_transaction_id,fact_id) JOIN aegisflow.inbox_message i ON i.tenant_id=f.tenant_id AND i.source_system=f.source_system AND i.event_id=f.inbox_event_id WHERE r.business_transaction_id=?",Integer.class,tx)).isEqualTo(2);
        var late=create("warehouse","positive",order,tx,2);
        intake(List.of(late)); project(); evaluate(after); replay(events); project(); evaluate(after);
        assertThat(count("incident",tx)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT incident_id FROM aegisflow.incident WHERE business_transaction_id=?",UUID.class,tx))
                .isEqualTo(incident.get("incident_id"));
        assertThat(jdbc.queryForObject("SELECT state FROM aegisflow.incident WHERE business_transaction_id=?",String.class,tx)).isEqualTo("DETECTED");
        assertThat(count("incident_timeline",tx)).isGreaterThanOrEqualTo(2);
        assertThat(count("incident_fact_reference",tx)).isGreaterThanOrEqualTo(5);
    }
    @Test void reorderedTransportAndPendingProjectionDoNotCreateFalseIncident() throws Exception {
        UUID order=UUID.randomUUID(); String tx="backlog-"+order;
        // Business order happens first, but its relay is deliberately held until Warehouse is delivered.
        UUID orderEvent=PipelineApplications.create(apps.get("order"),"order","positive",order,tx,2);
        UUID warehouse=create("warehouse","positive",order,tx,2);
        intake(List.of(warehouse)); project();
        PipelineApplications.relay(apps.get("order"),"order"); intake(List.of(orderEvent));
        Clock after=Clock.fixed(Instant.now().plusSeconds(120),ZoneOffset.UTC);
        evaluate(after);
        assertThat(result(tx)).isEqualTo("UNKNOWN"); assertThat(count("incident",tx)).isZero();
        project(); evaluate(after);
        assertThat(result(tx)).isEqualTo("SATISFIED"); assertThat(count("incident",tx)).isZero();
        assertThat(count("observed_fact",tx)).isEqualTo(2);
    }
    @ParameterizedTest @ValueSource(strings={"quantity","contradiction"})
    void invalidBusinessOutcomesCannotMasqueradeAsHealthy(String fault) throws Exception {
        UUID order=UUID.randomUUID(); String tx=fault+"-"+order;
        var events=new ArrayList<UUID>(); events.add(create("order","positive",order,tx,2));
        events.add(create("warehouse","positive",order,tx,fault.equals("quantity")?3:2));
        if(fault.equals("contradiction")) events.add(create("warehouse","negative",order,tx,2));
        intake(events); project(); evaluate(after(tx));
        assertThat(result(tx)).isEqualTo("VIOLATED"); assertThat(count("incident",tx)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reason_code FROM aegisflow.incident WHERE business_transaction_id=?",String.class,tx))
                .isEqualTo(fault.equals("quantity")?"QUANTITY_MISMATCH":"CONTRADICTORY_OUTCOMES");
    }
    @Test void applicationRestartPreservesPendingProjectionThenDurableDeadline() throws Exception {
        UUID order=UUID.randomUUID(); String tx="restart-"+order;
        intake(List.of(create("order","positive",order,tx,2)));
        assertThat(count("observed_fact",tx)).isZero();
        restartCore(); project(); Clock after=after(tx);
        assertThat(count("business_transaction",tx)).isEqualTo(1);
        restartCore(); evaluate(after);
        assertThat(count("incident",tx)).isEqualTo(1);
        assertThat(count("incident_fact_reference",tx)).isEqualTo(1);
    }
    @Test void brokerTenantSpoofIsQuarantinedAndSameTransactionInOtherTenantCannotFillMissingEvidence() throws Exception {
        UUID order=UUID.randomUUID(); String tx="tenant-"+order;
        UUID orderEvent=create("order","positive",order,tx,2);
        UUID paymentEvent=create("payment","positive",order,tx,2);
        intake(List.of(orderEvent,paymentEvent));
        var json=new tools.jackson.databind.json.JsonMapper();
        var spoof=json.readValue(apps.get("order").getBean(JdbcTemplate.class).queryForObject(
                "SELECT envelope::text FROM aegisflow.outbox_event WHERE event_id=?",String.class,orderEvent),Map.class);
        spoof.put("tenantId","pipeline-payment-only"); spoof.put("eventId",UUID.randomUUID().toString());
        var rejected=CorePipelineScenario.publish(KAFKA,"order",spoof,json.writeValueAsBytes(spoof));
        var other=json.readValue(apps.get("payment").getBean(JdbcTemplate.class).queryForObject(
                "SELECT envelope::text FROM aegisflow.outbox_event WHERE event_id=?",String.class,paymentEvent),Map.class);
        UUID otherId=UUID.randomUUID(); other.put("tenantId","pipeline-payment-only"); other.put("eventId",otherId.toString());
        CorePipelineScenario.publish(KAFKA,"payment",other,json.writeValueAsBytes(other));
        try(var consumer=new KafkaConsumer<byte[],byte[]>(InboxConfiguration.consumerProperties(
                KAFKA.bootstrap(),KAFKA.password("core"),"aegisflow-core-detection-"+UUID.randomUUID()));
            var worker=new KafkaInboxConsumer(consumer,core.getBean(InboxStore.class)::accept)) {
            CorePipelineScenario.until(worker,() -> jdbc.queryForObject(
                    "SELECT count(*) FROM aegisflow.inbox_message WHERE event_id=?",Integer.class,otherId)==1
                    && CorePipelineScenario.committed(consumer,rejected));
        }
        assertThat(jdbc.queryForObject("SELECT reason_code FROM aegisflow.quarantine_record WHERE topic=? AND partition_id=? AND offset_id=?",
                String.class,rejected.topic(),rejected.partition(),rejected.offset())).isEqualTo("TENANT_NOT_TRUSTED");
        project(); evaluate(after(tx));
        assertThat(count("incident",tx)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aegisflow.incident WHERE tenant_id='pipeline-payment-only' AND business_transaction_id=?",Integer.class,tx)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aegisflow.observed_fact WHERE tenant_id='pipeline-payment-only' AND business_transaction_id=?",Integer.class,tx)).isEqualTo(1);
        assertThat(count("incident_fact_reference",tx)).isEqualTo(2);
    }
    static void restartCore() throws Exception {
        core.close(); core=PipelineApplications.restart("core",CORE,KAFKA);
        core.getBean(KafkaInboxConsumer.class).close(); jdbc=core.getBean(JdbcTemplate.class);
    }
    static UUID create(String owner,String outcome,UUID order,String tx,int qty) throws Exception {
        var app=apps.get(owner); UUID id=PipelineApplications.create(app,owner,outcome,order,tx,qty);
        var db=app.getBean(JdbcTemplate.class);
        assertThat(db.queryForObject("SELECT status FROM aegisflow.outbox_event WHERE event_id=?",String.class,id)).isEqualTo("PENDING");
        PipelineApplications.relay(app,owner);
        assertThat(db.queryForObject("SELECT status FROM aegisflow.outbox_event WHERE event_id=?",String.class,id)).isEqualTo("PUBLISHED");
        return id;
    }
    static void intake(List<UUID> ids) {
        try(var consumer=new KafkaConsumer<byte[],byte[]>(InboxConfiguration.consumerProperties(
                KAFKA.bootstrap(),KAFKA.password("core"),"aegisflow-core-detection-"+UUID.randomUUID()));
            var worker=new KafkaInboxConsumer(consumer,core.getBean(InboxStore.class)::accept)) {
            CorePipelineScenario.until(worker,() -> ids.stream().allMatch(id -> jdbc.queryForObject(
                    "SELECT count(*) FROM aegisflow.inbox_message WHERE event_id=?",Integer.class,id)==1));
        }
    }
    static void replay(List<UUID> ids) {
        Set<UUID> seen=new HashSet<>();
        try(var consumer=new KafkaConsumer<byte[],byte[]>(InboxConfiguration.consumerProperties(
                KAFKA.bootstrap(),KAFKA.password("core"),"aegisflow-core-detection-"+UUID.randomUUID()));
            var worker=new KafkaInboxConsumer(consumer,record -> {
                String result=core.getBean(InboxStore.class).accept(record);
                for(UUID id:ids) if(CorePipelineScenario.contains(record,id)) { assertThat(result).isEqualTo("DUPLICATE"); seen.add(id); }
                return result;
            })) { CorePipelineScenario.until(worker,() -> seen.containsAll(ids)); }
    }
    static void project() { PipelineProjectionDriver.drain(core,Clock.systemUTC()); }
    static void evaluate(Clock clock) { PipelineDetectionDriver.evaluate(core,clock); PipelineDetectionDriver.record(core,clock); }
    static Clock after(String tx) {
        return Clock.fixed(jdbc.queryForObject("SELECT enforce_after FROM aegisflow.business_transaction WHERE tenant_id=? AND business_transaction_id=?",
                java.sql.Timestamp.class,PipelineApplications.TENANT,tx).toInstant().plusSeconds(1),ZoneOffset.UTC);
    }
    static int count(String table,String tx) {
        return jdbc.queryForObject("SELECT count(*) FROM aegisflow."+table+" WHERE tenant_id=? AND business_transaction_id=?",
                Integer.class,PipelineApplications.TENANT,tx);
    }
    static String result(String tx) {
        return jdbc.queryForObject("SELECT result FROM aegisflow.invariant_evaluation WHERE business_transaction_id=? ORDER BY projection_version DESC,unsettled ASC LIMIT 1",String.class,tx);
    }
}
