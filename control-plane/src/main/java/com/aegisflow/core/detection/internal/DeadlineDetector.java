package com.aegisflow.core.detection.internal;

import com.aegisflow.core.correlation.DetectionProjections;
import com.aegisflow.core.eventing.ProjectionInbox;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Durable cyclic sweep: no timestamp high-water mark, no in-memory-only deadline queue. */
final class DeadlineDetector {
    private final DetectionProjections projections;
    private final ProjectionInbox inbox;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final int batch;
    private final TransactionTemplate tx;
    private final JsonMapper json=new JsonMapper();
    DeadlineDetector(DetectionProjections projections,ProjectionInbox inbox,JdbcTemplate jdbc,
                     PlatformTransactionManager manager,Clock clock,int batch) {
        if(batch<1 || batch>100) throw new IllegalArgumentException("Batch must be 1..100");
        this.projections=projections; this.inbox=inbox; this.jdbc=jdbc; this.clock=clock; this.batch=batch;
        tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.setTimeout(5);
    }
    int poll() {
        Integer count=tx.execute(status -> {
            if(!inbox.tryLock()) return 0;
            var cursor=jdbc.queryForMap("SELECT tenant_cursor,transaction_cursor FROM aegisflow.detection_sweep WHERE singleton=1");
            var rows=projections.scanAfter((String)cursor.get("tenant_cursor"),(String)cursor.get("transaction_cursor"),batch);
            if(rows.isEmpty()) {
                jdbc.update("UPDATE aegisflow.detection_sweep SET tenant_cursor='',transaction_cursor='' WHERE singleton=1");
                return 0;
            }
            var now=clock.instant();
            int inserted=0;
            for(var s:rows) {
                boolean unsettled=inbox.hasUnsettled(s.tenant(),s.transaction());
                var decision=InventoryInvariant.evaluate(s,unsettled,now);
                inserted+=jdbc.update("""
                    INSERT INTO aegisflow.invariant_evaluation
                    (tenant_id,evaluation_id,business_transaction_id,invariant_id,invariant_version,
                     projection_version,phase,unsettled,result,reason_code,evaluated_at,
                     contract_id,contract_version,configuration_version,inventory_duration_seconds,lateness_seconds,
                     due_at,enforce_after,observation)
                    VALUES (?,?,?,'INV-002',1,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb)
                    ON CONFLICT (tenant_id,business_transaction_id,invariant_id,invariant_version,projection_version,phase,unsettled)
                    DO NOTHING
                    """,s.tenant(),UUID.randomUUID(),s.transaction(),s.version(),decision.phase(),unsettled,
                    decision.result(),decision.reason(),Timestamp.from(now),s.contract(),s.contractVersion(),
                    s.configuration(),s.duration(),s.lateness(),s.due()==null?null:Timestamp.from(s.due()),
                    s.enforceAfter()==null?null:Timestamp.from(s.enforceAfter()),json.writeValueAsString(s));
            }
            var last=rows.getLast();
            jdbc.update("UPDATE aegisflow.detection_sweep SET tenant_cursor=?,transaction_cursor=? WHERE singleton=1",
                last.tenant(),last.transaction());
            return inserted;
        });
        return count==null?0:count;
    }
}
