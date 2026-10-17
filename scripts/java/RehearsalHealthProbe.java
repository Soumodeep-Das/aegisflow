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
    private static final Pattern HEALTH_UP = Pattern.compile(
            "\\A\\s*\\{\\s*\"status\"\\s*:\\s*\"UP\"\\s*}\\s*\\z");

    private RehearsalHealthProbe() { }

    static boolean isHealthy(int status, byte[] body) {
        return status == 200 && body != null && body.length <= MAX_BYTES
                && HEALTH_UP.matcher(new String(body, StandardCharsets.UTF_8)).matches();
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
            if (!isHealthy(response.statusCode(), body)) {
                System.exit(1);
            }
            System.out.println("HTTP 200/UP");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            System.exit(1);
        } catch (Exception unavailable) {
            // The orchestrator retries readiness within its deadline and saves
            // redacted application logs; a failed probe never publishes payloads.
            System.exit(1);
        }
    }
}
