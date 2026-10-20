/**
 * Correlates tenant-scoped facts into business-transaction timelines and projections.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Correlation",
        allowedDependencies = {"eventing"})
package com.aegisflow.core.correlation;
