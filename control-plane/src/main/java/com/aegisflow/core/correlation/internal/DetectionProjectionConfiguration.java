package com.aegisflow.core.correlation.internal;

import com.aegisflow.core.correlation.DetectionProjections;
import com.aegisflow.core.eventing.ProjectionInbox;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods=false)
@Profile("postgres")
class DetectionProjectionConfiguration {
    @Bean DetectionProjections detectionProjections(JdbcTemplate jdbc, ProjectionInbox inbox) {
        return (tenant,transaction,limit) -> {
            if(limit<1 || limit>100) throw new IllegalArgumentException("Bounded scan required");
            if(!inbox.tryLock()) throw new IllegalStateException("Projection gate unavailable");
            return jdbc.query("""
                SELECT * FROM aegisflow.business_transaction
                WHERE (tenant_id,business_transaction_id)>(?,?)
                ORDER BY tenant_id,business_transaction_id LIMIT ?
                """,(rs,n) -> {
                    String t=rs.getString("tenant_id"), tx=rs.getString("business_transaction_id");
                    var facts=jdbc.query("""
                        SELECT * FROM aegisflow.observed_fact WHERE tenant_id=? AND business_transaction_id=?
                        ORDER BY fact_id LIMIT 1001
                        """,(f,i) -> ProjectionModel.fact(f.getObject("fact_id",UUID.class),t,tx,
                            f.getString("source_system"),f.getObject("inbox_event_id",UUID.class),f.getString("fact_type"),
                            f.getTimestamp("effective_at").toInstant(),f.getTimestamp("observed_at").toInstant(),
                            f.getString("schema_version"),f.getString("canonical_value"),
                            f.getString("payload_hash"),f.getString("payload_hash_version")),t,tx);
                    if(facts.size()>1000) throw new IllegalStateException("Projection fact budget exceeded");
                    var reduced=ProjectionModel.reduce(facts);
                    var due=rs.getTimestamp("due_at"); var enforce=rs.getTimestamp("enforce_after");
                    return new DetectionProjections.Snapshot(t,tx,rs.getLong("projection_version"),
                        rs.getString("contract_id"),rs.getInt("contract_version"),rs.getString("configuration_version"),
                        rs.getInt("inventory_duration_seconds"),rs.getInt("lateness_seconds"),
                        due==null?null:due.toInstant(),enforce==null?null:enforce.toInstant(),
                        view(reduced.order().fact()),reduced.order().conflict(),
                        view(reduced.inventory().fact()),reduced.inventory().conflict(),
                        facts.stream().map(DetectionProjectionConfiguration::view).toList());
                },tenant,transaction,limit);
        };
    }
    private static DetectionProjections.Fact view(ProjectionModel.Fact f) {
        return f==null?null:new DetectionProjections.Fact(f.id(),f.source(),f.type(),f.entity(),f.version(),
            f.value().orderId(),f.value().items(),f.value().reason(),f.occurred(),f.received());
    }
}
