package com.aegisflow.core;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import com.aegisflow.core.eventing.ProjectionInbox;
import com.aegisflow.core.correlation.DetectionProjections;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;

class CoreClockConfigurationTests {
    private ApplicationContextRunner base() throws Exception {
        return new ApplicationContextRunner().withUserConfiguration(CoreClockConfiguration.class)
                .withConfiguration(AutoConfigurations.of(Class.forName(
                        "org.springframework.modulith.moments.autoconfigure.MomentsAutoConfiguration")));
    }
    @Test void infrastructureHasUtcClockWithoutWorkers() throws Exception {
        base().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(Clock.class).hasBean("moments");
            assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
        });
    }
    @Test void allRealWorkerConfigurationsAndMomentsResolveClocksTogether() throws Exception {
        base().withUserConfiguration(
                Class.forName("com.aegisflow.core.correlation.internal.ProjectionConfiguration"),
                Class.forName("com.aegisflow.core.detection.internal.DetectionConfiguration"),
                Class.forName("com.aegisflow.core.detection.internal.IncidentConfiguration"))
                .withPropertyValues("spring.profiles.active=postgres",
                        "aegisflow.core.projection.enabled=true","aegisflow.core.projection.configuration-version=demo-v1",
                        "aegisflow.core.projection.inventory-seconds=45","aegisflow.core.projection.lateness-seconds=15",
                        "aegisflow.core.projection.batch-size=25","aegisflow.core.detection.enabled=true",
                        "aegisflow.core.detection.batch-size=25","aegisflow.core.incidents.enabled=true",
                        "aegisflow.core.incidents.batch-size=25")
                .withBean(ProjectionInbox.class,() -> mock(ProjectionInbox.class))
                .withBean(DetectionProjections.class,() -> mock(DetectionProjections.class))
                .withBean(JdbcTemplate.class,() -> mock(JdbcTemplate.class))
                .withBean(PlatformTransactionManager.class,() -> mock(PlatformTransactionManager.class))
                .withBean(TaskScheduler.class,() -> mock(TaskScheduler.class))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasBean("moments").hasBean("correlationProjector")
                            .hasBean("deadlineDetector").hasBean("incidentRecorder");
                    assertThat(context.getBeansOfType(Clock.class)).hasSize(4);
                    assertThat(context.getBean(Clock.class)).isSameAs(context.getBean("applicationClock"));
                    for(String name: new String[]{"projectionClock","detectionClock","incidentClock"})
                        assertThat(context.getBean(name,Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
                });
    }
}
