package com.aegisflow.simulator.order;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderSimulatorArchitectureTest {

    private static final List<String> FORBIDDEN_INTERNAL_PACKAGES = List.of(
            "com/aegisflow/core/",
            "com/aegisflow/simulator/payment/",
            "com/aegisflow/simulator/warehouse/");

    @Test
    void compiledClassesDoNotReferenceTheControlPlaneOrSiblingSimulators() throws IOException {
        Path classesRoot = Path.of(System.getProperty("user.dir"), "target", "classes");
        assertTrue(Files.isDirectory(classesRoot), () -> "Missing compiled main classes at " + classesRoot);

        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(classesRoot)) {
            for (Path classFile : paths.filter(path -> path.toString().endsWith(".class")).toList()) {
                String bytecode = Files.readString(classFile, StandardCharsets.ISO_8859_1);
                for (String forbiddenPackage : FORBIDDEN_INTERNAL_PACKAGES) {
                    if (bytecode.contains(forbiddenPackage)) {
                        violations.add(classesRoot.relativize(classFile) + " -> " + forbiddenPackage);
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> "Forbidden simulator dependencies: " + violations);
    }
}
