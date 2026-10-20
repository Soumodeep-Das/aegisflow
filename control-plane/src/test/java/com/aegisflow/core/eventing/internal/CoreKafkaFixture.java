package com.aegisflow.core.eventing.internal;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
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
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** Test-only broker: reuses the production-local scripts, never an existing Compose container. */
final class CoreKafkaFixture extends GenericContainer<CoreKafkaFixture> {
    static final String IMAGE = "apache/kafka:4.2.2@sha256:1213eb3943d551e5ed1fca7a4e109001cee35770b66a02a0c37a8964efe09b69";
    private static final String LABEL = "aegisflow.core-pipeline-test";
    private final String owner = UUID.randomUUID().toString();
    private final Map<String, String> passwords = credentials();
    private int publishedPort;

    CoreKafkaFixture(Network network) {
        super(DockerImageName.parse(IMAGE));
        withNetwork(network).withNetworkAliases("kafka").withLabel(LABEL, owner).withExposedPorts(19092);
        withEnv("KAFKA_CLUSTER_ID", "MkU3OEVBNTcwNTJENDM2Qk");
        withEnv("KAFKA_HEAP_OPTS", "-Xms256m -Xmx512m");
        withEnv("LOG_DIR", "/tmp/aegisflow-kafka/logs");
        passwords.forEach((principal, password) -> withEnv("KAFKA_" + principal.toUpperCase(java.util.Locale.ROOT) + "_PASSWORD", password));
        for (String file : new String[]{"common.sh", "startup.sh", "healthcheck.sh", "provision.sh", "server.properties"})
            withCopyFileToContainer(MountableFile.forHostPath(scriptDirectory().resolve(file), 0444), "/opt/aegisflow/kafka/" + file);
        withCommand("/opt/aegisflow/kafka/startup.sh");
        withCreateContainerCmdModifier(cmd -> {
            cmd.withEntrypoint("/bin/sh").withUser("1000:1000");
            cmd.getHostConfig().withPortBindings(loopbackBinding(publishedPort));
        });
        waitingFor(Wait.forSuccessfulCommand("/bin/sh /opt/aegisflow/kafka/healthcheck.sh")
                .withStartupTimeout(Duration.ofSeconds(90)));
    }

    @Override protected void configure() {
        super.configure();
        if (!getHost().equals("localhost") && !getHost().equals("127.0.0.1"))
            throw new IllegalStateException("This isolated fixture requires local Docker, not a remote daemon");
        // Select a free local port, then request that exact binding for this container's lifetime.
        // A blank HostPort lets Docker choose again on restart. A selection/bind race fails startup;
        // we never stop a foreign listener to reclaim the port.
        try (var socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            publishedPort = socket.getLocalPort();
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot select isolated Kafka loopback port", failure);
        }
        withEnv("KAFKA_HOST_PORT", Integer.toString(publishedPort));
    }

    static PortBinding loopbackBinding(int port) {
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("Explicit unprivileged host port required");
        return new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1", port), ExposedPort.tcp(19092));
    }

    @Override protected void containerIsStarting(InspectContainerResponse response) {
        if (getMappedPort(19092) != publishedPort)
            throw new IllegalStateException("Initial binding differs from the fixture's advertised port");
    }

    @Override protected void containerIsStarted(InspectContainerResponse response) {
        try {
            // Retain the 0444 source fixture: real startup must repair only its private copy.
            var modes = execInContainer("/bin/sh", "-ec",
                    "test \"$(stat -c %a /opt/aegisflow/kafka/server.properties)\" = 444 && "
                    + "test \"$(stat -c %a /tmp/aegisflow-kafka/server.properties)\" = 600");
            if (modes.getExitCode() != 0) throw new IllegalStateException("Kafka fixture configuration mode check failed");
            var result = execInContainer("/bin/sh", "/opt/aegisflow/kafka/provision.sh");
            if (result.getExitCode() != 0) throw new IllegalStateException("Kafka fixture provisioning failed");
        } catch (Exception failure) {
            throw new IllegalStateException("Kafka fixture provisioning failed; no credential/output dump");
        }
    }

    String bootstrap() { return "127.0.0.1:" + publishedPort; }
    String password(String principal) { return passwords.get(principal); }

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
