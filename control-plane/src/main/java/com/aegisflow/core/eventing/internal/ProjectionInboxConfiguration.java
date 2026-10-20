package com.aegisflow.core.eventing.internal;

import com.aegisflow.core.eventing.ProjectionInbox;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Backfill remains available even while Kafka intake is disabled or offline. */
@Configuration(proxyBeanMethods=false)
@Profile("postgres")
class ProjectionInboxConfiguration {
    @Bean ProjectionInbox projectionInbox(JdbcTemplate jdbc) { return new JdbcProjectionInbox(jdbc); }
}
