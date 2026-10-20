package com.aegisflow.core.detection.internal;

import static org.assertj.core.api.Assertions.*;
import com.aegisflow.core.correlation.DetectionProjections.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class InventoryInvariantTests {
    static final Instant START=Instant.parse("2026-10-20T10:00:00Z");
    static Fact fact(String type,String order,int quantity,Instant occurred) {
        return new Fact(UUID.randomUUID(),"warehouse-service",type,"entity",1,order,Map.of("sku",quantity),
            "OUT_OF_STOCK",occurred,START.plusSeconds(70));
    }
    static Snapshot snapshot(Fact warehouse,boolean conflict) {
        var order=fact("OrderCreated","order",2,START);
        return new Snapshot("tenant","txn",1,"ECOM_ORDER_V1",1,"demo-v1",45,15,START.plusSeconds(45),
            START.plusSeconds(60),order,false,warehouse,conflict,warehouse==null?List.of(order):List.of(order,warehouse));
    }
    static InventoryInvariant.Decision decide(Snapshot s,int seconds) {
        return InventoryInvariant.evaluate(s,false,START.plusSeconds(seconds));
    }
    @Test void missingBeforeThresholdIsUnknown() {
        assertThat(decide(snapshot(null,false),59).result()).isEqualTo("UNKNOWN");
    }
    @Test void exactThresholdIsEligibleWithLatenessAppliedOnce() {
        assertThat(decide(snapshot(null,false),60).reason()).isEqualTo("MISSING_OBSERVED_OUTCOME");
        assertThat(decide(snapshot(null,false),60).result()).isEqualTo("VIOLATED");
    }
    @Test void reservedExactItemsIsSatisfied() {
        assertThat(decide(snapshot(fact("InventoryReserved","order",2,START.plusSeconds(45)),false),60).result()).isEqualTo("SATISFIED");
    }
    @Test void supportedRejectionIsValidOutcomeNotMissing() {
        assertThat(decide(snapshot(fact("InventoryRejected","order",0,START.plusSeconds(30)),false),60).result()).isEqualTo("SATISFIED");
    }
    @Test void partialReservationViolatesOnlyAfterEnforcement() {
        var s=snapshot(fact("InventoryReserved","order",1,START),false);
        assertThat(decide(s,59).result()).isEqualTo("UNKNOWN");
        assertThat(decide(s,60).reason()).isEqualTo("QUANTITY_MISMATCH");
    }
    @Test void wrongOrderViolates() {
        assertThat(decide(snapshot(fact("InventoryReserved","other",2,START),false),60).reason()).isEqualTo("ORDER_MISMATCH");
    }
    @Test void contradictoryOutcomeIsNotSilentlySatisfied() {
        assertThat(decide(snapshot(fact("InventoryReserved","order",2,START),true),60).reason()).isEqualTo("CONTRADICTORY_OUTCOMES");
    }
    @Test void backlogAlwaysSuppressesDefinitiveResult() {
        assertThat(InventoryInvariant.evaluate(snapshot(null,false),true,START.plusSeconds(90)).result()).isEqualTo("UNKNOWN");
    }
    @Test void lateDeliveryOfOnTimeEventChangesCurrentEvidence() {
        assertThat(decide(snapshot(fact("InventoryReserved","order",2,START.plusSeconds(30)),false),100).result()).isEqualTo("SATISFIED");
    }
    @Test void lateBusinessOccurrenceDoesNotHideMissedDeadline() {
        assertThat(decide(snapshot(fact("InventoryReserved","order",2,START.plusSeconds(46)),false),100).reason()).isEqualTo("OUTCOME_AFTER_DEADLINE");
    }
    @Test void sourceClockInconsistencyIsUnknown() {
        assertThat(decide(snapshot(fact("InventoryReserved","order",2,START.minusSeconds(1)),false),100).result()).isEqualTo("UNKNOWN");
    }
    @Test void missingOrderIsUnknown() {
        var s=snapshot(null,false);
        var noOrder=new Snapshot(s.tenant(),s.transaction(),1,s.contract(),1,s.configuration(),45,15,null,null,
            null,false,null,false,List.of());
        assertThat(decide(noOrder,100).reason()).isEqualTo("ORDER_NOT_OBSERVED");
    }
    @Test void ambiguousOrderIsUnknown() {
        var s=snapshot(null,false);
        var ambiguous=new Snapshot(s.tenant(),s.transaction(),1,s.contract(),1,s.configuration(),45,15,null,null,
            s.order(),true,null,false,s.facts());
        assertThat(decide(ambiguous,100).reason()).isEqualTo("ORDER_AMBIGUOUS");
    }
    @Test void versionedNonDemoDurationsAreRespected() {
        var s=snapshot(null,false);
        var other=new Snapshot(s.tenant(),s.transaction(),1,s.contract(),1,"slow-v2",90,20,START.plusSeconds(90),START.plusSeconds(110),
            s.order(),false,null,false,s.facts());
        assertThat(decide(other,109).result()).isEqualTo("UNKNOWN");
        assertThat(decide(other,110).result()).isEqualTo("VIOLATED");
    }
}
