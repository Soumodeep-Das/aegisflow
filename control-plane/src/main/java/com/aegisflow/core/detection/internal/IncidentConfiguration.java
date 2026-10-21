package com.aegisflow.core.detection.internal;

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
@ConditionalOnProperty(name="aegisflow.core.incidents.enabled",havingValue="true")
@EnableScheduling
class IncidentConfiguration {
    @Bean Clock incidentClock() { return Clock.systemUTC(); }
    @Bean IncidentRecorder incidentRecorder(JdbcTemplate jdbc,PlatformTransactionManager manager,
            @Qualifier("incidentClock") Clock clock,Environment env) {
        return new IncidentRecorder(jdbc,manager,clock,
            env.getRequiredProperty("aegisflow.core.incidents.batch-size",Integer.class));
    }
    @Bean IncidentTick incidentTick(IncidentRecorder recorder) { return new IncidentTick(recorder); }
    static final class IncidentTick {
        private final IncidentRecorder recorder;
        IncidentTick(IncidentRecorder recorder) { this.recorder=recorder; }
        @Scheduled(fixedDelay=1000) public void tick() {
            try { recorder.poll(); }
            catch(RuntimeException failed) {
                org.slf4j.LoggerFactory.getLogger(IncidentTick.class).warn(
                    "Incident transaction rolled back; evaluation remains eligible for retry");
            }
        }
    }
}
