package com.aegisflow.core.detection.internal;

import com.aegisflow.core.correlation.DetectionProjections.Snapshot;
import java.time.Instant;

/** INV-002 v1: an observed outcome, never an authoritative remote-state verification. */
final class InventoryInvariant {
    record Decision(String result,String reason,String phase) {}
    static Decision evaluate(Snapshot s, boolean unsettled, Instant now) {
        boolean enforced=s.enforceAfter()!=null && !now.isBefore(s.enforceAfter());
        String phase=enforced?"ENFORCED":"PENDING";
        if(unsettled) return new Decision("UNKNOWN","PROJECTION_UNSETTLED",phase);
        if(s.order()==null) return new Decision("UNKNOWN","ORDER_NOT_OBSERVED",phase);
        if(s.orderConflict() || s.due()==null || s.enforceAfter()==null)
            return new Decision("UNKNOWN","ORDER_AMBIGUOUS",phase);
        var outcome=s.inventory();
        String violation=null;
        if(s.inventoryConflict()) violation="CONTRADICTORY_OUTCOMES";
        else if(outcome==null) violation="MISSING_OBSERVED_OUTCOME";
        else if(!s.order().orderId().equals(outcome.orderId())) violation="ORDER_MISMATCH";
        else if(outcome.occurred().isBefore(s.order().occurred()))
            return new Decision("UNKNOWN","OUTCOME_PRECEDES_ORDER",phase);
        else if(outcome.type().equals("InventoryReserved") && !s.order().items().equals(outcome.items()))
            violation="QUANTITY_MISMATCH";
        else if(outcome.type().equals("InventoryRejected") && !"OUT_OF_STOCK".equals(outcome.reason()))
            return new Decision("UNKNOWN","UNSUPPORTED_REJECTION",phase);
        else if(!outcome.type().equals("InventoryReserved") && !outcome.type().equals("InventoryRejected"))
            return new Decision("UNKNOWN","UNSUPPORTED_OUTCOME",phase);
        else if(outcome.occurred().isAfter(s.due())) violation="OUTCOME_AFTER_DEADLINE";
        if(violation!=null) return new Decision(enforced?"VIOLATED":"UNKNOWN",
            enforced?violation:"AWAITING_ENFORCEMENT",phase);
        // Late delivery of an on-time outcome changes current evidence, not immutable past evaluations.
        return new Decision("SATISFIED","VALID_OBSERVED_OUTCOME",phase);
    }
}
