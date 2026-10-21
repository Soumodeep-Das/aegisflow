package com.aegisflow.core.detection.internal;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
class IncidentPolicyTests {
    @Test void onlyViolationOpens() { assertThat(IncidentPolicy.opens("VIOLATED")).isTrue(); }
    @Test void satisfactionDoesNotOpenOrResolve() {
        assertThat(IncidentPolicy.opens("SATISFIED")).isFalse();
        assertThat(IncidentPolicy.kind(false,"SATISFIED")).isEqualTo("EVIDENCE_CHANGED");
    }
    @Test void unknownDoesNotOpen() { assertThat(IncidentPolicy.opens("UNKNOWN")).isFalse(); }
    @Test void episodeIsStableAndConfigurationBound() {
        assertThat(IncidentPolicy.episode("INV-002",1,"ECOM_ORDER_V1",1,"demo-v1"))
            .isEqualTo("INV-002:1:ECOM_ORDER_V1:1:demo-v1")
            .isNotEqualTo(IncidentPolicy.episode("INV-002",1,"ECOM_ORDER_V1",1,"other"));
    }
    @Test void missingEvidenceIsNotLabelledAuthoritativeFailure() {
        assertThat(IncidentPolicy.severity("MISSING_OBSERVED_OUTCOME")).isEqualTo("MEDIUM");
        assertThat(IncidentPolicy.severity("CONTRADICTORY_OUTCOMES")).isEqualTo("HIGH");
    }
    @Test void initialAndRepeatedTimelineKindsDiffer() {
        assertThat(IncidentPolicy.kind(true,"VIOLATED")).isEqualTo("DETECTED");
        assertThat(IncidentPolicy.kind(false,"VIOLATED")).isEqualTo("VIOLATION_REOBSERVED");
    }
}
