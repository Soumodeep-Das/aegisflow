package com.aegisflow.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.testcontainers.utility.DockerImageName;

class PostgresImageConfigurationTests {
    @Test
    void actualContainerDeclarationInitializesWithoutDockerAndPreservesImmutableReference() {
        // Access initializes the IT's real static PostgreSQLContainer, but never starts it.
        var image = PostgresInfrastructureIT.POSTGRES_IMAGE;
        assertThat(image.asCanonicalNameString()).isEqualTo(
                "postgres:18.6@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336");
        assertThatCode(() -> image.assertCompatibleWith(DockerImageName.parse("postgres")))
                .doesNotThrowAnyException();
    }
}
