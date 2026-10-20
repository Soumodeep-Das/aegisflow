package com.aegisflow.core.correlation.internal;

import com.aegisflow.core.eventing.ProjectionInbox.AcceptedEvent;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Pure reduction of validated facts; arrival order never chooses the effective source version. */
final class ProjectionModel {
    static final JsonMapper JSON=new JsonMapper();
    record Contract(String configurationVersion,int inventorySeconds,int latenessSeconds) {
        Contract {
            if(configurationVersion==null || !configurationVersion.matches("[A-Za-z0-9._-]{1,64}")
                    || inventorySeconds<1 || inventorySeconds>86400 || latenessSeconds<0 || latenessSeconds>86400)
                throw new IllegalArgumentException("Invalid versioned inventory contract");
        }
        Instant due(Instant occurred) { return occurred.plusSeconds(inventorySeconds); }
        Instant enforcement(Instant occurred) { return due(occurred).plusSeconds(latenessSeconds); }
    }
    record Value(String orderId,Map<String,Integer> items,String amount,String currency,String reason) {
        Value { items=Collections.unmodifiableMap(new TreeMap<>(items)); }
    }
    record Fact(UUID id,String tenant,String transaction,String source,UUID eventId,String type,String entity,
                long version,Instant occurred,Instant received,String schema,String details,String hash,
                String hashVersion,Value value) {}
    record Effective(Fact fact,boolean conflict) {}
    record Snapshot(Effective order,Effective payment,Effective inventory,String status) {}

    static Fact from(AcceptedEvent event,UUID factId) {
        var details=JSON.readTree(event.payload()).get("details");
        return fact(factId,event.key().tenant(),event.key().transaction(),event.key().source(),
                event.key().eventId(),event.type(),event.occurred(),event.received(),event.schema(),
                JSON.writeValueAsString(details),event.hash(),event.hashVersion());
    }
    static Fact fact(UUID id,String tenant,String transaction,String source,UUID eventId,String type,
                     Instant occurred,Instant received,String schema,String details,String hash,String hashVersion) {
        JsonNode value=JSON.readTree(details);
        String entityField,versionField,itemField=null;
        switch(type) {
            case "OrderCreated" -> { entityField="orderId"; versionField="orderVersion"; itemField="lineItems"; }
            case "PaymentAuthorized","PaymentDeclined" -> { entityField="paymentId"; versionField="paymentVersion"; }
            case "InventoryReserved","InventoryRejected" -> {
                entityField="reservationId"; versionField="reservationVersion";
                if(type.equals("InventoryReserved")) itemField="items";
            }
            default -> throw new IllegalArgumentException("Unsupported accepted event shape");
        }
        String entity=required(value,entityField), order=required(value,"orderId");
        var version=value.get(versionField);
        if(version==null || !version.isIntegralNumber() || !version.canConvertToLong() || version.longValue()<1)
            throw new IllegalArgumentException("Invalid accepted aggregate version");
        Map<String,Integer> items=new TreeMap<>();
        if(itemField!=null) {
            var array=value.get(itemField);
            if(array==null || !array.isArray() || array.isEmpty() || array.size()>100)
                throw new IllegalArgumentException("Invalid accepted items");
            for(var line:array) {
                var quantity=line.get("quantity");
                if(quantity==null || !quantity.isIntegralNumber() || !quantity.canConvertToInt()
                        || quantity.intValue()<1 || quantity.intValue()>1_000_000
                        || items.put(required(line,"sku"),quantity.intValue())!=null)
                    throw new IllegalArgumentException("Invalid accepted item");
            }
        }
        return new Fact(id,tenant,transaction,source,eventId,type,entity,version.longValue(),occurred,received,
                schema,details,hash,hashVersion,new Value(order,items,optional(value,"amount"),
                optional(value,"currency"),optional(value,"reasonCode")));
    }
    private static String required(JsonNode value,String field) {
        String result=optional(value,field);
        if(result==null || result.isBlank()) throw new IllegalArgumentException("Missing accepted identity");
        return result;
    }
    private static String optional(JsonNode value,String field) {
        return value.hasNonNull(field) ? value.get(field).stringValue() : null;
    }
    static Snapshot reduce(List<Fact> facts) {
        var order=effective(facts,"order-service");
        var payment=effective(facts,"payment-service");
        var inventory=effective(facts,"warehouse-service");
        boolean conflict=order.conflict() || payment.conflict() || inventory.conflict();
        if(order.fact()!=null) {
            for(var candidate:List.of(payment,inventory)) {
                if(candidate.fact()!=null && !candidate.fact().value().orderId().equals(order.fact().value().orderId()))
                    conflict=true;
            }
        }
        String status=conflict ? "CONFLICT" : order.fact()==null ? "AWAITING_ORDER"
                : inventory.fact()==null ? "PENDING_INVENTORY" : "INVENTORY_OBSERVED";
        // INVENTORY_OBSERVED is not SATISFIED: quantity/deadline decisions belong to the later detector.
        return new Snapshot(order,payment,inventory,status);
    }
    private static Effective effective(List<Fact> facts,String source) {
        var candidates=facts.stream().filter(f -> f.source().equals(source)).sorted(
                Comparator.comparingLong(Fact::version).reversed().thenComparing(f -> f.eventId().toString())).toList();
        if(candidates.isEmpty()) return new Effective(null,false);
        Fact chosen=candidates.getFirst();
        boolean conflict=candidates.stream().anyMatch(f ->
                !f.entity().equals(chosen.entity()) || !f.type().equals(chosen.type())
                || !f.value().equals(chosen.value())
                || (f.version()==chosen.version() && !f.occurred().equals(chosen.occurred())));
        return new Effective(chosen,conflict);
    }
}
