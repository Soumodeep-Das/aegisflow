package com.aegisflow.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * Bootstrap entry point for the AegisFlow control plane.
 *
 * <p>The control plane is one deployable with explicit Spring Modulith boundaries. Business behavior is added
 * behind those boundaries in later P0 milestones.</p>
 */
@Modulithic(systemName = "AegisFlow Control Plane")
@SpringBootApplication
public class AegisFlowCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(AegisFlowCoreApplication.class, args);
    }
}
