package com.aegisflow.core.detection.internal;
import static org.assertj.core.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
class IncidentProcessLaunchTests {
    @Test void quotesLongWindowsClasspathInArgumentFile() {
        String cp="C:\\Program Files\\classes;"+"C:\\dependency.jar;".repeat(3000);
        String args=IncidentProcessLaunch.arguments(cp);
        assertThat(args).startsWith("-cp\n\"C:\\\\Program Files\\\\classes;")
            .endsWith("\"\n"+IncidentProcessMain.class.getName()+"\n");
    }
    @Test void rejectsArgumentFileLineInjection() {
        assertThatThrownBy(() -> IncidentProcessLaunch.arguments("classes\n-unwanted")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void dropsInheritedAppAndJavaOptionOverrides() {
        assertThat(IncidentProcessLaunch.environment(Map.of("PATH","runtime","CORE_DB_RUNTIME_PASSWORD","private",
            "JAVA_TOOL_OPTIONS","override","INCIDENT_TEST_URL","foreign"))).containsOnlyKeys("PATH");
    }
}
