/**
 * Validates incoming event contracts and will own durable inbox and quarantine behavior.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Eventing",
        allowedDependencies = {})
package com.aegisflow.core.eventing;
