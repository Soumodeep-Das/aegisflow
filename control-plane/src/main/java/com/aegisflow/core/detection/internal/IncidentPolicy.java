package com.aegisflow.core.detection.internal;

/** No recovery lifecycle: one still-open episode per pinned workflow contract in P0.4. */
final class IncidentPolicy {
    static String episode(String invariant,int version,String contract,int contractVersion,String configuration) {
        return invariant+":"+version+":"+contract+":"+contractVersion+":"+configuration;
    }
    static boolean opens(String result) { return "VIOLATED".equals(result); }
    static String severity(String reason) {
        return "MISSING_OBSERVED_OUTCOME".equals(reason) || "OUTCOME_AFTER_DEADLINE".equals(reason) ? "MEDIUM" : "HIGH";
    }
    static String kind(boolean created,String result) {
        return created ? "DETECTED" : opens(result) ? "VIOLATION_REOBSERVED" : "EVIDENCE_CHANGED";
    }
}
