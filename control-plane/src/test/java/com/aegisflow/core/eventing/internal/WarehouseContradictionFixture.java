package com.aegisflow.core.eventing.internal;

import java.util.*;

/** Explicit faulty-producer claim, not a second valid Warehouse business operation. */
final class WarehouseContradictionFixture {
    static Map<String,Object> rejectedClaim(Map<String,Object> reserved) {
        if(!"InventoryReserved".equals(reserved.get("eventType")))
            throw new IllegalArgumentException("A committed reservation envelope is required");
        var event=new HashMap<>(reserved);
        var details=new HashMap<>((Map<String,Object>)reserved.get("details"));
        event.put("eventId",UUID.randomUUID().toString());
        event.put("eventType","InventoryRejected");
        details.remove("items"); details.put("reasonCode","OUT_OF_STOCK");
        event.put("details",details);
        return event;
    }
}
