package com.aegisflow.core.correlation.internal;

import static org.assertj.core.api.Assertions.*;
import com.aegisflow.core.eventing.ProjectionInbox;
import com.aegisflow.core.eventing.internal.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

/** P0.4-A gate: real SQL, production intake and projector; no broker is needed to prove durable catch-up. */
@Testcontainers(disabledWithoutDocker=true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(60)
class DurableProjectionIT {
    static final String RUNTIME="aegisflow_core_runtime", MIGRATOR="aegisflow_core_migrator";
    static final String PASSWORD="synthetic-projection-only";
    static final Instant WHEN=ProjectionModelTests.WHEN;
    static final Clock CLOCK=Clock.fixed(WHEN.plusSeconds(300),ZoneOffset.UTC);
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer(DockerImageName.parse(
            "postgres:18.6@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336")
            .asCompatibleSubstituteFor("postgres")).withDatabaseName("projection_it")
            .withUsername("inspection_admin").withPassword(PASSWORD);
    static final AtomicLong OFFSET=new AtomicLong();
    static final JsonMapper JSON=new JsonMapper();
    static JdbcTemplate jdbc,admin;
    static DataSourceTransactionManager manager;
    static InboxStore intake;
    static EventValidator validator;
    static ProjectionInbox inbox;
    static AnnotationConfigApplicationContext context;
    static UUID legacy;
    static Flyway flyway(String target) {
        var configuration=Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),MIGRATOR,PASSWORD)
                .schemas("aegisflow").defaultSchema("aegisflow").createSchemas(false)
                .locations("classpath:db/migration").placeholders(Map.of("runtimeRole",RUNTIME));
        if(target!=null) configuration.target(target);
        return configuration.load();
    }
    @BeforeAll static void start() {
        admin=new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()));
        admin.execute("CREATE ROLE "+MIGRATOR+" LOGIN PASSWORD '"+PASSWORD+"'");
        admin.execute("CREATE ROLE "+RUNTIME+" LOGIN PASSWORD '"+PASSWORD+"'");
        admin.execute("REVOKE ALL ON DATABASE projection_it FROM PUBLIC");
        admin.execute("GRANT CONNECT ON DATABASE projection_it TO "+MIGRATOR+","+RUNTIME);
        admin.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC");
        admin.execute("CREATE SCHEMA aegisflow AUTHORIZATION "+MIGRATOR);
        flyway("2").migrate();
        var dataSource=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),RUNTIME,PASSWORD);
        jdbc=new JdbcTemplate(dataSource); manager=new DataSourceTransactionManager(dataSource);
        validator=new EventValidator(new IntakePolicy(Map.of(
                "order-service",Set.of("tenant-a","tenant-b"),"payment-service",Set.of("tenant-a","tenant-b"),
                "warehouse-service",Set.of("tenant-a","tenant-b"))));
        intake=new InboxStore(jdbc,manager,validator,"projection-it");
        legacy=UUID.randomUUID();
        assertThat(intake.accept(event("tenant-a","legacy","OrderCreated","order",1,legacy))).isEqualTo("ACCEPTED");
        flyway(null).migrate();
        context=new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("postgres");
        context.registerBean(JdbcTemplate.class,() -> jdbc);
        context.scan("com.aegisflow.core.eventing.internal");
        context.refresh();
        inbox=context.getBean(ProjectionInbox.class);
    }
    @AfterAll static void close() { if(context!=null) context.close(); }
    @BeforeEach void clear(TestInfo info) {
        if(!info.getTestMethod().orElseThrow().getName().equals("backfillsAnInboxCommittedBeforeV3")) {
            // Only this test's fresh disposable database, never an ordinary Compose volume.
            admin.execute("TRUNCATE aegisflow.invariant_evaluation,aegisflow.business_transaction,aegisflow.observed_fact,"
                    +"aegisflow.inbox_projection_progress,aegisflow.inbox_message,aegisflow.quarantine_record");
        }
    }
    static CorrelationProjector projector() { return projector(inbox,new ProjectionModel.Contract("demo-v1",45,15)); }
    static CorrelationProjector projector(ProjectionInbox port,ProjectionModel.Contract contract) {
        return new CorrelationProjector(port,jdbc,manager,CLOCK,contract,25);
    }
    static ConsumerRecord<byte[],byte[]> event(String tenant,String transaction,String type,String order,long version,UUID id) {
        String source=type.equals("OrderCreated") ? "order" : type.startsWith("Payment") ? "payment" : "warehouse";
        var details=new HashMap<String,Object>(); details.put("orderId",order);
        if(source.equals("order")) {
            details.put("orderVersion",version); details.put("lineItems",List.of(Map.of("sku","sku-1","quantity",2)));
        } else if(source.equals("payment")) {
            details.put("paymentVersion",version); details.put("paymentId","pay-"+order);
            if(type.equals("PaymentDeclined")) details.put("reasonCode","SIMULATED_DECLINE");
        } else {
            details.put("reservationVersion",version); details.put("reservationId","reservation-"+order);
            if(type.equals("InventoryReserved")) details.put("items",List.of(Map.of("sku","sku-1","quantity",2)));
            else details.put("reasonCode","OUT_OF_STOCK");
        }
        if(!source.equals("warehouse")) { details.put("amount","12.00"); details.put("currency","INR"); }
        byte[] payload=JSON.writeValueAsBytes(Map.of("tenantId",tenant,"businessTransactionId",transaction,
                "eventId",id.toString(),"eventType",type,"sourceSystem",source+"-service","schemaVersion","1.0",
                "occurredAt",WHEN.toString(),"correlationId","trace-only","details",details));
        return new ConsumerRecord<>("aegisflow."+source+".events.v1",0,OFFSET.incrementAndGet(),
                (tenant+":"+transaction).getBytes(StandardCharsets.UTF_8),payload);
    }
    static UUID accept(String tenant,String transaction,String type,String order,long version) {
        UUID id=UUID.randomUUID(); assertThat(intake.accept(event(tenant,transaction,type,order,version,id))).isEqualTo("ACCEPTED"); return id;
    }
    static int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM aegisflow."+table,Integer.class); }
    static String status(String tenant,String transaction) {
        return jdbc.queryForObject("SELECT derived_status FROM aegisflow.business_transaction WHERE tenant_id=? AND business_transaction_id=?",
                String.class,tenant,transaction);
    }
    static boolean unsettled(String tenant,String transaction) {
        return Boolean.TRUE.equals(new TransactionTemplate(manager).execute(s -> inbox.hasUnsettled(tenant,transaction)));
    }
    @Test @Order(1) void backfillsAnInboxCommittedBeforeV3() {
        assertThat(flyway(null).info().applied()).hasSize(4);
        assertThat(projector().poll()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT inbox_event_id FROM aegisflow.observed_fact",UUID.class)).isEqualTo(legacy);
        assertThat(jdbc.queryForObject("SELECT status FROM aegisflow.inbox_message",String.class)).isEqualTo("ACCEPTED");
        assertThat(status("tenant-a","legacy")).isEqualTo("PENDING_INVENTORY");
        assertThat(unsettled("tenant-a","legacy")).isFalse();
    }
    @Test void duplicatesAndWorkerRestartDoNotRepeatEffects() {
        var record=event("tenant-a","txn","OrderCreated","order",1,UUID.randomUUID());
        assertThat(intake.accept(record)).isEqualTo("ACCEPTED");
        assertThat(projector().poll()).isEqualTo(1);
        assertThat(intake.accept(record)).isEqualTo("DUPLICATE");
        assertThat(projector().poll()).isZero();
        assertThat(count("observed_fact")).isEqualTo(1);
        assertThat(count("inbox_projection_progress")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT projection_version FROM aegisflow.business_transaction",Long.class)).isEqualTo(1L);
    }
    @Test void outOfOrderOwnersJoinWithoutUsingCorrelationId() {
        accept("tenant-a","txn","InventoryReserved","order",1); projector().poll();
        assertThat(status("tenant-a","txn")).isEqualTo("AWAITING_ORDER");
        accept("tenant-a","txn","PaymentAuthorized","order",1);
        accept("tenant-a","txn","OrderCreated","order",1); projector().poll();
        assertThat(status("tenant-a","txn")).isEqualTo("INVENTORY_OBSERVED");
        assertThat(count("observed_fact")).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT enforce_after FROM aegisflow.business_transaction",Timestamp.class).toInstant())
                .isEqualTo(WHEN.plusSeconds(60));
    }
    @Test void rollbackAfterCompletionInsertLeavesNoPartialEffectAndRetries() {
        accept("tenant-a","txn","OrderCreated","order",1);
        ProjectionInbox fail=new ForwardingInbox(inbox) {
            @Override public void applied(Key key,Instant at) { super.applied(key,at); throw new IllegalStateException("injected crash before commit"); }
        };
        assertThatThrownBy(() -> projector(fail,new ProjectionModel.Contract("demo-v1",45,15)).poll())
                .isInstanceOf(IllegalStateException.class);
        assertThat(count("inbox_message")).isEqualTo(1);
        assertThat(count("observed_fact")).isZero();
        assertThat(count("business_transaction")).isZero();
        assertThat(count("inbox_projection_progress")).isZero();
        assertThat(unsettled("tenant-a","txn")).isTrue();
        assertThat(projector().poll()).isEqualTo(1);
    }
    @Test void concurrentProjectorsAndTransactionGateDoNotDuplicateFacts() throws Exception {
        accept("tenant-a","txn","OrderCreated","order",1);
        accept("tenant-a","txn","InventoryReserved","order",1);
        try(var workers=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Integer> task=() -> { start.await(); return projector().poll(); };
            var one=workers.submit(task); var two=workers.submit(task); start.countDown();
            one.get(20,TimeUnit.SECONDS); two.get(20,TimeUnit.SECONDS);
        }
        projector().poll();
        assertThat(count("observed_fact")).isEqualTo(2);
        assertThat(count("inbox_projection_progress")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT projection_version FROM aegisflow.business_transaction",Long.class)).isEqualTo(2L);
    }
    @Test void sameTransactionInTwoTenantsHasIndependentFactsAndForeignKeys() {
        accept("tenant-a","same","OrderCreated","order",1);
        accept("tenant-b","same","InventoryRejected","order",1); projector().poll();
        assertThat(count("business_transaction")).isEqualTo(2);
        assertThat(status("tenant-a","same")).isEqualTo("PENDING_INVENTORY");
        assertThat(status("tenant-b","same")).isEqualTo("AWAITING_ORDER");
        UUID other=jdbc.queryForObject("SELECT fact_id FROM aegisflow.observed_fact WHERE tenant_id='tenant-b'",UUID.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE aegisflow.business_transaction SET inventory_fact_id=? WHERE tenant_id='tenant-a'",other))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void olderVersionsAndSameVersionContradictionsAreRetained() {
        accept("tenant-a","txn","InventoryReserved","order",2); projector().poll();
        accept("tenant-a","txn","InventoryReserved","order",1); projector().poll();
        assertThat(jdbc.queryForObject("""
                SELECT f.source_entity_version FROM aegisflow.business_transaction p JOIN aegisflow.observed_fact f
                ON (p.tenant_id,p.inventory_fact_id)=(f.tenant_id,f.fact_id)
                """,Long.class)).isEqualTo(2L);
        accept("tenant-a","txn","InventoryRejected","order",2); projector().poll();
        assertThat(status("tenant-a","txn")).isEqualTo("CONFLICT");
        assertThat(count("observed_fact")).isEqualTo(3);
    }
    @Test void existingTransactionKeepsItsContractAndClockMetadata() {
        accept("tenant-a","txn","PaymentAuthorized","order",1); projector().poll();
        accept("tenant-a","txn","OrderCreated","order",1);
        projector(inbox,new ProjectionModel.Contract("next-config",90,30)).poll();
        assertThat(jdbc.queryForObject("SELECT configuration_version FROM aegisflow.business_transaction",String.class)).isEqualTo("demo-v1");
        assertThat(jdbc.queryForObject("SELECT processed_at FROM aegisflow.business_transaction",Timestamp.class).toInstant()).isEqualTo(CLOCK.instant());
        assertThat(jdbc.queryForObject("SELECT due_at FROM aegisflow.business_transaction",Timestamp.class).toInstant()).isEqualTo(WHEN.plusSeconds(45));
        assertThat(jdbc.queryForObject("SELECT enforce_after FROM aegisflow.business_transaction",Timestamp.class).toInstant()).isEqualTo(WHEN.plusSeconds(60));
    }
    @Test void immutableEvidenceAndIntakeQuarantineRemainProtected() {
        var invalid=event("untrusted","bad","OrderCreated","order",1,UUID.randomUUID());
        assertThat(intake.accept(invalid)).isEqualTo("QUARANTINED");
        assertThat(projector().poll()).isZero();
        accept("tenant-a","txn","OrderCreated","order",1); projector().poll();
        assertThat(count("quarantine_record")).isEqualTo(1);
        for(String table:List.of("inbox_message","observed_fact","inbox_projection_progress")) {
            assertThatThrownBy(() -> jdbc.execute("DELETE FROM aegisflow."+table)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
        assertThatThrownBy(() -> jdbc.execute("UPDATE aegisflow.observed_fact SET payload_hash=repeat('0',64)"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.execute("UPDATE aegisflow.business_transaction SET configuration_version='rewrite'"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void permanentShapeFailureIsDurableAndPreventsFreshness() {
        accept("tenant-a","txn","OrderCreated","order",1);
        ProjectionInbox badShape=new ForwardingInbox(inbox) {
            @Override public List<AcceptedEvent> pending(int limit) {
                return super.pending(limit).stream().map(e -> new AcceptedEvent(e.key(),"FutureEvent",e.schema(),
                        e.occurred(),e.received(),e.payload(),e.hash(),e.hashVersion())).toList();
            }
        };
        assertThat(projector(badShape,new ProjectionModel.Contract("demo-v1",45,15)).poll()).isEqualTo(1);
        assertThat(count("observed_fact")).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM aegisflow.inbox_projection_progress",String.class)).isEqualTo("BLOCKED");
        assertThat(unsettled("tenant-a","txn")).isTrue();
        assertThat(projector().poll()).isZero();
    }
    @Test void lateVisibleEarlierInboxRowCannotBeSkippedByCursor() throws Exception {
        var record=event("tenant-a","early","OrderCreated","early-order",1,UUID.randomUUID());
        var value=validator.validate(record);
        try(var connection=DriverManager.getConnection(POSTGRES.getJdbcUrl(),RUNTIME,PASSWORD)) {
            connection.setAutoCommit(false);
            try(var statement=connection.prepareStatement("""
                INSERT INTO aegisflow.inbox_message
                (tenant_id,source_system,event_id,event_type,business_transaction_id,schema_version,occurred_at,
                 correlation_id,channel_identity,topic,partition_id,offset_id,payload,payload_hash,payload_hash_version,raw_payload_hash,status)
                VALUES (?,?,?,?,?,?,?,?,?,?,0,?,?::jsonb,?,'aegisflow-inbox-json-v1',?,'ACCEPTED')
                """)) {
                statement.setString(1,value.tenant()); statement.setString(2,value.source()); statement.setObject(3,value.id());
                statement.setString(4,value.type()); statement.setString(5,value.transaction()); statement.setString(6,value.schema());
                statement.setTimestamp(7,Timestamp.from(value.occurred())); statement.setString(8,value.correlation());
                statement.setString(9,"projection-it"); statement.setString(10,record.topic()); statement.setLong(11,record.offset());
                statement.setString(12,value.envelope()); statement.setString(13,value.hash()); statement.setString(14,value.hash());
                statement.executeUpdate();
            }
            accept("tenant-a","later","OrderCreated","later-order",1);
            assertThat(projector().poll()).isEqualTo(1);
            connection.commit();
        }
        assertThat(projector().poll()).isEqualTo(1);
        assertThat(count("observed_fact")).isEqualTo(2);
        assertThat(unsettled("tenant-a","early")).isFalse();
    }
    @Test void readAndCompletionPortRequiresAnActualTransaction() {
        assertThatThrownBy(() -> inbox.pending(1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> inbox.hasUnsettled("tenant-a","txn")).isInstanceOf(IllegalStateException.class);
    }
    static class ForwardingInbox implements ProjectionInbox {
        final ProjectionInbox delegate;
        ForwardingInbox(ProjectionInbox delegate) { this.delegate=delegate; }
        public boolean tryLock() { return delegate.tryLock(); }
        public List<AcceptedEvent> pending(int limit) { return delegate.pending(limit); }
        public void applied(Key key,Instant at) { delegate.applied(key,at); }
        public void blocked(Key key,String reason,Instant at) { delegate.blocked(key,reason,at); }
        public boolean hasUnsettled(String tenant,String transaction) { return delegate.hasUnsettled(tenant,transaction); }
    }
}
