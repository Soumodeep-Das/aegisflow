package com.aegisflow.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

class ControlPlaneArchitectureTests {

    private static final Set<String> EXPECTED_MODULES = Set.of(
            "audit",
            "correlation",
            "detection",
            "eventing",
            "evidence",
            "identity",
            "investigation",
            "operations",
            "recovery",
            "verification");

    private static final String[] SIMULATOR_PACKAGES = {
            "com.aegisflow.simulator..",
            "com.aegisflow.simulators..",
            "com.aegisflow.order..",
            "com.aegisflow.payment..",
            "com.aegisflow.warehouse.."
    };

    private static final ApplicationModules MODULES =
            ApplicationModules.of(AegisFlowCoreApplication.class);

    @Test
    void verifiesModuleStructure() {
        MODULES.verify();
    }

    @Test
    void discoversExactlyTheDocumentedModules() {
        var discoveredModules = MODULES.stream()
                .map(module -> module.getIdentifier().toString())
                .collect(Collectors.toUnmodifiableSet());

        assertThat(discoveredModules)
                .containsExactlyInAnyOrderElementsOf(EXPECTED_MODULES)
                .hasSize(10);
    }

    @Test
    void modulesAllowOnlyTheDocumentedProjectionIntakeDependency() throws ClassNotFoundException {
        for (var moduleName : EXPECTED_MODULES) {
            var markerType = Class.forName("com.aegisflow.core." + moduleName + ".FoundationModule");
            var moduleMetadata = markerType.getPackage().getAnnotation(ApplicationModule.class);

            assertThat(moduleMetadata)
                    .as("%s must declare @ApplicationModule", moduleName)
                    .isNotNull();
            assertThat(moduleMetadata.allowedDependencies())
                    .as("%s must expose only its documented dependencies", moduleName)
                    .containsExactly(moduleName.equals("correlation") ? new String[]{"eventing"}
                        : moduleName.equals("detection") ? new String[]{"correlation","eventing"} : new String[]{});
        }

        MODULES.forEach(module -> assertThat(module.getAllowedDependencies(MODULES).isEmpty())
                .as("%s has the expected dependency policy", module.getIdentifier())
                .isEqualTo(!Set.of("correlation","detection").contains(module.getIdentifier().toString())));
    }

    @Test
    void controlPlaneDoesNotReferenceSimulatorCode() {
        var controlPlaneClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.aegisflow.core");

        noClasses()
                .that().resideInAPackage("com.aegisflow.core..")
                .should().dependOnClassesThat().resideInAnyPackage(SIMULATOR_PACKAGES)
                .because("the control plane may integrate with simulators only through versioned APIs and events")
                .check(controlPlaneClasses);
    }
}
