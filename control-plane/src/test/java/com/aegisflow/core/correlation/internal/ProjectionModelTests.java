package com.aegisflow.core.correlation.internal;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.aegisflow.core.correlation.internal.ProjectionModel.*;

class ProjectionModelTests {
    static final Instant WHEN=Instant.parse("2026-10-20T10:00:00Z");
    static Fact fact(String type,String entity,long version,String order,Map<String,Integer> items) {
        String source=type.equals("OrderCreated") ? "order-service" : type.startsWith("Payment") ? "payment-service" : "warehouse-service";
        return new Fact(UUID.randomUUID(),"tenant-a","txn",source,UUID.randomUUID(),type,entity,version,WHEN,WHEN,
                "1.0","{}","a".repeat(64),"aegisflow-inbox-json-v1",
                new Value(order,items,null,null,type.equals("InventoryRejected") ? "OUT_OF_STOCK" : null));
    }
    @Test void warehouseBeforeOrderRemainsPendingThenConvergesInBothOrders() {
        var warehouse=fact("InventoryReserved","reservation",1,"order",Map.of("sku",2));
        var order=fact("OrderCreated","order",1,"order",Map.of("sku",2));
        assertThat(reduce(List.of(warehouse)).status()).isEqualTo("AWAITING_ORDER");
        assertThat(reduce(List.of(order,warehouse))).isEqualTo(reduce(List.of(warehouse,order)));
        assertThat(reduce(List.of(order,warehouse)).status()).isEqualTo("INVENTORY_OBSERVED");
    }
    @Test void olderVersionNeverReplacesHighestVersion() {
        var newer=fact("InventoryReserved","reservation",7,"order",Map.of("sku",2));
        var older=fact("InventoryReserved","reservation",2,"order",Map.of("sku",2));
        assertThat(reduce(List.of(newer,older)).inventory().fact()).isEqualTo(newer);
        assertThat(reduce(List.of(newer,older)).inventory().conflict()).isFalse();
    }
    @Test void contradictorySameVersionIsPreserved() {
        var reserved=fact("InventoryReserved","reservation",1,"order",Map.of("sku",2));
        var rejected=fact("InventoryRejected","reservation",1,"order",Map.of());
        assertThat(reduce(List.of(reserved,rejected)).status()).isEqualTo("CONFLICT");
    }
    @Test void unsupportedHigherVersionTerminalChangeIsNotSilentlyAccepted() {
        var reserved=fact("InventoryReserved","reservation",1,"order",Map.of("sku",2));
        var rejected=fact("InventoryRejected","reservation",2,"order",Map.of());
        var reduced=reduce(List.of(rejected,reserved));
        assertThat(reduced.inventory().fact()).isEqualTo(rejected);
        assertThat(reduced.inventory().conflict()).isTrue();
    }
    @Test void multipleSourceAggregatesDoNotBecomeOneUnambiguousOutcome() {
        assertThat(reduce(List.of(fact("InventoryReserved","r1",1,"order",Map.of("sku",2)),
                fact("InventoryReserved","r2",1,"order",Map.of("sku",2)))).status()).isEqualTo("CONFLICT");
    }
    @Test void rejectedIsAnObservedOutcomeWithoutInventingItems() {
        var state=reduce(List.of(fact("OrderCreated","order",1,"order",Map.of("sku",2)),
                fact("InventoryRejected","r1",1,"order",Map.of())));
        assertThat(state.status()).isEqualTo("INVENTORY_OBSERVED");
        assertThat(state.inventory().fact().value().items()).isEmpty();
        assertThat(state.inventory().fact().value().reason()).isEqualTo("OUT_OF_STOCK");
    }
    @Test void wrongOrderIdentityRemainsConflict() {
        assertThat(reduce(List.of(fact("OrderCreated","order",1,"order",Map.of("sku",2)),
                fact("InventoryReserved","r1",1,"other",Map.of("sku",2)))).status()).isEqualTo("CONFLICT");
    }
    @Test void wrongQuantityIsRetainedForDetectorRatherThanDeclaredSatisfied() {
        var state=reduce(List.of(fact("OrderCreated","order",1,"order",Map.of("sku",2)),
                fact("InventoryReserved","r1",1,"order",Map.of("sku",1))));
        assertThat(state.status()).isEqualTo("INVENTORY_OBSERVED");
        assertThat(state.order().fact().value().items()).isNotEqualTo(state.inventory().fact().value().items());
    }
    @Test void itemOrderingIsNotAConflict() {
        var a=new LinkedHashMap<String,Integer>(); a.put("a",1); a.put("b",2);
        var b=new LinkedHashMap<String,Integer>(); b.put("b",2); b.put("a",1);
        assertThat(reduce(List.of(fact("InventoryReserved","r",1,"o",a),
                fact("InventoryReserved","r",1,"o",b))).inventory().conflict()).isFalse();
    }
    @Test void latenessIsAppliedExactlyOnce() {
        var contract=new Contract("demo-v1",45,15);
        assertThat(contract.due(WHEN)).isEqualTo(WHEN.plusSeconds(45));
        assertThat(contract.enforcement(WHEN)).isEqualTo(WHEN.plusSeconds(60));
    }
    @Test void invalidConfigurationIsRejected() {
        assertThatThrownBy(() -> new Contract("bad version",45,15)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Contract("v1",0,15)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Contract("v1",45,-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
