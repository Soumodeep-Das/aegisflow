import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Test-harness health check executed inside the owning application container.
 * No host port, shell utilities, external library or database credential is needed.
 */
public final class RehearsalHealthProbe {
    private static final int MAX_BYTES = 4096;
    private static final String WS = "[ \\t\\r\\n]*";
    private static final String STATUS = "\"status\"" + WS + ":" + WS + "\"UP\"";
    private static final String GROUP_NAME = "\"[A-Za-z0-9_-]+\"";
    private static final String GROUPS = "\"groups\"" + WS + ":" + WS + "\\[" + WS
            + "(?:" + GROUP_NAME + "(?:" + WS + "," + WS + GROUP_NAME + ")*)?" + WS + "\\]";
    // Actuator includes group names when probes are enabled, even with details
    // hidden. Accept that bounded, detail-free shape in either member order;
    // do not mistake a nested/duplicate status or substring for root readiness.
    private static final Pattern HEALTH_UP = Pattern.compile(
            "\\A" + WS + "\\{" + WS + "(?:" + STATUS + "(?:" + WS + "," + WS + GROUPS
                    + ")?|" + GROUPS + WS + "," + WS + STATUS + ")" + WS + "}" + WS + "\\z");

    private RehearsalHealthProbe() { }

    static boolean isHealthy(int status, byte[] body) {
        return status == 200 && body != null && body.length <= MAX_BYTES
                && HEALTH_UP.matcher(new String(body, StandardCharsets.UTF_8)).matches();
    }

    static String responseSummary(int status, byte[] body) {
        if (isHealthy(status, body)) { return "HTTP 200/UP"; }
        return status == 200 ? "HTTP 200/unexpected-health-body" : "HTTP " + status + "/not-ready";
    }

    public static void main(String[] args) {
        try (var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080/actuator/health"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            byte[] body;
            try (var stream = response.body()) {
                body = stream.readNBytes(MAX_BYTES + 1);
            }
            String summary = responseSummary(response.statusCode(), body);
            // Fixed categories only: never print response bodies or credentials.
            System.out.println(summary);
            if (!isHealthy(response.statusCode(), body)) {
                System.exit(1);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            System.out.println("probe/interrupted");
            System.exit(1);
        } catch (Exception unavailable) {
            // The orchestrator retries readiness within its deadline and saves
            // redacted application logs; a failed probe never publishes payloads.
            System.out.println("probe/connection-failed");
            System.exit(1);
        }
    }
}
