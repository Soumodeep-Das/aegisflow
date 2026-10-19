package com.aegisflow.simulator.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/** Source-level regression guards, not a substitute for real Linux/container startup. */
class KafkaStartupScriptTests {
    @Test void privateCopyIsWritableBeforeAuthenticationIsAppended() throws Exception {
        String script = Files.readString(OrderKafkaFixture.scriptDirectory().resolve("startup.sh"));
        int copy = script.indexOf("cp /opt/aegisflow/kafka/server.properties \"$server_config\"");
        int secure = script.indexOf("chmod 600 \"$server_config\"");
        int append = script.indexOf("} >> \"$server_config\"");
        assertThat(copy).isGreaterThanOrEqualTo(0);
        assertThat(secure).isGreaterThan(copy);
        assertThat(append).isGreaterThan(secure);
        assertThat(script).doesNotContain("chmod 600 /opt/aegisflow/kafka/server.properties");
    }

    @Test void configurationFailuresExplicitlyStopBeforeBrokerLaunch() throws Exception {
        String script = Files.readString(OrderKafkaFixture.scriptDirectory().resolve("startup.sh"));
        assertThat(script).contains("cp /opt/aegisflow/kafka/server.properties \"$server_config\" || fail ");
        assertThat(script).contains("chmod 600 \"$server_config\" || fail ");
        assertThat(script).contains("} >> \"$server_config\" || fail ");
        assertThat(script.indexOf("} >> \"$server_config\" || fail "))
                .isLessThan(script.indexOf("exec /opt/kafka/bin/kafka-server-start.sh"));
    }
}
