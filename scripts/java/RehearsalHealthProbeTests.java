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
        check("Actuator probe groups with hidden details", true, 200,
                "{\"status\":\"UP\",\"groups\":[\"liveness\",\"readiness\"]}");
        check("Actuator groups before root status", true, 200,
                "{\"groups\":[\"liveness\",\"readiness\"],\"status\":\"UP\"}");
        check("group array whitespace and empty groups", true, 200,
                "{ \"groups\" : [ \n ], \"status\" : \"UP\" }");
        check("non-success HTTP", false, 503, "{\"status\":\"UP\"}");
        check("redirect is not readiness", false, 302, "{\"status\":\"UP\"}");
        check("unhealthy application", false, 200, "{\"status\":\"DOWN\"}");
        check("nested healthy component cannot mask root failure", false, 200,
                "{\"status\":\"DOWN\",\"component\":{\"status\":\"UP\"}}");
        check("duplicate status is rejected", false, 200, "{\"status\":\"DOWN\",\"status\":\"UP\"}");
        check("duplicate UP status is rejected", false, 200, "{\"status\":\"UP\",\"status\":\"UP\"}");
        check("duplicate groups is rejected", false, 200,
                "{\"status\":\"UP\",\"groups\":[],\"groups\":[]}");
        check("groups cannot hide root DOWN", false, 200,
                "{\"groups\":[\"liveness\",\"readiness\"],\"status\":\"DOWN\"}");
        check("nested group status is rejected", false, 200,
                "{\"groups\":[{\"status\":\"UP\"}],\"status\":\"DOWN\"}");
        check("group trailing comma is rejected", false, 200,
                "{\"status\":\"UP\",\"groups\":[\"liveness\",]}");
        check("trailing JSON is rejected", false, 200, "{\"status\":\"UP\"}{}");
        check("root array is rejected", false, 200, "[{\"status\":\"UP\"}]");
        check("non-JSON whitespace is rejected", false, 200, "\f{\"status\":\"UP\"}");
        check("internal details remain forbidden", false, 200,
                "{\"status\":\"UP\",\"components\":{\"db\":{\"status\":\"UP\"}}}");
        check("malformed body", false, 200, "not JSON");
        check("missing body", false, 200, null);
        check("oversized body", false, 200, " ".repeat(4097));
        if (!RehearsalHealthProbe.responseSummary(200, "private-details".getBytes(StandardCharsets.UTF_8))
                .equals("HTTP 200/unexpected-health-body")
                || !RehearsalHealthProbe.responseSummary(503, null).equals("HTTP 503/not-ready")
                || !RehearsalHealthProbe.responseSummary(200, "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8))
                        .equals("HTTP 200/UP")) {
            throw new AssertionError("fixed diagnostics must not include response content");
        }
        passed++;
        System.out.println("PASS: fixed safe response diagnostics");
        System.out.println("Rehearsal health probe tests: passed=" + passed + ", failures=0; no HTTP or Docker used.");
    }
}
