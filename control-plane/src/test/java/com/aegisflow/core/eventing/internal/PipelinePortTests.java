package com.aegisflow.core.eventing.internal;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Guards for the actual port failures recorded in the earlier host gates. No Docker needed. */
class PipelinePortTests {
    @Test void kafkaUsesExplicitIpv4LoopbackPortForItsLifetime() {
        var mapping = CoreKafkaFixture.loopbackBinding(52354);
        assertThat(mapping.getBinding().getHostIp()).isEqualTo("127.0.0.1");
        assertThat(mapping.getBinding().getHostPortSpec()).isEqualTo("52354");
        assertThat(mapping.getExposedPort().getPort()).isEqualTo(19092);
    }
    @Test void kafkaRejectsUnselectedPrivilegedAndOutOfRangeBindings() {
        for (int port : new int[]{-1,0,1023,65536})
            assertThatThrownBy(() -> CoreKafkaFixture.loopbackBinding(port)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void everyOwnerOverridesBothRuntimeAndFlywayWithTheMappedContainerEndpoint() {
        String mapped = "jdbc:postgresql://localhost:53427/disposable?loggerLevel=OFF";
        for (String owner : new String[]{"core","order","payment","warehouse"}) {
            var args = PipelineApplications.databaseArguments(owner,mapped);
            assertThat(args).contains("--spring.datasource.url=" + mapped,"--spring.flyway.url=" + mapped,
                    "--spring.datasource.username=aegisflow_" + owner + "_runtime",
                    "--spring.flyway.user=aegisflow_" + owner + "_migrator");
            assertThat(args.stream().filter(s -> s.startsWith("--spring.flyway.locations=")).findFirst().orElseThrow())
                    .startsWith("--spring.flyway.locations=filesystem:").endsWith("/src/main/resources/db/migration");
        }
    }
    @Test void consumerRejectsInvalidAdvertisedEndpointPorts() {
        for (int port : new int[]{0,1023,65536})
            assertThatThrownBy(() -> InboxConfiguration.consumerProperties("127.0.0.1:" + port,
                    "a".repeat(64),"aegisflow-core-test")).isInstanceOf(IllegalArgumentException.class);
        assertThat(InboxConfiguration.consumerProperties("kafka:9092","a".repeat(64),"aegisflow-core-test"))
                .containsEntry("bootstrap.servers","kafka:9092");
    }
}
