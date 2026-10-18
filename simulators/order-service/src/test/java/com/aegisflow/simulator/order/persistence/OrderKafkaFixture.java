package com.aegisflow.simulator.order.persistence;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** Test-only broker: reuses the production-local scripts, never an existing Compose container. */
final class OrderKafkaFixture extends GenericContainer<OrderKafkaFixture> {
    static final String IMAGE = "apache/kafka:4.2.2@sha256:1213eb3943d551e5ed1fca7a4e109001cee35770b66a02a0c37a8964efe09b69";
    private static final String LABEL = "aegisflow.order-kafka-test";
    private final String owner = UUID.randomUUID().toString();
    private final Map<String, String> passwords = credentials();
    private int publishedPort;

    OrderKafkaFixture(Network network) {
        super(DockerImageName.parse(IMAGE));
        withNetwork(network).withNetworkAliases("kafka").withLabel(LABEL, owner).withExposedPorts(19092);
        withEnv("KAFKA_CLUSTER_ID", "MkU3OEVBNTcwNTJENDM2Qk");
        withEnv("KAFKA_HEAP_OPTS", "-Xms256m -Xmx512m");
        withEnv("LOG_DIR", "/tmp/aegisflow-kafka/logs");
        passwords.forEach((principal, password) -> withEnv("KAFKA_" + principal.toUpperCase(java.util.Locale.ROOT) + "_PASSWORD", password));
        for (String file : new String[]{"common.sh", "startup.sh", "healthcheck.sh", "provision.sh", "server.properties"})
            withCopyFileToContainer(MountableFile.forHostPath(scriptDirectory().resolve(file), 0444), "/opt/aegisflow/kafka/" + file);
        // Docker chooses the host port first; containerIsStarting supplies it before broker bootstrap.
        // This fixture has no /tmp tmpfs: the file survives restart of this SAME container.
        withCommand("-ec", """
                n=0
                until [ -r /tmp/aegisflow-test-host-port ]; do
                  n=$((n+1)); [ "$n" -lt 120 ] || exit 1; sleep 1
                done
                export KAFKA_HOST_PORT=$(cat /tmp/aegisflow-test-host-port)
                exec /bin/sh /opt/aegisflow/kafka/startup.sh
                """);
        withCreateContainerCmdModifier(cmd -> {
            cmd.withEntrypoint("/bin/sh").withUser("1000:1000");
            cmd.getHostConfig().withPortBindings(new PortBinding(Ports.Binding.bindIp("127.0.0.1"), ExposedPort.tcp(19092)));
        });
        waitingFor(Wait.forSuccessfulCommand("/bin/sh /opt/aegisflow/kafka/healthcheck.sh")
                .withStartupTimeout(Duration.ofSeconds(90)));
    }

    @Override protected void containerIsStarting(InspectContainerResponse response) {
        if (!getHost().equals("localhost") && !getHost().equals("127.0.0.1"))
            throw new IllegalStateException("This isolated fixture requires local Docker, not a remote daemon");
        publishedPort = getMappedPort(19092);
        copyFileToContainer(Transferable.of(Integer.toString(publishedPort), 0444), "/tmp/aegisflow-test-host-port");
    }

    @Override protected void containerIsStarted(InspectContainerResponse response) {
        try {
            var result = execInContainer("/bin/sh", "/opt/aegisflow/kafka/provision.sh");
            if (result.getExitCode() != 0) throw new IllegalStateException("Kafka fixture provisioning failed");
        } catch (Exception failure) {
            throw new IllegalStateException("Kafka fixture provisioning failed; no credential/output dump");
        }
    }

    String bootstrap() { return "127.0.0.1:" + publishedPort; }
    String orderPassword() { return passwords.get("order"); }

    Map<String, Object> coreProperties() {
        var props = new HashMap<String, Object>();
        props.put("bootstrap.servers", bootstrap());
        props.put("security.protocol", "SASL_PLAINTEXT");
        props.put("sasl.mechanism", "PLAIN");
        props.put("sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"core\" password=\"" + passwords.get("core") + "\";");
        return props;
    }

    void stopBrokerForOutage() {
        assertOwned();
        getDockerClient().stopContainerCmd(getContainerId()).withTimeout(10).exec();
        if (Boolean.TRUE.equals(getDockerClient().inspectContainerCmd(getContainerId()).exec().getState().getRunning()))
            throw new IllegalStateException("Owned broker did not stop");
    }

    void restartSameBroker() {
        assertOwned();
        getDockerClient().startContainerCmd(getContainerId()).exec();
        var actual = getDockerClient().inspectContainerCmd(getContainerId()).exec()
                .getNetworkSettings().getPorts().getBindings().get(ExposedPort.tcp(19092));
        if (actual == null || actual.length != 1 || Integer.parseInt(actual[0].getHostPortSpec()) != publishedPort)
            throw new IllegalStateException("Restart changed the fixture's advertised port");
        Wait.forSuccessfulCommand("/bin/sh /opt/aegisflow/kafka/healthcheck.sh")
                .withStartupTimeout(Duration.ofSeconds(90)).waitUntilReady(this);
    }

    private void assertOwned() {
        var info = getDockerClient().inspectContainerCmd(getContainerId()).exec();
        if (!getContainerId().equals(info.getId()) || !owner.equals(info.getConfig().getLabels().get(LABEL)))
            throw new IllegalStateException("Refusing to stop/start a foreign container");
    }

    static Path scriptDirectory() {
        // Surefire/Failsafe work in the Order module; direct IDE invocation can use the repository root.
        for (Path base = Path.of("").toAbsolutePath(); base != null; base = base.getParent()) {
            Path scripts = base.resolve("scripts/kafka");
            if (Files.isRegularFile(base.resolve("compose.kafka.yaml")) && Files.isRegularFile(scripts.resolve("startup.sh")))
                return scripts;
        }
        throw new IllegalStateException("Existing Kafka scripts were not found");
    }

    private static Map<String, String> credentials() {
        var result = new HashMap<String, String>();
        var random = new java.security.SecureRandom();
        for (String principal : new String[]{"broker", "admin", "order", "payment", "warehouse", "core"}) {
            byte[] bytes = new byte[32]; random.nextBytes(bytes);
            result.put(principal, HexFormat.of().formatHex(bytes));
        }
        return Map.copyOf(result);
    }
}
