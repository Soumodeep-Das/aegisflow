import java.nio.charset.StandardCharsets;

/** Infrastructure-free contract tests; these do not claim an HTTP/Docker pass. */
public final class RehearsalHealthProbeTests {
    private static int passed;

    private static void check(String label, boolean expected, int status, String body) {
        byte[] bytes = body == null ? null : body.getBytes(StandardCharsets.UTF_8);
        if (RehearsalHealthProbe.isHealthy(status, bytes) != expected) {
            throw new AssertionError(label);
        }
        passed++;
        System.out.println("PASS: " + label);
    }

    public static void main(String[] args) {
        check("exact healthy endpoint", true, 200, "{\"status\":\"UP\"}");
        check("JSON whitespace", true, 200, " \n{ \"status\" : \"UP\" }\n");
        check("non-success HTTP", false, 503, "{\"status\":\"UP\"}");
        check("redirect is not readiness", false, 302, "{\"status\":\"UP\"}");
        check("unhealthy application", false, 200, "{\"status\":\"DOWN\"}");
        check("nested healthy component cannot mask root failure", false, 200,
                "{\"status\":\"DOWN\",\"component\":{\"status\":\"UP\"}}");
        check("duplicate status is rejected", false, 200, "{\"status\":\"DOWN\",\"status\":\"UP\"}");
        check("malformed body", false, 200, "not JSON");
        check("missing body", false, 200, null);
        check("oversized body", false, 200, " ".repeat(4097));
        System.out.println("Rehearsal health probe tests: passed=" + passed + ", failures=0; no HTTP or Docker used.");
    }
}
