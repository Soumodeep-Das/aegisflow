import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/** JRE-only bounded health probe: process/database health, never business readiness. */
public final class HealthProbe {
    static boolean healthy(int status, String body) {
        return status==200 && body.length()<=4096 && body.matches("(?s)\\s*\\{\\s*\"status\"\\s*:\\s*\"UP\"(?:\\s*,.*|\\s*\\})\\s*");
    }
    public static void main(String[] args) {
        if(args.length!=1) System.exit(2);
        try {
            var connection=(HttpURLConnection)URI.create(args[0]).toURL().openConnection();
            connection.setConnectTimeout(1500); connection.setReadTimeout(1500);
            connection.setInstanceFollowRedirects(false);
            try {
                int status=connection.getResponseCode();
                if(status!=200) System.exit(1);
                try(var stream=connection.getInputStream()) {
                    if(!healthy(status,new String(stream.readNBytes(4097),StandardCharsets.UTF_8))) System.exit(1);
                }
            } finally { connection.disconnect(); }
        } catch(Exception unavailable) { System.exit(1); }
    }
}
