package com.aegisflow.core.eventing.internal;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Actual executable workers, not calls to projector/detector/recorder methods. */
@Testcontainers(disabledWithoutDocker=true)
class PackagedDetectionScenario {
    @Container static final CoreKafkaFixture KAFKA=PackagedPipelineScenario.KAFKA;
    @Container static final PostgreSQLContainer CORE=PackagedPipelineScenario.CORE;
    @Container static final PostgreSQLContainer ORDER=PackagedPipelineScenario.ORDER;
    @Container static final PostgreSQLContainer PAYMENT=PackagedPipelineScenario.PAYMENT;
    @Container static final PostgreSQLContainer WAREHOUSE=PackagedPipelineScenario.WAREHOUSE;

    @Test @Timeout(value=300,unit=TimeUnit.SECONDS)
    void executableSchedulersDetectMissingEvidenceAndPreserveHistoryAcrossCoreRestart() throws Exception {
        var databases=Map.of("core",CORE,"order",ORDER,"payment",PAYMENT,"warehouse",WAREHOUSE);
        var contexts=new LinkedHashMap<String,ConfigurableApplicationContext>();
        UUID healthy=UUID.randomUUID(), missing=UUID.randomUUID();
        String healthyTx="healthy-"+healthy, missingTx="missing-"+missing;
        Map<String,List<UUID>> events=new LinkedHashMap<>(); UUID held;
        try {
            for(String owner:List.of("core","order","payment","warehouse"))
                contexts.put(owner,PipelineApplications.start(owner,databases.get(owner),KAFKA));
            // All contexts are ready before the first business operation: matching evidence occurs within 45s.
            for(String owner:List.of("order","payment","warehouse")) {
                events.put(owner,List.of(PipelineApplications.create(contexts.get(owner),owner,"positive",healthy,healthyTx,2),
                        PipelineApplications.create(contexts.get(owner),owner,"positive",missing,missingTx,2)));
            }
            held=events.get("warehouse").get(1);
            contexts.get("warehouse").getBean(JdbcTemplate.class).update(
                    "UPDATE aegisflow.outbox_event SET next_attempt_at=clock_timestamp()+interval '1 day' WHERE event_id=?",held);
        } finally { contexts.values().forEach(ConfigurableApplicationContext::close); }
        var db=PackagedPipelineScenario.jdbc("core",CORE);
        for(var entry:events.entrySet()) for(UUID id:entry.getValue())
            assertThat(PackagedPipelineScenario.jdbc(entry.getKey(),databases.get(entry.getKey())).queryForObject(
                    "SELECT status FROM aegisflow.outbox_event WHERE event_id=?",String.class,id)).isEqualTo("PENDING");
        assertThat(db.queryForObject("SELECT count(*) FROM aegisflow.inbox_message",Integer.class)).isZero();
        Path logs=PipelineApplications.root().resolve(".cache/packaged-detection/"+UUID.randomUUID()); Files.createDirectories(logs);
        System.out.println("Packaged detection logs: "+logs);
        String group="aegisflow-core-detection-"+UUID.randomUUID();
        Map<String,String> enabled=Map.of("CORE_PROJECTION_ENABLED","true","CORE_DETECTION_ENABLED","true","CORE_INCIDENTS_ENABLED","true");
        var owned=new ArrayList<PackagedApplicationProcess>();
        try {
            var core=PackagedPipelineScenario.start("core",CORE,logs.resolve("core.log"),group,enabled); owned.add(core);
            for(String owner:List.of("order","payment","warehouse"))
                owned.add(PackagedPipelineScenario.start(owner,databases.get(owner),logs.resolve(owner+".log"),group));
            PackagedPipelineScenario.await(Duration.ofSeconds(120),() -> core.healthy()
                    && count(db,"incident",missingTx)==1
                    && db.queryForObject("SELECT count(*) FROM aegisflow.invariant_evaluation WHERE business_transaction_id=? AND phase='ENFORCED' AND result='SATISFIED'",Integer.class,healthyTx)>0);
            assertThat(count(db,"incident",healthyTx)).isZero();
            assertThat(count(db,"observed_fact",healthyTx)).isEqualTo(3);
            assertThat(count(db,"observed_fact",missingTx)).isEqualTo(2);
            assertThat(count(db,"incident_fact_reference",missingTx)).isGreaterThanOrEqualTo(2);
            var initial=db.queryForMap("SELECT * FROM aegisflow.incident WHERE business_transaction_id=?",missingTx);
            assertThat(initial).containsEntry("reason_code","MISSING_OBSERVED_OUTCOME").containsEntry("state","DETECTED");
            core.close(); assertThat(core.alive()).isFalse();
            var warehouseDb=PackagedPipelineScenario.jdbc("warehouse",WAREHOUSE);
            warehouseDb.update("UPDATE aegisflow.outbox_event SET next_attempt_at=clock_timestamp() WHERE event_id=?",held);
            PackagedPipelineScenario.await(Duration.ofSeconds(30),() -> warehouseDb.queryForObject(
                    "SELECT status FROM aegisflow.outbox_event WHERE event_id=?",String.class,held).equals("PUBLISHED"));
            var restarted=PackagedPipelineScenario.start("core",CORE,logs.resolve("core-restart.log"),group,enabled); owned.add(restarted);
            PackagedPipelineScenario.await(Duration.ofSeconds(90),() -> restarted.healthy()
                    && count(db,"observed_fact",missingTx)==3
                    && db.queryForObject("SELECT count(*) FROM aegisflow.incident_timeline t JOIN aegisflow.invariant_evaluation e USING (tenant_id,business_transaction_id,evaluation_id) WHERE t.business_transaction_id=? AND e.result='SATISFIED'",Integer.class,missingTx)>0);
            assertThat(count(db,"incident",missingTx)).isEqualTo(1);
            assertThat(db.queryForMap("SELECT incident_id,state,reason_code FROM aegisflow.incident WHERE business_transaction_id=?",missingTx))
                    .containsEntry("incident_id",initial.get("incident_id")).containsEntry("state","DETECTED")
                    .containsEntry("reason_code","MISSING_OBSERVED_OUTCOME");
            assertThat(count(db,"incident",healthyTx)).isZero();
            assertThat(db.queryForObject("SELECT count(*) FROM aegisflow.quarantine_record",Integer.class)).isZero();
            assertThat(db.queryForObject("SELECT count(*) FROM aegisflow.inbox_message",Integer.class)).isEqualTo(6);
            // Retain only scoped synthetic evidence, never credentials or environment dumps.
            Files.writeString(logs.resolve("incident-evidence.txt"),db.queryForList("SELECT i.tenant_id,i.business_transaction_id,i.incident_id,i.state,i.reason_code,i.severity,e.invariant_id,e.invariant_version,e.contract_id,e.contract_version,e.configuration_version,e.due_at,e.enforce_after,e.evaluated_at,e.result,e.reason_code AS evaluation_reason,e.observation,t.entry_kind FROM aegisflow.incident i JOIN aegisflow.incident_timeline t USING (tenant_id,business_transaction_id,incident_id) JOIN aegisflow.invariant_evaluation e USING (tenant_id,business_transaction_id,evaluation_id) WHERE i.business_transaction_id=?",missingTx).toString());
        } finally { Collections.reverse(owned); owned.forEach(PackagedApplicationProcess::close); }
    }
    static int count(JdbcTemplate jdbc,String table,String tx) {
        return jdbc.queryForObject("SELECT count(*) FROM aegisflow."+table+" WHERE tenant_id=? AND business_transaction_id=?",
                Integer.class,PipelineApplications.TENANT,tx);
    }
}
