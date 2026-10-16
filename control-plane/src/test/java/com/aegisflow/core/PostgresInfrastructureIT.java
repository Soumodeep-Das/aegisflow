package com.aegisflow.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** SAFE-001: administration identities never enter Core's runtime DataSource. */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PostgresInfrastructureIT {

    private static final String RUNTIME = "aegisflow_core_runtime";
    private static final String MIGRATOR = "aegisflow_core_migrator";
    private static final String PASSWORD = "synthetic-test-only";

    // Declare the image family explicitly while retaining the immutable tag/digest reference.
    static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse(
            "postgres:18.6@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336")
            .asCompatibleSubstituteFor("postgres");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE)
            .withDatabaseName("aegisflow_core")
            .withUsername("inspection_admin")
            .withPassword(PASSWORD);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry properties) throws SQLException {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + MIGRATOR + " LOGIN PASSWORD '" + PASSWORD + "'");
            statement.execute("CREATE ROLE " + RUNTIME + " LOGIN PASSWORD '" + PASSWORD + "'");
            statement.execute("REVOKE ALL ON DATABASE aegisflow_core FROM PUBLIC");
            statement.execute("GRANT CONNECT ON DATABASE aegisflow_core TO " + MIGRATOR + ", " + RUNTIME);
            statement.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC");
            statement.execute("CREATE SCHEMA aegisflow AUTHORIZATION " + MIGRATOR);
            statement.execute("GRANT USAGE ON SCHEMA aegisflow TO " + RUNTIME);
            statement.execute("CREATE DATABASE simulator_owned");
            statement.execute("REVOKE ALL ON DATABASE simulator_owned FROM PUBLIC");
        }
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", () -> RUNTIME);
        properties.add("spring.datasource.password", () -> PASSWORD);
        properties.add("spring.flyway.user", () -> MIGRATOR);
        properties.add("spring.flyway.password", () -> PASSWORD);
        properties.add("spring.flyway.placeholders.runtimeRole", () -> RUNTIME);
    }

    @Autowired
    private Flyway flyway;

    @LocalServerPort
    private int port;

    @Test
    void independentMigrationHistoryValidates() {
        assertThat(flyway.info().applied()).hasSize(1);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void postgresApplicationStartsAndServesHealthyHttp() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health"))
                .timeout(Duration.ofSeconds(5)).GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void runtimeCannotCreateTablesOrModifyMigrationHistory() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), RUNTIME, PASSWORD);
             var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.execute("CREATE TABLE aegisflow.forbidden(id integer)"))
                    .isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("42501");
            assertThatThrownBy(() -> statement.execute("DELETE FROM aegisflow.flyway_schema_history"))
                    .isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("42501");
        }
    }

    @Test
    void coreRuntimeCannotConnectToSimulatorOwnedDatabase() {
        String simulatorUrl = POSTGRES.getJdbcUrl().replace("/aegisflow_core", "/simulator_owned");
        assertThatThrownBy(() -> DriverManager.getConnection(simulatorUrl, RUNTIME, PASSWORD))
                .isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("42501");
    }
}
