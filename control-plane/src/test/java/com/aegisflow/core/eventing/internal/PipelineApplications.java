package com.aegisflow.core.eventing.internal;

import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.mockito.Mockito.mock;

/** Test-only reflective assembly; no deployable imports another deployable's classes/artifact. */
final class PipelineApplications {
    static final String TENANT = "pipeline-a";
    static final String PASSWORD = "synthetic-pipeline-db-only";
    private static final Map<String,String> APPS = Map.of(
            "core","com.aegisflow.core.AegisFlowCoreApplication",
            "order","com.aegisflow.simulator.order.OrderSimulatorApplication",
            "payment","com.aegisflow.simulator.payment.PaymentSimulatorApplication",
            "warehouse","com.aegisflow.simulator.warehouse.WarehouseSimulatorApplication");

    @TestConfiguration(proxyBeanMethods = false)
    static class ManualPolling {
        @Bean TaskScheduler taskScheduler() { return mock(TaskScheduler.class); }
    }
    static ConfigurableApplicationContext start(String owner, PostgreSQLContainer database, CoreKafkaFixture kafka) throws Exception {
        String runtime = "aegisflow_" + owner + "_runtime", migrator = "aegisflow_" + owner + "_migrator";
        try (var connection = DriverManager.getConnection(database.getJdbcUrl(),database.getUsername(),database.getPassword());
             var sql = connection.createStatement()) {
            sql.execute("CREATE ROLE " + migrator + " LOGIN PASSWORD '" + PASSWORD + "'");
            sql.execute("CREATE ROLE " + runtime + " LOGIN PASSWORD '" + PASSWORD + "'");
            sql.execute("REVOKE ALL ON DATABASE " + database.getDatabaseName() + " FROM PUBLIC");
            sql.execute("GRANT CONNECT ON DATABASE " + database.getDatabaseName() + " TO " + runtime + "," + migrator);
            sql.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC");
            sql.execute("CREATE SCHEMA aegisflow AUTHORIZATION " + migrator);
        }
        var args = new ArrayList<String>(List.of(
                "--spring.config.location=optional:classpath:/pipeline-no-default-config.yaml",
                "--spring.profiles.active=postgres", "--spring.application.name=pipeline-" + owner,
                "--server.port=0",
                "--spring.jpa.hibernate.ddl-auto=validate", "--spring.jpa.properties.hibernate.default_schema=aegisflow",
                "--spring.jpa.open-in-view=false"));
        args.addAll(databaseArguments(owner,database.getJdbcUrl()));
        if (owner.equals("core")) {
            args.addAll(List.of("--aegisflow.core.inbox.enabled=true",
                    // JPA is test-only for simulator contexts; Core must retain its production JDBC manager.
                    "--spring.autoconfigure.exclude=org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
                    "--aegisflow.core.inbox.bootstrap-servers=" + kafka.bootstrap(),
                    "--aegisflow.core.inbox.password=" + kafka.password("core"),
                    "--aegisflow.core.inbox.group=aegisflow-core-startup-it", "--aegisflow.core.inbox.channel=pipeline-it",
                    "--aegisflow.core.inbox.order-tenants=" + TENANT, "--aegisflow.core.inbox.payment-tenants=" + TENANT + ",pipeline-payment-only",
                    "--aegisflow.core.inbox.warehouse-tenants=" + TENANT));
        } else {
            String prefix = "--aegisflow." + owner + ".relay.";
            args.addAll(List.of(prefix + "enabled=true",prefix + "allowed-tenants=" + TENANT,
                    prefix + "bootstrap-servers=" + kafka.bootstrap(),prefix + "password=" + kafka.password(owner),
                    prefix + "max-per-poll=10"));
        }
        return new SpringApplicationBuilder(Class.forName(APPS.get(owner)),ManualPolling.class)
                .web(WebApplicationType.NONE).run(args.toArray(String[]::new));
    }

    static List<String> databaseArguments(String owner, String mappedUrl) {
        String runtime = "aegisflow_" + owner + "_runtime", migrator = "aegisflow_" + owner + "_migrator";
        String module = owner.equals("core") ? "control-plane" : "simulators/" + owner + "-service";
        return List.of("--spring.datasource.url=" + mappedUrl, "--spring.datasource.username=" + runtime,
                "--spring.datasource.password=" + PASSWORD, "--spring.datasource.hikari.maximum-pool-size=4",
                "--spring.flyway.url=" + mappedUrl, "--spring.flyway.user=" + migrator,
                "--spring.flyway.password=" + PASSWORD, "--spring.flyway.schemas=aegisflow",
                "--spring.flyway.default-schema=aegisflow", "--spring.flyway.create-schemas=false",
                "--spring.flyway.placeholders.runtimeRole=" + runtime,
                "--spring.flyway.locations=filesystem:" + root().resolve(module + "/src/main/resources/db/migration").toString().replace('\\','/'));
    }

    static UUID create(ConfigurableApplicationContext app, String owner, String outcome) throws Exception {
        String pkg = "com.aegisflow.simulator." + owner;
        Class<?> commandClass = Class.forName(switch (owner) {
            case "order" -> pkg + ".persistence.CreateOrder";
            case "payment" -> pkg + ".CreatePaymentCommand";
            default -> pkg + ".reservation.CreateReservationCommand";
        });
        UUID resource = UUID.randomUUID();
        var values = new HashMap<String,Object>();
        values.put("tenantId",TENANT); values.put("idempotencyKey",resource.toString());
        values.put("orderId",owner.equals("order") ? resource : resource.toString());
        values.put("paymentId",resource); values.put("reservationId",resource);
        values.put("businessTransactionId","txn-" + resource); values.put("correlationId","corr-" + resource);
        values.put("amount",new BigDecimal("12.00")); values.put("currency","INR");
        if (owner.equals("order")) values.put("lines",List.of(Class.forName(pkg + ".persistence.CreateOrder$Line")
                .getConstructor(String.class,int.class).newInstance("sku-1",2)));
        if (owner.equals("payment")) {
            values.put("status",outcome.equals("negative") ? "DECLINED" : "AUTHORIZED");
            values.put("declineReason",outcome.equals("negative") ? "SIMULATED_DECLINE" : null);
        }
        if (owner.equals("warehouse")) {
            values.put("status",outcome.equals("negative") ? "REJECTED" : "RESERVED");
            values.put("rejectionReason",outcome.equals("negative") ? "OUT_OF_STOCK" : null);
            values.put("lines",List.of(Class.forName(pkg + ".reservation.ReservationLine")
                    .getConstructor(String.class,int.class).newInstance("sku-1",2)));
        }
        var components = commandClass.getRecordComponents();
        Object[] parameters = new Object[components.length]; Class<?>[] types = new Class<?>[components.length];
        for (int i=0;i<components.length;i++) {
            types[i] = components[i].getType(); Object value = values.get(components[i].getName());
            if (value != null && types[i].isEnum()) value = Enum.valueOf((Class)types[i],(String)value);
            parameters[i] = value;
        }
        Object command = commandClass.getConstructor(types).newInstance(parameters);
        Class<?> serviceClass = Class.forName(switch (owner) {
            case "order" -> pkg + ".persistence.OrderPersistenceService";
            case "payment" -> pkg + ".PaymentCreation";
            default -> pkg + ".reservation.ReservationApplicationService";
        });
        String method = switch(owner) { case "order" -> "create"; case "payment" -> "recordInitialOutcome"; default -> "createReservation"; };
        Object result = serviceClass.getMethod(method,commandClass).invoke(app.getBean(serviceClass),command);
        return (UUID)result.getClass().getMethod("eventId").invoke(result);
    }
    static void relay(ConfigurableApplicationContext app, String owner) throws Exception {
        Class<?> type = Class.forName("com.aegisflow.simulator." + owner
                + (owner.equals("warehouse") ? ".reservation." : owner.equals("payment") ? ".persistence.internal." : ".persistence.")
                + Character.toUpperCase(owner.charAt(0)) + owner.substring(1) + "OutboxRelay");
        var poll = type.getDeclaredMethod("poll"); poll.setAccessible(true);
        poll.invoke(app.getBean(type));
    }
    static Path root() {
        for (Path base = Path.of("").toAbsolutePath();base != null;base=base.getParent())
            if (Files.isRegularFile(base.resolve("compose.kafka.yaml"))) return base;
        throw new IllegalStateException("Repository root missing");
    }
}
