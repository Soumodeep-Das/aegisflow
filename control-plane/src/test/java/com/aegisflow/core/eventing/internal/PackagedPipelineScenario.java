package com.aegisflow.core.eventing.internal;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Final gate: actual executable JARs, real schedulers, ephemeral HTTP, and Core process restart. */
@Testcontainers(disabledWithoutDocker=true)
class PackagedPipelineScenario {
    private static final Network NETWORK=Network.newNetwork();
    @Container static final CoreKafkaFixture KAFKA=new CoreKafkaFixture(NETWORK);
    @Container static final PostgreSQLContainer CORE=database("core");
    @Container static final PostgreSQLContainer ORDER=database("order");
    @Container static final PostgreSQLContainer PAYMENT=database("payment");
    @Container static final PostgreSQLContainer WAREHOUSE=database("warehouse");
    private static PostgreSQLContainer database(String owner) {
        return new PostgreSQLContainer(DockerImageName.parse(
                "postgres:18.6@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336")
                .asCompatibleSubstituteFor("postgres")).withDatabaseName("packaged_"+owner)
                .withUsername("inspection_admin").withPassword(PipelineApplications.PASSWORD);
    }
    @Test @Timeout(value=180,unit=TimeUnit.SECONDS)
    void scheduledExecutableJarsDeliverAllOwnersAndCoreRestartsAgainstRetainedDatabaseAndOffsets() throws Exception {
        Map<String,PostgreSQLContainer> databases=Map.of("core",CORE,"order",ORDER,"payment",PAYMENT,"warehouse",WAREHOUSE);
        Map<String,UUID> firstEvents=new LinkedHashMap<>();
        UUID held=null;
        // Only the seed harness uses manual polling. It commits real business/outbox writes without sending.
        try(var context=PipelineApplications.start("core",CORE,KAFKA)) {
            assertThat(context.getBean(JdbcTemplate.class).queryForObject("SELECT count(*) FROM aegisflow.inbox_message",Integer.class)).isZero();
        }
        for(String owner:List.of("order","payment","warehouse")) {
            try(var context=PipelineApplications.start(owner,databases.get(owner),KAFKA)) {
                firstEvents.put(owner,PipelineApplications.create(context,owner,"positive"));
                if(owner.equals("order")) {
                    held=PipelineApplications.create(context,owner,"positive");
                    context.getBean(JdbcTemplate.class).update(
                            "UPDATE aegisflow.outbox_event SET next_attempt_at=clock_timestamp()+interval '1 day' WHERE event_id=?",held);
                }
            }
        }
        UUID heldEvent=Objects.requireNonNull(held);
        var coreDb=jdbc("core",CORE); var orderDb=jdbc("order",ORDER);
        // Prove the seeding harness did not publish; the executable source schedulers must do the work.
        for(var entry:firstEvents.entrySet()) {
            assertThat(jdbc(entry.getKey(),databases.get(entry.getKey())).queryForObject(
                    "SELECT status FROM aegisflow.outbox_event WHERE event_id=?",String.class,entry.getValue())).isEqualTo("PENDING");
        }
        assertThat(coreDb.queryForObject("SELECT count(*) FROM aegisflow.inbox_message",Integer.class)).isZero();
        Path logs=PipelineApplications.root().resolve(".cache/packaged-pipeline/"+UUID.randomUUID());
        Files.createDirectories(logs);
        System.out.println("Packaged application logs: "+logs);
        String group="aegisflow-core-packaged-"+UUID.randomUUID();
        List<PackagedApplicationProcess> owned=new ArrayList<>();
        try {
            Map<String,PackagedApplicationProcess> apps=new LinkedHashMap<>();
            for(String owner:List.of("core","order","payment","warehouse")) {
                var app=start(owner,databases.get(owner),logs.resolve(owner+".log"),group);
                apps.put(owner,app); owned.add(app);
            }
            await(Duration.ofSeconds(90),() -> {
                boolean healthy=true;
                for(var process:apps.values()) healthy &= process.healthy();
                return healthy && firstEvents.values().stream().allMatch(id -> count(coreDb,id)==1);
            });
            for(var entry:firstEvents.entrySet()) {
                assertThat(jdbc(entry.getKey(),databases.get(entry.getKey())).queryForObject(
                        "SELECT status FROM aegisflow.outbox_event WHERE event_id=?",String.class,entry.getValue())).isEqualTo("PUBLISHED");
            }
            assertThat(count(coreDb,heldEvent)).isZero();
            assertThat(orderDb.queryForObject("SELECT status FROM aegisflow.outbox_event WHERE event_id=?",String.class,heldEvent)).isEqualTo("PENDING");

            // Stop only the Core child process created above; broker/database/source schedulers stay running.
            apps.get("core").close();
            assertThat(apps.get("core").alive()).isFalse();
            orderDb.update("UPDATE aegisflow.outbox_event SET next_attempt_at=clock_timestamp() WHERE event_id=?",heldEvent);
            await(Duration.ofSeconds(30),() -> orderDb.queryForObject(
                    "SELECT status FROM aegisflow.outbox_event WHERE event_id=?",String.class,heldEvent).equals("PUBLISHED"));
            assertThat(count(coreDb,heldEvent)).isZero();
            var restarted=start("core",CORE,logs.resolve("core-restart.log"),group);
            owned.add(restarted); apps.put("core",restarted);
            await(Duration.ofSeconds(60),() -> restarted.healthy() && count(coreDb,heldEvent)==1);
            assertThat(coreDb.queryForObject("SELECT count(*) FROM aegisflow.inbox_message",Integer.class)).isEqualTo(4);
            assertThat(coreDb.queryForObject("SELECT count(*) FROM aegisflow.quarantine_record",Integer.class)).isZero();
            for(var entry:apps.entrySet()) assertThat(entry.getValue().healthy()).as(entry.getKey()+" packaged health").isTrue();
            // Runtime ingestion cannot inspect migration history; only the test's own migration identity can.
            assertThat(coreDb.queryForObject(
                    "SELECT has_table_privilege(current_user,'aegisflow.flyway_schema_history','SELECT')",
                    Boolean.class)).isFalse();
            var migrationDb=new JdbcTemplate(new DriverManagerDataSource(
                    CORE.getJdbcUrl(),"aegisflow_core_migrator",PipelineApplications.PASSWORD));
            assertThat(migrationDb.queryForObject(
                    "SELECT count(*) FROM aegisflow.flyway_schema_history WHERE success",Integer.class)).isEqualTo(4);
        } finally {
            Collections.reverse(owned);
            owned.forEach(PackagedApplicationProcess::close);
        }
    }
    static PackagedApplicationProcess start(String owner,PostgreSQLContainer db,Path log,String group) throws Exception {
        String upper=owner.toUpperCase(Locale.ROOT), prefix=upper+"_DB_";
        Map<String,String> env=new HashMap<>();
        env.put(prefix+"URL",db.getJdbcUrl()); env.put(prefix+"RUNTIME_USER","aegisflow_"+owner+"_runtime");
        env.put(prefix+"RUNTIME_PASSWORD",PipelineApplications.PASSWORD);
        env.put(prefix+"MIGRATION_USER","aegisflow_"+owner+"_migrator"); env.put(prefix+"MIGRATION_PASSWORD",PipelineApplications.PASSWORD);
        if(owner.equals("core")) {
            env.put("CORE_INBOX_ENABLED","true"); env.put("CORE_INBOX_GROUP",group); env.put("CORE_INBOX_CHANNEL","packaged-pipeline-it");
            env.put("CORE_KAFKA_BOOTSTRAP_SERVERS",KAFKA.bootstrap()); env.put("KAFKA_CORE_PASSWORD",KAFKA.password("core"));
            for(String source:List.of("ORDER","PAYMENT","WAREHOUSE")) env.put("CORE_INBOX_"+source+"_TENANTS",PipelineApplications.TENANT);
        } else {
            env.put(upper+"_OUTBOX_RELAY_ENABLED","true"); env.put(upper+"_OUTBOX_ALLOWED_TENANTS",PipelineApplications.TENANT);
            env.put(upper+"_KAFKA_BOOTSTRAP_SERVERS",KAFKA.bootstrap()); env.put("KAFKA_"+upper+"_PASSWORD",KAFKA.password(owner));
        }
        String module=owner.equals("core") ? "control-plane" : "simulators/"+owner+"-service";
        String artifact=owner.equals("core") ? "aegisflow-core" : owner+"-service";
        return PackagedApplicationProcess.start(PipelineApplications.root().resolve(module+"/target/"+artifact+"-0.1.0-SNAPSHOT.jar"),log,env);
    }
    static JdbcTemplate jdbc(String owner,PostgreSQLContainer db) {
        return new JdbcTemplate(new DriverManagerDataSource(db.getJdbcUrl(),"aegisflow_"+owner+"_runtime",PipelineApplications.PASSWORD));
    }
    static int count(JdbcTemplate jdbc,UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM aegisflow.inbox_message WHERE event_id=?",Integer.class,id);
    }
    @FunctionalInterface interface Condition { boolean ready() throws Exception; }
    static void await(Duration timeout,Condition condition) throws Exception {
        long deadline=System.nanoTime()+timeout.toNanos();
        while(System.nanoTime()<deadline) {
            if(condition.ready()) return;
            Thread.sleep(250);
        }
        throw new AssertionError("Packaged scheduled pipeline did not reach the required state; inspect retained .cache/packaged-pipeline logs");
    }
}
