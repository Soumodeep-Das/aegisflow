package com.aegisflow.simulator.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OrderKafkaFixtureTests {
    @Test void bindingUsesExactSelectedLoopbackPortRatherThanDockerAssignedPort() {
        var mapping = OrderKafkaFixture.loopbackBinding(52354);
        assertThat(mapping.getBinding().getHostIp()).isEqualTo("127.0.0.1");
        assertThat(mapping.getBinding().getHostPortSpec()).isEqualTo("52354");
        assertThat(mapping.getExposedPort().getPort()).isEqualTo(19092);
    }

    @Test void unselectedPrivilegedAndOutOfRangePortsAreRejected() {
        for (int port : new int[]{-1, 0, 1023, 65536})
            assertThatThrownBy(() -> OrderKafkaFixture.loopbackBinding(port))
                    .isInstanceOf(IllegalArgumentException.class);
    }
}
