package com.aegisflow.simulator.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderPersistenceTests {
    private static final UUID ID = UUID.fromString("d1e02e42-264b-4ead-ab1a-cfbf16799bd0");
    private CreateOrder command(String correlation, String amount, List<CreateOrder.Line> lines) {
        return new CreateOrder("tenant-demo", ID, "txn-1", correlation, "request-1", new BigDecimal(amount), "INR", lines);
    }
    @Test void rejectsMissingTenantAndInvalidQuantities() {
        assertThatThrownBy(() -> new CreateOrder(" ", ID, "txn", "corr", "key", BigDecimal.ONE, "INR", List.of(new CreateOrder.Line("sku", 1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreateOrder.Line("sku", 0)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsMoneyThatCannotBeRepresentedWithoutRounding() {
        assertThatThrownBy(() -> command("corr", "1.001", List.of(new CreateOrder.Line("sku", 1))))
                .isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> command("corr", "-1", List.of(new CreateOrder.Line("sku", 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsDuplicateSkuAndExcessiveAmount() {
        assertThatThrownBy(() -> command("corr", "1", List.of(new CreateOrder.Line("sku", 1), new CreateOrder.Line("sku", 2))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command("corr", "1000000000000000", List.of(new CreateOrder.Line("sku", 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void equivalentMaterialRequestsHaveSameVersionedHash() {
        var first = command("corr-a", "1499", List.of(new CreateOrder.Line("z", 2), new CreateOrder.Line("a", 1)));
        var repeat = command("corr-b", "1499.00", List.of(new CreateOrder.Line("a", 1), new CreateOrder.Line("z", 2)));
        assertThat(CanonicalJson.requestHash(first)).isEqualTo(CanonicalJson.requestHash(repeat));
        assertThat(CanonicalJson.requestHash(command("corr-a", "1499.01", first.lines())))
                .isNotEqualTo(CanonicalJson.requestHash(first));
    }
    @Test void canonicalObjectsSortKeysRecursivelyAndKeepArrayOrder() {
        assertThat(CanonicalJson.write(Map.of("z", List.of(2,1), "a", Map.of("y",1,"b",2))))
                .isEqualTo("{\"a\":{\"b\":2,\"y\":1},\"z\":[2,1]}");
        assertThat(CanonicalJson.hash("{}")).isEqualTo("44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a");
    }
    @Test void orderCreatedMatchesFrozenContractFixture() throws Exception {
        var command = command("corr-1", "1499.00", List.of(new CreateOrder.Line("SKU-1",2)));
        try (var resource = getClass().getResourceAsStream("/contracts/order-created-v1.json")) {
            assertThat(resource).isNotNull();
            assertThat(OrderOutbox.envelope(command,UUID.fromString("14b47a9f-5d34-4a02-bb08-2cd504ca9f43"),1,
                    Instant.parse("2026-10-12T08:00:00.123456Z")))
                    .isEqualTo(new String(resource.readAllBytes(),StandardCharsets.UTF_8).trim());
        }
    }
}
