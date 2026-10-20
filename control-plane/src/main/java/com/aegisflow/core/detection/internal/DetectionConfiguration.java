package com.aegisflow.core.detection.internal;

import com.aegisflow.core.correlation.DetectionProjections;
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
@ConditionalOnProperty(name="aegisflow.core.detection.enabled",havingValue="true")
@EnableScheduling
class DetectionConfiguration {
    @Bean Clock detectionClock() { return Clock.systemUTC(); }
    @Bean DeadlineDetector deadlineDetector(DetectionProjections projections,ProjectionInbox inbox,JdbcTemplate jdbc,
            PlatformTransactionManager manager,@Qualifier("detectionClock") Clock clock,Environment env) {
        return new DeadlineDetector(projections,inbox,jdbc,manager,clock,
            env.getRequiredProperty("aegisflow.core.detection.batch-size",Integer.class));
    }
    @Bean DetectionTick detectionTick(DeadlineDetector detector) { return new DetectionTick(detector); }
    static final class DetectionTick {
        private final DeadlineDetector detector;
        DetectionTick(DeadlineDetector detector) { this.detector=detector; }
        @Scheduled(fixedDelay=1000) public void tick() {
            try { detector.poll(); }
            catch(RuntimeException failed) {
                org.slf4j.LoggerFactory.getLogger(DetectionTick.class).warn(
                    "Deadline sweep rolled back; durable work retained for retry");
            }
        }
    }
}
