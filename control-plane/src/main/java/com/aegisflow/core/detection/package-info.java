/**
 * Evaluates versioned business invariants and their contract deadlines.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Detection",
        allowedDependencies = {"correlation", "eventing"})
package com.aegisflow.core.detection;
