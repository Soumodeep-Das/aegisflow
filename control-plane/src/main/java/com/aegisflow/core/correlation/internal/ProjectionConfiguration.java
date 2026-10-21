package com.aegisflow.core.correlation.internal;

import com.aegisflow.core.eventing.ProjectionInbox;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods=false)
@Profile("postgres")
@ConditionalOnProperty(name="aegisflow.core.projection.enabled",havingValue="true")
@EnableScheduling
class ProjectionConfiguration {
    @Bean Clock projectionClock() { return Clock.systemUTC(); }
    @Bean CorrelationProjector correlationProjector(ProjectionInbox inbox,JdbcTemplate jdbc,
            PlatformTransactionManager manager,@Qualifier("projectionClock") Clock projectionClock,Environment env) {
        return new CorrelationProjector(inbox,jdbc,manager,projectionClock,new ProjectionModel.Contract(
                env.getRequiredProperty("aegisflow.core.projection.configuration-version"),
                env.getRequiredProperty("aegisflow.core.projection.inventory-seconds",Integer.class),
                env.getRequiredProperty("aegisflow.core.projection.lateness-seconds",Integer.class)),
                env.getRequiredProperty("aegisflow.core.projection.batch-size",Integer.class));
    }
    @Bean ProjectionTick projectionTick(CorrelationProjector projector) { return new ProjectionTick(projector); }
    static final class ProjectionTick {
        private final CorrelationProjector projector;
        ProjectionTick(CorrelationProjector projector) { this.projector=projector; }
        @Scheduled(fixedDelay=5000) public void tick() {
            try { projector.poll(); }
            catch(RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(ProjectionTick.class).warn(
                        "Projection pass rolled back; durable pending work retained; inspect Core database health");
            }
        }
    }
}
