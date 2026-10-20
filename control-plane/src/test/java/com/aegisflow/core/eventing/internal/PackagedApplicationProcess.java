package com.aegisflow.core.eventing.internal;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.json.JsonMapper;

/** Owns only the Process returned by its own ProcessBuilder; never searches for or kills foreign PIDs. */
final class PackagedApplicationProcess implements AutoCloseable {
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).build();
    private static final Pattern PORT = Pattern.compile("Tomcat started on port ([0-9]+) ");
    private static final class Transport {
        // Pure argument/parser checks must not initialize a selector or require loopback access.
        static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }
    private final Process process;
    final Path log;

    private PackagedApplicationProcess(Process process, Path log) { this.process=process; this.log=log; }

    static PackagedApplicationProcess start(Path jar, Path log, Map<String,String> ownEnvironment) throws IOException {
        if (!Files.isRegularFile(jar)) throw new IllegalStateException("Build the current executable JAR before this gate: " + jar);
        Path java = Path.of(System.getProperty("java.home"),"bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        var builder=new ProcessBuilder(command(java,jar)).directory(PipelineApplications.root().toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile());
        Map<String,String> filtered=isolatedEnvironment(builder.environment());
        builder.environment().clear(); builder.environment().putAll(filtered); builder.environment().putAll(ownEnvironment);
        return new PackagedApplicationProcess(builder.start(),log);
    }
    static List<String> command(Path java,Path jar) {
        return List.of(java.toString(),"-Duser.timezone=UTC","-jar",jar.toString(),"--spring.profiles.active=postgres",
                "--server.address=127.0.0.1","--server.port=0","--spring.main.banner-mode=off");
    }
    static Map<String,String> isolatedEnvironment(Map<String,String> inherited) {
        Map<String,String> result=new HashMap<>();
        Set<String> allowed=Set.of("PATH","SYSTEMROOT","WINDIR","SYSTEMDRIVE","COMSPEC","OS",
                "PROCESSOR_ARCHITECTURE","NUMBER_OF_PROCESSORS","TEMP","TMP","USERPROFILE","LOCALAPPDATA",
                "APPDATA","PROGRAMDATA","JAVA_HOME","LANG","LC_ALL","LC_CTYPE");
        inherited.forEach((key,value) -> {
            String upper=key.toUpperCase(Locale.ROOT);
            if (allowed.contains(upper)) result.put(key,value);
        });
        return result;
    }
    static OptionalInt httpPort(String log) {
        var match=PORT.matcher(log);
        if (!match.find()) return OptionalInt.empty();
        try {
            int port=Integer.parseInt(match.group(1));
            return port>=1024 && port<=65535 ? OptionalInt.of(port) : OptionalInt.empty();
        } catch (NumberFormatException invalid) { return OptionalInt.empty(); }
    }
    static boolean healthyResponse(int status,String body) {
        if (status!=200 || body==null || body.length()>8192) return false;
        try (var parser=JSON.createParser(body)) {
            var root=JSON.readTree(parser);
            if (root==null || !root.isObject() || parser.nextToken()!=null || root.get("status")==null
                    || !root.get("status").isString() || !root.get("status").stringValue().equals("UP")) return false;
            for (var entry:root.properties()) {
                if (entry.getKey().equals("groups")) {
                    if (!entry.getValue().isArray()) return false;
                    for (var group:entry.getValue()) if (!group.isString()) return false;
                } else if (!entry.getKey().equals("status")) return false;
            }
            return true;
        } catch (RuntimeException invalid) { return false; }
    }
    boolean healthy() throws InterruptedException {
        if (!process.isAlive()) throw new IllegalStateException("Owned packaged application exited; inspect " + log);
        try {
            if (!Files.isRegularFile(log)) return false;
            var port=httpPort(Files.readString(log));
            if (port.isEmpty()) return false;
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port.getAsInt()+"/actuator/health"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            var response=Transport.HTTP.send(request,HttpResponse.BodyHandlers.ofString());
            return healthyResponse(response.statusCode(),response.body());
        } catch (IOException notReady) { return false; }
    }
    boolean alive() { return process.isAlive(); }
    @Override public void close() {
        boolean interrupted=Thread.interrupted();
        try {
            if (process.isAlive()) {
                process.destroy();
                try {
                    if (!process.waitFor(15,TimeUnit.SECONDS)) {
                        process.destroyForcibly(); process.waitFor(5,TimeUnit.SECONDS);
                    }
                } catch (InterruptedException failure) { interrupted=true; process.destroyForcibly(); }
            }
        } finally { if(interrupted) Thread.currentThread().interrupt(); }
    }
}
