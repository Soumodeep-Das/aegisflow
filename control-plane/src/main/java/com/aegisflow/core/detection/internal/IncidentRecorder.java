package com.aegisflow.core.detection.internal;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Each timeline entry is also its durable completion marker; never depends on callback memory. */
final class IncidentRecorder {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final int batch;
    private final TransactionTemplate tx;
    private final JsonMapper json=new JsonMapper();
    IncidentRecorder(JdbcTemplate jdbc,PlatformTransactionManager manager,Clock clock,int batch) {
        if(batch<1 || batch>100) throw new IllegalArgumentException("Batch must be 1..100");
        this.jdbc=jdbc; this.clock=clock; this.batch=batch;
        tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        tx.setTimeout(5);
    }
    int poll() {
        int completed=0;
        for(int n=0;n<batch;n++) {
            if(!Boolean.TRUE.equals(tx.execute(s -> next()))) break;
            completed++;
        }
        return completed;
    }
    private boolean next() {
        // Separate from the projector gate: only immutable evaluation snapshots are read here.
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(104001,2)",Boolean.class))) return false;
        var candidates=jdbc.queryForList("""
            SELECT e.*, i.incident_id AS existing_id FROM aegisflow.invariant_evaluation e
            LEFT JOIN aegisflow.incident i ON
                (i.tenant_id,i.business_transaction_id,i.invariant_id,i.invariant_version,
                 i.contract_id,i.contract_version,i.configuration_version)=
                (e.tenant_id,e.business_transaction_id,e.invariant_id,e.invariant_version,
                 e.contract_id,e.contract_version,e.configuration_version)
            WHERE (e.result='VIOLATED' OR i.incident_id IS NOT NULL)
              AND NOT EXISTS (SELECT 1 FROM aegisflow.incident_timeline t
                WHERE t.tenant_id=e.tenant_id AND t.incident_id=i.incident_id AND t.evaluation_id=e.evaluation_id)
            ORDER BY e.evaluated_at,e.tenant_id,e.business_transaction_id,e.evaluation_id LIMIT 1
            """);
        if(candidates.isEmpty()) return false;
        var e=candidates.getFirst();
        String tenant=(String)e.get("tenant_id"), transaction=(String)e.get("business_transaction_id");
        UUID evaluation=(UUID)e.get("evaluation_id"), incident=(UUID)e.get("existing_id");
        String result=(String)e.get("result");
        boolean created=incident==null;
        var now=Timestamp.from(clock.instant());
        if(created) {
            if(!IncidentPolicy.opens(result)) throw new IllegalStateException("Only violations open incidents");
            incident=UUID.randomUUID();
            jdbc.update("""
                INSERT INTO aegisflow.incident
                (tenant_id,incident_id,business_transaction_id,invariant_id,invariant_version,contract_id,
                 contract_version,configuration_version,episode_key,severity,reason_code,state,state_version,
                 incident_revision,opened_at,updated_at,initial_evaluation_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,'DETECTED',1,1,?,?,?)
                """,tenant,incident,transaction,e.get("invariant_id"),e.get("invariant_version"),e.get("contract_id"),
                e.get("contract_version"),e.get("configuration_version"),
                IncidentPolicy.episode((String)e.get("invariant_id"),((Number)e.get("invariant_version")).intValue(),
                    (String)e.get("contract_id"),((Number)e.get("contract_version")).intValue(),(String)e.get("configuration_version")),
                IncidentPolicy.severity((String)e.get("reason_code")),e.get("reason_code"),now,now,evaluation);
        }
        jdbc.update("""
            INSERT INTO aegisflow.incident_timeline
            (tenant_id,business_transaction_id,incident_id,evaluation_id,entry_kind,recorded_at) VALUES (?,?,?,?,?,?)
            """,tenant,transaction,incident,evaluation,IncidentPolicy.kind(created,result),now);
        var facts=json.readTree(e.get("observation").toString()).get("facts");
        if(facts==null || !facts.isArray() || facts.size()>1000) throw new IllegalStateException("Invalid evaluation provenance");
        for(var fact:facts) {
            jdbc.update("""
                INSERT INTO aegisflow.incident_fact_reference
                (tenant_id,business_transaction_id,incident_id,evaluation_id,fact_id) VALUES (?,?,?,?,?)
                """,tenant,transaction,incident,evaluation,UUID.fromString(fact.get("id").stringValue()));
        }
        if(!created) jdbc.update("""
            UPDATE aegisflow.incident SET incident_revision=incident_revision+1,updated_at=greatest(updated_at,?)
            WHERE tenant_id=? AND incident_id=?
            """,now,tenant,incident);
        return true;
    }
}
