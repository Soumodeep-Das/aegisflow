package com.aegisflow.core.correlation.internal;

import com.aegisflow.core.eventing.ProjectionInbox;
import java.time.Clock;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Test-only access to the production worker; not packaged scheduler proof. */
public final class PipelineProjectionDriver {
    public static void drain(ConfigurableApplicationContext app, Clock clock) {
        var worker=new CorrelationProjector(app.getBean(ProjectionInbox.class),app.getBean(JdbcTemplate.class),
                app.getBean(PlatformTransactionManager.class),clock,new ProjectionModel.Contract("demo-v1",45,15),100);
        for(int n=0;n<10;n++) if(worker.poll()==0) return;
        throw new AssertionError("Projection did not drain within bounded passes");
    }
}
