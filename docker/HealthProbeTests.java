public final class HealthProbeTests {
    public static void main(String[] args) {
        check(HealthProbe.healthy(200,"{\"status\":\"UP\"}"));
        check(HealthProbe.healthy(200,"{\"status\":\"UP\",\"groups\":[\"liveness\",\"readiness\"]}"));
        check(!HealthProbe.healthy(503,"{\"status\":\"UP\"}"));
        check(!HealthProbe.healthy(200,"{\"status\":\"DOWN\"}"));
        check(!HealthProbe.healthy(200,"{\"nested\":{\"status\":\"UP\"}}"));
        check(!HealthProbe.healthy(200,"x".repeat(4097)));
        System.out.println("PASS: 6 bounded health-response checks");
    }
    private static void check(boolean value) { if(!value) throw new AssertionError("Health probe regression"); }
}
