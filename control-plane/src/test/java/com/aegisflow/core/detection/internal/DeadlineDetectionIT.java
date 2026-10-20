package com.aegisflow.core.detection.internal;

import static org.assertj.core.api.Assertions.*;
import com.aegisflow.core.correlation.DetectionProjections;
import com.aegisflow.core.correlation.internal.DetectionFixture;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** INV-002 / FM-001,002: production intake/projector/detector with real SQL and a mutable clock. */
@Testcontainers(disabledWithoutDocker=true)
@Timeout(60)
class DeadlineDetectionIT {
    @Container static final PostgreSQLContainer POSTGRES=DetectionFixture.container();
    static final DetectionFixture fixture=new DetectionFixture();
    static final Instant START=DetectionFixture.startTime();
    MutableClock clock;
    @BeforeAll static void start() { fixture.start(); }
    @AfterAll static void stop() { fixture.close(); }
    @BeforeEach void clear() { fixture.clear(); clock=new MutableClock(START); }
    DeadlineDetector detector() { return detector(fixture.projections(),25); }
    DeadlineDetector detector(DetectionProjections port,int batch) {
        return new DeadlineDetector(port,DetectionFixture.inbox(),DetectionFixture.jdbc(),DetectionFixture.manager(),clock,batch);
    }
    void order(String tenant,String tx) { fixture.accept(tenant,tx,"OrderCreated","order"); fixture.project(); }
    void sweep(DeadlineDetector worker) { worker.poll(); worker.poll(); } // includes cyclic wrap, no wall-clock sleeps
    int count() { return DetectionFixture.jdbc().queryForObject("SELECT count(*) FROM aegisflow.invariant_evaluation",Integer.class); }
    String latest() {
        return DetectionFixture.jdbc().queryForObject("""
            SELECT result FROM aegisflow.invariant_evaluation ORDER BY evaluated_at DESC,projection_version DESC LIMIT 1
            """,String.class);
    }
    @Test void evaluatesAtThresholdAfterWorkerRestartAndDeduplicates() {
        order("tenant-a","txn"); clock.at=START.plusSeconds(59);
        sweep(detector()); assertThat(latest()).isEqualTo("UNKNOWN");
        clock.at=START.plusSeconds(60); sweep(detector()); assertThat(latest()).isEqualTo("VIOLATED");
        sweep(detector()); assertThat(count()).isEqualTo(2);
    }
    @Test void outcomeBeforeOrderConvergesToSatisfied() {
        fixture.accept("tenant-a","txn","InventoryReserved","order"); fixture.project();
        order("tenant-a","txn"); clock.at=START.plusSeconds(60);
        sweep(detector()); assertThat(latest()).isEqualTo("SATISFIED");
    }
    @Test void inventoryRejectionSatisfiesOutcomeContract() {
        order("tenant-a","txn"); fixture.accept("tenant-a","txn","InventoryRejected","order"); fixture.project();
        clock.at=START.plusSeconds(60); sweep(detector()); assertThat(latest()).isEqualTo("SATISFIED");
    }
    @Test void pendingWarehouseBacklogIsUnknownThenSatisfied() {
        order("tenant-a","txn"); fixture.accept("tenant-a","txn","InventoryReserved","order");
        clock.at=START.plusSeconds(60); sweep(detector()); assertThat(latest()).isEqualTo("UNKNOWN");
        fixture.project(); clock.at=START.plusSeconds(61); sweep(detector()); assertThat(latest()).isEqualTo("SATISFIED");
        assertThat(DetectionFixture.jdbc().queryForObject("SELECT count(*) FROM aegisflow.invariant_evaluation WHERE result='VIOLATED'",Integer.class)).isZero();
    }
    @Test void laterEvidencePreservesOriginalViolation() {
        order("tenant-a","txn"); clock.at=START.plusSeconds(60); sweep(detector());
        fixture.accept("tenant-a","txn","InventoryReserved","order"); fixture.project();
        clock.at=START.plusSeconds(90); sweep(detector());
        assertThat(count()).isEqualTo(2); assertThat(latest()).isEqualTo("SATISFIED");
        assertThat(DetectionFixture.jdbc().queryForObject("SELECT count(*) FROM aegisflow.invariant_evaluation WHERE result='VIOLATED'",Integer.class)).isEqualTo(1);
    }
    @Test void tenantKeysAndEvidenceRemainSeparated() {
        order("tenant-a","same"); order("tenant-b","same");
        fixture.accept("tenant-b","same","InventoryReserved","order"); fixture.project();
        clock.at=START.plusSeconds(60); sweep(detector());
        assertThat(count()).isEqualTo(2);
        assertThat(DetectionFixture.jdbc().queryForObject("SELECT result FROM aegisflow.invariant_evaluation WHERE tenant_id='tenant-a'",String.class)).isEqualTo("VIOLATED");
        assertThat(DetectionFixture.jdbc().queryForObject("SELECT result FROM aegisflow.invariant_evaluation WHERE tenant_id='tenant-b'",String.class)).isEqualTo("SATISFIED");
    }
    @Test void concurrentWorkersCannotDuplicateDecisions() throws Exception {
        order("tenant-a","txn"); clock.at=START.plusSeconds(60);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> detector().poll()); var b=pool.submit(() -> detector().poll());
            // Repeatable-read contenders may serialize-fail; the scheduler retries the complete rolled-back pass.
            for(var future:List.of(a,b)) {
                try { future.get(15,TimeUnit.SECONDS); }
                catch(ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(org.springframework.dao.TransientDataAccessException.class);
                }
            }
        }
        sweep(detector()); assertThat(count()).isEqualTo(1);
    }
    @Test void failureAfterEvaluationInsertRollsBackCursorAndResult() {
        order("tenant-a","txn"); clock.at=START.plusSeconds(60);
        DetectionFixture.admin().execute("""
            CREATE FUNCTION aegisflow.fail_sweep() RETURNS trigger LANGUAGE plpgsql AS $$
            BEGIN RAISE EXCEPTION 'synthetic rollback'; END $$
            """);
        DetectionFixture.admin().execute("CREATE TRIGGER fail_sweep BEFORE UPDATE ON aegisflow.detection_sweep FOR EACH ROW EXECUTE FUNCTION aegisflow.fail_sweep()");
        try {
            assertThatThrownBy(() -> detector().poll()).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(count()).isZero();
        } finally {
            DetectionFixture.admin().execute("DROP TRIGGER fail_sweep ON aegisflow.detection_sweep");
            DetectionFixture.admin().execute("DROP FUNCTION aegisflow.fail_sweep()");
        }
        sweep(detector()); assertThat(count()).isEqualTo(1);
    }
    @Test void cyclicBoundedScanFindsEarlierNewKeysAndOverdueWork() {
        order("tenant-a","z"); clock.at=START.plusSeconds(60);
        var worker=detector(fixture.projections(),1); worker.poll();
        order("tenant-a","a"); sweep(worker); sweep(worker);
        assertThat(count()).isEqualTo(2);
    }
    @Test void databaseUnavailableAttemptRetainsDeadlineForRetry() {
        order("tenant-a","txn"); clock.at=START.plusSeconds(60);
        DetectionProjections unavailable=(t,x,l) -> { throw new org.springframework.dao.DataAccessResourceFailureException("synthetic outage"); };
        assertThatThrownBy(() -> detector(unavailable,25).poll()).isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
        assertThat(count()).isZero(); sweep(detector()); assertThat(count()).isEqualTo(1);
    }
    @Test void runtimeCannotRewriteOrDeleteEvaluationHistory() {
        order("tenant-a","txn"); clock.at=START.plusSeconds(60); sweep(detector());
        assertThatThrownBy(() -> DetectionFixture.jdbc().update("DELETE FROM aegisflow.invariant_evaluation")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> DetectionFixture.jdbc().update("UPDATE aegisflow.invariant_evaluation SET result='SATISFIED'")).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void exactObservationAndContractAreStoredWithoutRemoteClaims() {
        order("tenant-a","txn"); clock.at=START.plusSeconds(60); sweep(detector());
        var row=DetectionFixture.jdbc().queryForMap("""
            SELECT contract_id,configuration_version,lateness_seconds,
                observation->>'tenant' AS observed_tenant,
                jsonb_array_length(observation->'facts') AS facts,
                extract(epoch FROM enforce_after-due_at)::int AS allowance
            FROM aegisflow.invariant_evaluation
            """);
        assertThat(row).containsEntry("contract_id","ECOM_ORDER_V1").containsEntry("configuration_version","demo-v1")
            .containsEntry("lateness_seconds",15).containsEntry("allowance",15).containsEntry("observed_tenant","tenant-a").containsEntry("facts",1);
    }
    @Test void blockedProjectionIsUnknownAndNeverMissing() {
        order("tenant-a","txn"); fixture.accept("tenant-a","txn","InventoryReserved","order");
        new org.springframework.transaction.support.TransactionTemplate(DetectionFixture.manager()).execute(s -> {
            var port=DetectionFixture.inbox();
            var pending=port.pending(1).getFirst();
            port.blocked(pending.key(),"ACCEPTED_SHAPE_UNSUPPORTED",START);
            return null;
        });
        clock.at=START.plusSeconds(60); sweep(detector());
        assertThat(latest()).isEqualTo("UNKNOWN");
        assertThat(DetectionFixture.jdbc().queryForObject("SELECT reason_code FROM aegisflow.invariant_evaluation",String.class))
            .isEqualTo("PROJECTION_UNSETTLED");
    }
    @Test void conflictingWarehouseFactsAreNotSilentlyReplaced() {
        order("tenant-a","txn");
        fixture.accept("tenant-a","txn","InventoryReserved","order");
        fixture.accept("tenant-a","txn","InventoryRejected","order"); fixture.project();
        clock.at=START.plusSeconds(60); sweep(detector());
        assertThat(DetectionFixture.jdbc().queryForObject("SELECT reason_code FROM aegisflow.invariant_evaluation",String.class))
            .isEqualTo("CONTRADICTORY_OUTCOMES");
    }
    @Test void wrongOrderOutcomeViolatesWithoutCrossTransactionJoin() {
        order("tenant-a","txn");
        fixture.accept("tenant-a","txn","InventoryReserved","different"); fixture.project();
        clock.at=START.plusSeconds(60); sweep(detector());
        assertThat(DetectionFixture.jdbc().queryForObject("SELECT reason_code FROM aegisflow.invariant_evaluation",String.class))
            .isEqualTo("ORDER_MISMATCH");
    }
    @Test void productionScheduledCallbackDrivesDueWorkWithoutDeadlineSleep() {
        order("tenant-a","txn"); clock.at=START.plusSeconds(60);
        new DetectionConfiguration.DetectionTick(detector()).tick();
        assertThat(count()).isEqualTo(1); assertThat(latest()).isEqualTo("VIOLATED");
    }
    @Test void observationTenantCannotDisagreeWithRelationalTenant() {
        order("tenant-a","txn"); order("tenant-b","txn"); clock.at=START.plusSeconds(60); sweep(detector());
        assertThatThrownBy(() -> DetectionFixture.jdbc().update("""
            INSERT INTO aegisflow.invariant_evaluation
            SELECT 'tenant-b',gen_random_uuid(),business_transaction_id,invariant_id,invariant_version,
                projection_version+1,phase,unsettled,result,reason_code,evaluated_at,
                contract_id,contract_version,configuration_version,inventory_duration_seconds,lateness_seconds,
                due_at,enforce_after,observation
            FROM aegisflow.invariant_evaluation WHERE tenant_id='tenant-a'
            """)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    static final class MutableClock extends Clock {
        volatile Instant at;
        MutableClock(Instant at) { this.at=at; }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return at; }
    }
}
