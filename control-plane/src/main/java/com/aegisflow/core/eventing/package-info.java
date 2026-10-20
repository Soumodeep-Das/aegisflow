/**
 * Validates incoming contracts and owns durable inbox/quarantine acceptance and Kafka offsets.
 * Business correlation and detection remain separate later modules.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Eventing",
        allowedDependencies = {})
package com.aegisflow.core.eventing;
