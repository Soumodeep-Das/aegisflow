public final class HealthProbeTests {
    public static void main(String[] args) {
        check(HealthProbe.healthy(200,"{\"status\":\"UP\"}"));
        check(HealthProbe.healthy(200,"{\"status\":\"UP\",\"groups\":[\"liveness\",\"readiness\"]}"));
        check(!HealthProbe.healthy(503,"{\"status\":\"UP\"}"));
        check(!HealthProbe.healthy(200,"{\"status\":\"DOWN\"}"));
        check(!HealthProbe.healthy(200,"{\"nested\":{\"status\":\"UP\"}}"));
        check(!HealthProbe.healthy(200,"x".repeat(4097)));
        check(HealthProbe.healthy(200,"{\"groups\":[\"liveness\",\"readiness\"],\"status\":\"UP\"}"));
        check(HealthProbe.healthy(200," { \"groups\" : [], \"status\" : \"UP\" } "));
        check(!HealthProbe.healthy(200,"{\"groups\":[],\"status\":\"DOWN\"}"));
        check(!HealthProbe.healthy(200,"{\"status\":\"UP\",\"status\":\"DOWN\"}"));
        check(!HealthProbe.healthy(200,"{\"status\":\"UP\","));
        check(!HealthProbe.healthy(200,"{\"groups\":[{\"status\":\"UP\"}],\"status\":\"DOWN\"}"));
        check(!HealthProbe.healthy(200,"{\"status\":\"UP\"} garbage"));
        System.out.println("PASS: 13 bounded health-response checks");
    }
    private static void check(boolean value) { if(!value) throw new AssertionError("Health probe regression"); }
}
