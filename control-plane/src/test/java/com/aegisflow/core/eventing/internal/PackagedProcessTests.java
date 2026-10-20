package com.aegisflow.core.eventing.internal;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PackagedProcessTests {
    @Test void childEnvironmentDoesNotInheritOtherOwnersCredentialsOrConfigOverrides() {
        var filtered=PackagedApplicationProcess.isolatedEnvironment(Map.of(
                "Path","system-path","TEMP","temporary","PAYMENT_DB_RUNTIME_PASSWORD","foreign",
                "CORE_INBOX_ENABLED","true","SPRING_CONFIG_ADDITIONAL_LOCATION","private",
                "KAFKA_ADMIN_PASSWORD","private","JAVA_TOOL_OPTIONS","foreign-options","_JAVA_OPTIONS","foreign-options"));
        assertThat(filtered).containsOnlyKeys("Path","TEMP");
    }
    @Test void executableJarUsesIndependentClasspathAndOperatingSystemChosenHttpPort() {
        var command=PackagedApplicationProcess.command(Path.of("C:/Java Runtime/bin/java.exe"),Path.of("C:/project folder/app.jar"));
        assertThat(command).contains("-jar","C:\\project folder\\app.jar","--spring.profiles.active=postgres",
                "--server.address=127.0.0.1","--server.port=0");
        assertThat(command).doesNotContain("-cp","-classpath");
    }
    @Test void extractsActualEphemeralHttpPortAndRejectsInvalidOrUnrelatedEndpoints() {
        assertThat(PackagedApplicationProcess.httpPort("Tomcat started on port 54321 (http) with context path '/'").orElseThrow()).isEqualTo(54321);
        for(String log:new String[]{"jdbc:postgresql://localhost:54321/db","Tomcat initialized with port 0 (http)",
                "Tomcat started on port 0 (http)","Tomcat started on port 65536 (http)"})
            assertThat(PackagedApplicationProcess.httpPort(log)).isEmpty();
    }
    @Test void healthAcceptsKnownActuatorGroupsButRejectsFalsePositives() {
        assertThat(PackagedApplicationProcess.healthyResponse(200,"{\"status\":\"UP\",\"groups\":[\"liveness\",\"readiness\"]}")).isTrue();
        assertThat(PackagedApplicationProcess.healthyResponse(200,"{\"groups\":[],\"status\":\"UP\"}")).isTrue();
        assertThat(PackagedApplicationProcess.healthyResponse(503,"{\"status\":\"UP\"}")).isFalse();
        for(String body:new String[]{"{\"status\":\"DOWN\"}","{\"nested\":{\"status\":\"UP\"}}",
                "{\"status\":\"DOWN\",\"status\":\"UP\"}","{\"status\":\"UP\"} {}", "{\"status\":\"UP\",\"groups\":[1]}"})
            assertThat(PackagedApplicationProcess.healthyResponse(200,body)).isFalse();
    }
}
