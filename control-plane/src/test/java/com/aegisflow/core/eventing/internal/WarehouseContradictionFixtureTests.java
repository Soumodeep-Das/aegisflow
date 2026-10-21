package com.aegisflow.core.eventing.internal;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class WarehouseContradictionFixtureTests {
    @Test void changesClaimWithoutMutatingOriginalOrBusinessIdentity() {
        Map<String,Object> details=Map.of("reservationId","reservation-1","reservationVersion",1,
                "orderId","order-1","items",List.of(Map.of("sku","sku-1","quantity",2)));
        Map<String,Object> original=Map.of("eventId",UUID.randomUUID().toString(),"eventType","InventoryReserved",
                "tenantId","tenant-a","businessTransactionId","tx-1","sourceSystem","warehouse-service",
                "occurredAt","2026-10-21T00:00:00Z","schemaVersion","1.0","details",details);
        var changed=WarehouseContradictionFixture.rejectedClaim(original);
        assertThat(changed.get("eventId")).isNotEqualTo(original.get("eventId"));
        assertThatCode(() -> UUID.fromString((String)changed.get("eventId"))).doesNotThrowAnyException();
        for(String field:List.of("tenantId","businessTransactionId","sourceSystem","occurredAt","schemaVersion"))
            assertThat(changed.get(field)).isEqualTo(original.get(field));
        assertThat((Map<String,Object>)changed.get("details")).containsEntry("reasonCode","OUT_OF_STOCK")
                .containsEntry("reservationId","reservation-1").containsEntry("reservationVersion",1)
                .containsEntry("orderId","order-1").doesNotContainKey("items");
        assertThat(details).containsKey("items").doesNotContainKey("reasonCode");
        assertThat(original.get("eventType")).isEqualTo("InventoryReserved");
    }
    @Test void requiresReservationInput() {
        assertThatThrownBy(() -> WarehouseContradictionFixture.rejectedClaim(Map.of("eventType","OrderCreated")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
