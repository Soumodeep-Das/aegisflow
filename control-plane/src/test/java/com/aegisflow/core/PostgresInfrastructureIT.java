package com.aegisflow.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Infrastructure connectivity only; business schema and migrations begin in P0.2. */
@Testcontainers(disabledWithoutDocker = true)
class PostgresInfrastructureIT {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            "postgres:18.6@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336")
            .withDatabaseName("foundation")
            .withUsername("foundation")
            .withPassword("synthetic-test-only");

    @Test
    void jdbcAndFlywayConnectWithoutBusinessMigrations() throws Exception {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement();
             var result = statement.executeQuery("select 1")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        }

        var flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .failOnMissingLocations(false)
                .load();
        assertThat(flyway.info().all()).isEmpty();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }
}
