import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/** JRE-only bounded health probe: process/database health, never business readiness. */
public final class HealthProbe {
    // Only the details-disabled Actuator contract: status plus optional string-array groups.
    // JSON object field order is immaterial; nested or duplicate statuses must not pass.
    private static final String STRING="\"(?:[^\"\\\\\\x00-\\x1f]|\\\\(?:[\"\\\\/bfnrt]|u[0-9a-fA-F]{4}))*\"";
    private static final String GROUPS="\"groups\"\\s*:\\s*\\[\\s*(?:"+STRING+"(?:\\s*,\\s*"+STRING+")*)?\\s*\\]";
    private static final String UP="\"status\"\\s*:\\s*\"UP\"";
    static boolean healthy(int status, String body) {
        return status==200 && body.length()<=4096 && body.matches(
                "\\s*\\{\\s*(?:"+UP+"(?:\\s*,\\s*"+GROUPS+")?|"+GROUPS+"\\s*,\\s*"+UP+")\\s*\\}\\s*");
    }
    public static void main(String[] args) {
        if(args.length!=1) System.exit(2);
        try {
            var connection=(HttpURLConnection)URI.create(args[0]).toURL().openConnection();
            connection.setConnectTimeout(1500); connection.setReadTimeout(1500);
            connection.setInstanceFollowRedirects(false);
            try {
                int status=connection.getResponseCode();
                if(status!=200) {
                    System.err.println("Health HTTP status: " + status);
                    System.exit(1);
                }
                try(var stream=connection.getInputStream()) {
                    String body=new String(stream.readNBytes(4097),StandardCharsets.UTF_8);
                    if(!healthy(status,body)) {
                        // Health details are disabled by application policy; retain the bounded
                        // response in Docker health history to distinguish parsing from DOWN.
                        System.err.println("Health response rejected: " + body);
                        System.exit(1);
                    }
                }
            } finally { connection.disconnect(); }
        } catch(Exception unavailable) {
            System.err.println("Health request failed: " + unavailable.getClass().getSimpleName());
            System.exit(1);
        }
    }
}
