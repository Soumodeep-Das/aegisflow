package com.aegisflow.simulator.order;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LocalDemoTests {
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
