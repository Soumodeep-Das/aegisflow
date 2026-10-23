package com.aegisflow.simulator.order;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LocalDemoTests {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temporary;
    @Test void barrierRequiresReleaseAndNeverWaitsUnboundedly() throws Exception {
        var blocked = temporary.resolve("blocked");
        assertThatThrownBy(() -> LocalDemo.awaitBarrier(blocked, java.time.Duration.ZERO))
                .isInstanceOf(IllegalStateException.class).hasMessage("Demo readiness barrier timed out");
        assertThat(java.nio.file.Files.exists(temporary.resolve("blocked.ready"))).isTrue();
        var released = temporary.resolve("released");
        java.nio.file.Files.createFile(temporary.resolve("released.go"));
        LocalDemo.awaitBarrier(released, java.time.Duration.ofSeconds(1));
        assertThat(java.nio.file.Files.exists(temporary.resolve("released.ready"))).isTrue();
    }
    @Test void rejectsMissingAndDuplicateRunArguments() {
        assertThatThrownBy(() -> LocalDemo.argument(new String[0],"run")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LocalDemo.argument(new String[]{"--aegisflow.demo.run=a","--aegisflow.demo.run=b"},"run")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void validatesOnlyNarrowOperations() {
        LocalDemo.validate("healthy","create");
        assertThatThrownBy(() -> LocalDemo.validate("arbitrary","create")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LocalDemo.validate("missing","refund")).isInstanceOf(IllegalArgumentException.class);
        
    }
}
