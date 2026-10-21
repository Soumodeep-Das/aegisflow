package com.aegisflow.core.correlation.internal;

import com.aegisflow.core.correlation.DetectionProjections;
import com.aegisflow.core.eventing.ProjectionInbox;
import java.time.Instant;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Reuses A's production-intake/projector fixture without rerunning its test methods. */
public final class DetectionFixture implements AutoCloseable {
    private AnnotationConfigApplicationContext context;
    public static PostgreSQLContainer container() { return DurableProjectionIT.POSTGRES; }
    public static Instant startTime() { return DurableProjectionIT.WHEN; }
    public void start() {
        DurableProjectionIT.start();
        context=new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("postgres");
        context.registerBean(JdbcTemplate.class,DetectionFixture::jdbc);
        context.registerBean(ProjectionInbox.class,DetectionFixture::inbox);
        context.register(DetectionProjectionConfiguration.class);
        context.refresh();
    }
    public void clear() {
        DurableProjectionIT.admin.execute("TRUNCATE aegisflow.incident_fact_reference,aegisflow.incident_timeline,aegisflow.incident,"
            +"aegisflow.invariant_evaluation,aegisflow.business_transaction,"
            +"aegisflow.observed_fact,aegisflow.inbox_projection_progress,aegisflow.inbox_message,aegisflow.quarantine_record");
        DurableProjectionIT.admin.update("UPDATE aegisflow.detection_sweep SET tenant_cursor='',transaction_cursor=''");
    }
    public static JdbcTemplate jdbc() { return DurableProjectionIT.jdbc; }
    public static JdbcTemplate admin() { return DurableProjectionIT.admin; }
    public static PlatformTransactionManager manager() { return DurableProjectionIT.manager; }
    public static ProjectionInbox inbox() { return DurableProjectionIT.inbox; }
    public DetectionProjections projections() { return context.getBean(DetectionProjections.class); }
    public void accept(String tenant,String tx,String type,String order) {
        DurableProjectionIT.accept(tenant,tx,type,order,1);
    }
    public void project() { DurableProjectionIT.projector().poll(); }
    @Override public void close() { if(context!=null) context.close(); DurableProjectionIT.close(); }
}
