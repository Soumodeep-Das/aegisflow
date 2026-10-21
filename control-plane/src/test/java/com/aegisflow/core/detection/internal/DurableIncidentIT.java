package com.aegisflow.core.detection.internal;

import static org.assertj.core.api.Assertions.*;
import com.aegisflow.core.correlation.internal.DetectionFixture;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessException;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker=true)
@Timeout(90)
class DurableIncidentIT {
    @Container static final PostgreSQLContainer POSTGRES=DetectionFixture.container();
    static final DetectionFixture fixture=new DetectionFixture();
    static final Instant START=DetectionFixture.startTime();
    Clock clock;
    @BeforeAll static void start() { fixture.start(); }
    @AfterAll static void stop() { fixture.close(); }
    @BeforeEach void reset() { fixture.clear(); clock=Clock.fixed(START.plusSeconds(60),ZoneOffset.UTC); }
    IncidentRecorder recorder() { return new IncidentRecorder(DetectionFixture.jdbc(),DetectionFixture.manager(),clock,25); }
    int count(String table) { return DetectionFixture.jdbc().queryForObject("SELECT count(*) FROM aegisflow."+table,Integer.class); }
    void order(String tenant,String tx) { fixture.accept(tenant,tx,"OrderCreated","order"); fixture.project(); }
    void evaluate() {
        var detector=new DeadlineDetector(fixture.projections(),DetectionFixture.inbox(),DetectionFixture.jdbc(),
            DetectionFixture.manager(),clock,25);
        detector.poll(); detector.poll();
    }
    UUID id() { return DetectionFixture.jdbc().queryForObject("SELECT incident_id FROM aegisflow.incident",UUID.class); }
    @Test void violationCreatesOneDetectedEvidenceBackedIncident() {
        order("tenant-a","txn"); fixture.accept("tenant-a","txn","PaymentAuthorized","order"); fixture.project();
        evaluate(); assertThat(count("incident")).isZero(); recorder().poll();
        assertThat(count("incident")).isEqualTo(1); assertThat(count("incident_fact_reference")).isEqualTo(2);
        var incident=DetectionFixture.jdbc().queryForMap("SELECT state,state_version,incident_revision,reason_code FROM aegisflow.incident");
        assertThat(incident).containsEntry("state","DETECTED").containsEntry("state_version",1L)
            .containsEntry("incident_revision",1L).containsEntry("reason_code","MISSING_OBSERVED_OUTCOME");
    }
    @Test void repeatedPollingAndWorkerRestartPreserveIdentity() {
        order("tenant-a","txn"); evaluate(); recorder().poll(); var original=id();
        recorder().poll(); evaluate(); recorder().poll();
        assertThat(id()).isEqualTo(original); assertThat(count("incident_timeline")).isEqualTo(1);
    }
    @Test void healthyOutcomeNeverCreatesAnIncident() {
        order("tenant-a","txn"); fixture.accept("tenant-a","txn","InventoryReserved","order"); fixture.project();
        evaluate(); assertThat(recorder().poll()).isZero(); assertThat(count("incident")).isZero();
    }
    @Test void unknownBacklogNeverCreatesAnIncident() {
        order("tenant-a","txn"); fixture.accept("tenant-a","txn","InventoryReserved","order");
        evaluate(); recorder().poll(); assertThat(count("incident")).isZero();
    }
    @Test void laterSatisfiedEvidenceAppendsWithoutResolving() {
        order("tenant-a","txn"); evaluate(); recorder().poll(); var original=id();
        fixture.accept("tenant-a","txn","InventoryReserved","order"); fixture.project();
        clock=Clock.fixed(START.plusSeconds(90),ZoneOffset.UTC); evaluate(); recorder().poll();
        assertThat(id()).isEqualTo(original); assertThat(count("incident_timeline")).isEqualTo(2);
        assertThat(count("incident_fact_reference")).isEqualTo(3);
        assertThat(DetectionFixture.jdbc().queryForMap("SELECT state,state_version,incident_revision FROM aegisflow.incident"))
            .containsEntry("state","DETECTED").containsEntry("state_version",1L).containsEntry("incident_revision",2L);
        assertThat(DetectionFixture.jdbc().queryForObject("SELECT count(*) FROM aegisflow.invariant_evaluation WHERE result='VIOLATED'",Integer.class)).isEqualTo(1);
    }
    @Test void allEarlierEvaluationsAreBackfilledAfterCreation() {
        order("tenant-a","txn"); clock=Clock.fixed(START.plusSeconds(59),ZoneOffset.UTC); evaluate();
        assertThat(recorder().poll()).isZero();
        clock=Clock.fixed(START.plusSeconds(60),ZoneOffset.UTC); evaluate(); recorder().poll();
        assertThat(count("incident_timeline")).isEqualTo(2); assertThat(count("incident")).isEqualTo(1);
    }
    @Test void sameTransactionInDifferentTenantsHasSeparateIncidents() {
        order("tenant-a","same"); order("tenant-b","same"); evaluate(); recorder().poll();
        assertThat(count("incident")).isEqualTo(2);
        assertThat(DetectionFixture.jdbc().queryForObject("SELECT count(DISTINCT incident_id) FROM aegisflow.incident",Integer.class)).isEqualTo(2);
    }
    @Test void databaseUniquenessRejectsDuplicateEpisode() {
        order("tenant-a","txn"); evaluate(); recorder().poll();
        assertThatThrownBy(() -> DetectionFixture.jdbc().update("""
            INSERT INTO aegisflow.incident SELECT tenant_id,gen_random_uuid(),business_transaction_id,
                invariant_id,invariant_version,contract_id,contract_version,configuration_version,episode_key,
                severity,reason_code,state,state_version,incident_revision,opened_at,updated_at,initial_evaluation_id
            FROM aegisflow.incident
            """)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void concurrentRecordersProduceOneTimelineAndIncident() throws Exception {
        order("tenant-a","txn"); evaluate();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> recorder().poll()); var b=pool.submit(() -> recorder().poll());
            a.get(15,TimeUnit.SECONDS); b.get(15,TimeUnit.SECONDS);
        }
        recorder().poll(); assertThat(count("incident")).isEqualTo(1); assertThat(count("incident_timeline")).isEqualTo(1);
    }
    @Test void failureAfterTimelineInsertRollsBackEveryIncidentEffect() {
        order("tenant-a","txn"); evaluate();
        DetectionFixture.admin().execute("""
            CREATE FUNCTION aegisflow.fail_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
            BEGIN RAISE EXCEPTION 'synthetic rollback'; END $$
            """);
        DetectionFixture.admin().execute("CREATE TRIGGER fail_evidence BEFORE INSERT ON aegisflow.incident_fact_reference FOR EACH ROW EXECUTE FUNCTION aegisflow.fail_evidence()");
        try {
            assertThatThrownBy(() -> recorder().poll()).isInstanceOf(DataAccessException.class);
            assertThat(count("incident")).isZero(); assertThat(count("incident_timeline")).isZero();
            assertThat(count("invariant_evaluation")).isEqualTo(1);
        } finally {
            DetectionFixture.admin().execute("DROP TRIGGER fail_evidence ON aegisflow.incident_fact_reference");
            DetectionFixture.admin().execute("DROP FUNCTION aegisflow.fail_evidence()");
        }
        recorder().poll(); assertThat(count("incident")).isEqualTo(1);
    }
    @Test void runtimeCannotRewriteHistoryOrDeclareResolution() {
        order("tenant-a","txn"); evaluate(); recorder().poll();
        for(String sql:List.of("DELETE FROM aegisflow.incident","UPDATE aegisflow.incident SET state='RESOLVED'",
                "UPDATE aegisflow.incident_timeline SET entry_kind='EVIDENCE_CHANGED'",
                "DELETE FROM aegisflow.incident_fact_reference")) {
            assertThatThrownBy(() -> DetectionFixture.jdbc().update(sql)).isInstanceOf(DataAccessException.class);
        }
    }
    @Test void crossTenantFactReferenceIsRejected() {
        order("tenant-a","txn"); order("tenant-b","txn"); evaluate(); recorder().poll();
        assertThatThrownBy(() -> DetectionFixture.jdbc().update("""
            INSERT INTO aegisflow.incident_fact_reference
            SELECT t.tenant_id,t.business_transaction_id,t.incident_id,t.evaluation_id,f.fact_id
            FROM aegisflow.incident_timeline t CROSS JOIN aegisflow.observed_fact f
            WHERE t.tenant_id='tenant-a' AND f.tenant_id='tenant-b'
            """)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void crossTransactionFactReferenceIsRejected() {
        order("tenant-a","one"); order("tenant-a","two"); evaluate(); recorder().poll();
        assertThatThrownBy(() -> DetectionFixture.jdbc().update("""
            INSERT INTO aegisflow.incident_fact_reference
            SELECT t.tenant_id,t.business_transaction_id,t.incident_id,t.evaluation_id,f.fact_id
            FROM aegisflow.incident_timeline t CROSS JOIN aegisflow.observed_fact f
            WHERE t.business_transaction_id='one' AND f.business_transaction_id='two'
            """)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void pendingLateVisibleEvaluationIsNotLostBehindCursor() {
        // C has no advancing cursor: even an earlier evaluatedAt remains eligible after later work.
        order("tenant-a","later"); evaluate(); recorder().poll();
        clock=Clock.fixed(START.plusSeconds(59),ZoneOffset.UTC);
        order("tenant-a","earlier"); evaluate();
        clock=Clock.fixed(START.plusSeconds(60),ZoneOffset.UTC); evaluate(); recorder().poll();
        assertThat(count("incident")).isEqualTo(2);
    }
    @Test void boundedBatchLeavesRemainingWorkDurable() {
        order("tenant-a","one"); order("tenant-a","two"); evaluate();
        var bounded=new IncidentRecorder(DetectionFixture.jdbc(),DetectionFixture.manager(),clock,1);
        assertThat(bounded.poll()).isEqualTo(1); assertThat(count("incident")).isEqualTo(1);
        recorder().poll(); assertThat(count("incident")).isEqualTo(2);
    }
    @Test void productionCallbackConsumesDurableEvaluations() {
        order("tenant-a","txn"); evaluate();
        new IncidentConfiguration.IncidentTick(recorder()).tick();
        assertThat(count("incident")).isEqualTo(1);
    }
    @Test void separateJvmRestartPreservesIdentityAndAddsLateEvidence() throws Exception {
        order("tenant-a","txn"); evaluate(); child(); var original=id();
        fixture.accept("tenant-a","txn","InventoryReserved","order"); fixture.project();
        clock=Clock.fixed(START.plusSeconds(90),ZoneOffset.UTC); evaluate(); child();
        assertThat(id()).isEqualTo(original); assertThat(count("incident_timeline")).isEqualTo(2);
        assertThat(DetectionFixture.jdbc().queryForObject("SELECT state FROM aegisflow.incident",String.class)).isEqualTo("DETECTED");
    }
    void child() throws Exception {
        Path log=Path.of("target","incident-process",UUID.randomUUID()+".log").toAbsolutePath();
        Files.createDirectories(log.getParent());
        String java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        Path arguments=log.resolveSibling(log.getFileName()+".args");
        Files.writeString(arguments,IncidentProcessLaunch.arguments(
            System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"))));
        var builder=new ProcessBuilder(java,"@"+arguments).redirectErrorStream(true).redirectOutput(log.toFile());
        var inherited=IncidentProcessLaunch.environment(builder.environment());
        builder.environment().clear(); builder.environment().putAll(inherited);
        builder.environment().put("INCIDENT_TEST_URL",POSTGRES.getJdbcUrl());
        builder.environment().put("INCIDENT_TEST_USER","aegisflow_core_runtime");
        builder.environment().put("INCIDENT_TEST_PASSWORD","synthetic-projection-only");
        builder.environment().put("INCIDENT_TEST_TIME",clock.instant().toString());
        var process=builder.start();
        try {
            assertThat(process.waitFor(25,TimeUnit.SECONDS)).as("child completion; inspect %s",log).isTrue();
            assertThat(process.exitValue()).as("child exit; inspect %s",log).isZero();
        } finally { if(process.isAlive()) process.destroyForcibly(); }
    }
}
