package com.aegisflow.core.detection.internal;
import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;

/** Test-only fresh-JVM worker entry; no production HTTP/API, secrets or payload output. */
public final class IncidentProcessMain {
    public static void main(String[] args) {
        var env=System.getenv();
        var ds=new DriverManagerDataSource(env.get("INCIDENT_TEST_URL"),env.get("INCIDENT_TEST_USER"),env.get("INCIDENT_TEST_PASSWORD"));
        var recorder=new IncidentRecorder(new JdbcTemplate(ds),new DataSourceTransactionManager(ds),
            Clock.fixed(Instant.parse(env.get("INCIDENT_TEST_TIME")),ZoneOffset.UTC),25);
        recorder.poll();
    }
}
