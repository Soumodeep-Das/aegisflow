package com.aegisflow.core.detection.internal;

import com.aegisflow.core.correlation.DetectionProjections;
import com.aegisflow.core.eventing.ProjectionInbox;
import java.time.Clock;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Recreate workers against durable state on every call; clocks advance without sleeping. */
public final class PipelineDetectionDriver {
    public static void evaluate(ConfigurableApplicationContext app,Clock clock) {
        var worker=new DeadlineDetector(app.getBean(DetectionProjections.class),app.getBean(ProjectionInbox.class),
                app.getBean(JdbcTemplate.class),app.getBean(PlatformTransactionManager.class),clock,100);
        // One wrap plus all rows; this fixture has fewer than 100 transactions.
        worker.poll(); worker.poll(); worker.poll();
    }
    public static void record(ConfigurableApplicationContext app,Clock clock) {
        var worker=new IncidentRecorder(app.getBean(JdbcTemplate.class),app.getBean(PlatformTransactionManager.class),clock,100);
        for(int n=0;n<10;n++) if(worker.poll()==0) return;
        throw new AssertionError("Incident work did not drain within bounded passes");
    }
}
