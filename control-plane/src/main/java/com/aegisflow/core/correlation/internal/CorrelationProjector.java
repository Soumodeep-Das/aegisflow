package com.aegisflow.core.correlation.internal;

import com.aegisflow.core.eventing.ProjectionInbox;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import static com.aegisflow.core.correlation.internal.ProjectionModel.*;

/**
 * Each input has one local transaction for fact, projection/deadline and completion.
 * No Kafka call or remote effect occurs while holding the local coordination lock.
 */
final class CorrelationProjector {
    static final int FACT_LIMIT=1000;
    private final ProjectionInbox inbox;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final Contract contract;
    private final int batchSize;

    CorrelationProjector(ProjectionInbox inbox,JdbcTemplate jdbc,PlatformTransactionManager manager,
                        Clock clock,Contract contract,int batchSize) {
        if(batchSize<1 || batchSize>100) throw new IllegalArgumentException("Batch must be1..100");
        this.inbox=inbox; this.jdbc=jdbc; this.clock=clock; this.contract=contract; this.batchSize=batchSize;
        transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(5);
    }
    int poll() {
        int count=0;
        for(int n=0;n<batchSize;n++) {
            if(!processNext()) break;
            count++;
        }
        return count;
    }
    private boolean processNext() {
        ProjectionInbox.Key[] selected=new ProjectionInbox.Key[1];
        try {
            return Boolean.TRUE.equals(transaction.execute(status -> {
                if(!inbox.tryLock()) return false;
                var pending=inbox.pending(1);
                if(pending.isEmpty()) return false;
                var event=pending.getFirst();
                selected[0]=event.key();
                Fact fact;
                try { fact=ProjectionModel.from(event,UUID.randomUUID()); }
                catch(IllegalArgumentException invalid) { throw new Blocked("ACCEPTED_SHAPE_UNSUPPORTED"); }
                Instant now=clock.instant();
                insertFact(fact,now);
                var facts=readFacts(event.key().tenant(),event.key().transaction());
                if(facts.size()>FACT_LIMIT) throw new Blocked("TRANSACTION_FACT_LIMIT");
                updateProjection(event.key(),facts,now);
                inbox.applied(event.key(),now);
                return true;
            }));
        } catch(Blocked permanent) {
            return blockAfterRollback(selected[0],permanent.getMessage());
        } catch(DataIntegrityViolationException constraint) {
            // The effect transaction has already rolled back. Retain visible blocked work instead of looping forever.
            if(selected[0]==null) throw constraint;
            return blockAfterRollback(selected[0],"PERSISTENCE_CONSTRAINT");
        }
    }
    private boolean blockAfterRollback(ProjectionInbox.Key key,String reason) {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            if(!inbox.tryLock()) return false;
            var pending=inbox.pending(1);
            // Another worker may already have processed it, or a newly visible earlier row may now sort first.
            if(pending.isEmpty() || !pending.getFirst().key().equals(key)) return false;
            inbox.blocked(key,reason,clock.instant());
            return true;
        }));
    }
    private void insertFact(Fact fact,Instant now) {
        jdbc.update("""
            INSERT INTO aegisflow.observed_fact
            (tenant_id,fact_id,business_transaction_id,source_system,inbox_event_id,fact_type,source_entity_id,
             source_entity_version,order_id,effective_at,observed_at,projected_at,schema_version,
             canonical_value,payload_hash,payload_hash_version)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?)
            """,fact.tenant(),fact.id(),fact.transaction(),fact.source(),fact.eventId(),fact.type(),fact.entity(),
                fact.version(),fact.value().orderId(),Timestamp.from(fact.occurred()),Timestamp.from(fact.received()),
                Timestamp.from(now),fact.schema(),fact.details(),fact.hash(),fact.hashVersion());
    }
    private List<Fact> readFacts(String tenant,String businessTransaction) {
        return jdbc.query("""
            SELECT * FROM aegisflow.observed_fact WHERE tenant_id=? AND business_transaction_id=?
            ORDER BY fact_id LIMIT ?
            """,(rs,n) -> ProjectionModel.fact(rs.getObject("fact_id",UUID.class),tenant,businessTransaction,
                rs.getString("source_system"),rs.getObject("inbox_event_id",UUID.class),rs.getString("fact_type"),
                rs.getTimestamp("effective_at").toInstant(),rs.getTimestamp("observed_at").toInstant(),
                rs.getString("schema_version"),rs.getString("canonical_value"),rs.getString("payload_hash"),
                rs.getString("payload_hash_version")),tenant,businessTransaction,FACT_LIMIT+1);
    }
    private void updateProjection(ProjectionInbox.Key key,List<Fact> facts,Instant now) {
        var persisted=jdbc.query("""
            SELECT configuration_version,inventory_duration_seconds,lateness_seconds FROM aegisflow.business_transaction
            WHERE tenant_id=? AND business_transaction_id=?
            """,(rs,n) -> new Contract(rs.getString(1),rs.getInt(2),rs.getInt(3)),key.tenant(),key.transaction());
        Contract pinned=persisted.isEmpty() ? contract : persisted.getFirst();
        var snapshot=ProjectionModel.reduce(facts);
        var order=snapshot.order().fact();
        // An ambiguous OrderCreated cannot establish a trustworthy deadline. B must report UNKNOWN, not absence.
        Instant occurrence=order==null || snapshot.order().conflict() ? null : order.occurred();
        Instant first=facts.stream().map(Fact::received).min(Comparator.naturalOrder()).orElseThrow();
        Instant last=facts.stream().map(Fact::received).max(Comparator.naturalOrder()).orElseThrow();
        jdbc.update("""
            INSERT INTO aegisflow.business_transaction
            (tenant_id,business_transaction_id,contract_id,contract_version,configuration_version,
             inventory_duration_seconds,lateness_seconds,order_fact_id,payment_fact_id,inventory_fact_id,
             derived_status,projection_version,first_observed_at,last_observed_at,processed_at,due_at,enforce_after)
            VALUES (?,?,'ECOM_ORDER_V1',1,?,?,?,?,?,?,?,1,?,?,?,?,?)
            ON CONFLICT (tenant_id,business_transaction_id) DO UPDATE SET
             order_fact_id=EXCLUDED.order_fact_id,payment_fact_id=EXCLUDED.payment_fact_id,
             inventory_fact_id=EXCLUDED.inventory_fact_id,derived_status=EXCLUDED.derived_status,
             projection_version=aegisflow.business_transaction.projection_version+1,
             first_observed_at=EXCLUDED.first_observed_at,last_observed_at=EXCLUDED.last_observed_at,
             processed_at=EXCLUDED.processed_at,due_at=EXCLUDED.due_at,enforce_after=EXCLUDED.enforce_after
            """,key.tenant(),key.transaction(),pinned.configurationVersion(),pinned.inventorySeconds(),pinned.latenessSeconds(),
                id(snapshot.order()),id(snapshot.payment()),id(snapshot.inventory()),snapshot.status(),
                Timestamp.from(first),Timestamp.from(last),Timestamp.from(now),
                occurrence==null ? null : Timestamp.from(pinned.due(occurrence)),
                occurrence==null ? null : Timestamp.from(pinned.enforcement(occurrence)));
    }
    private static UUID id(Effective value) { return value.fact()==null ? null : value.fact().id(); }
    private static final class Blocked extends RuntimeException { Blocked(String reason) { super(reason); } }
}
