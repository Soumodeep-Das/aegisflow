# Same official Java 21 digest already used by the verified PostgreSQL rehearsal.
FROM eclipse-temurin:21-jre-jammy@sha256:8d584faeef02c22f6b58ad8a3cee8e1e53ae52bdf2224f4afb989cc35983b8ae
ARG APP_OWNER
ARG SOURCE_REVISION
LABEL org.opencontainers.image.title="aegisflow-${APP_OWNER}" \
      org.opencontainers.image.revision="${SOURCE_REVISION}" \
      com.aegisflow.owner="${APP_OWNER}"
WORKDIR /app
# Build input is a dedicated four-file staging directory, never the workspace.
COPY --chown=10001:10001 app.jar /app/app.jar
COPY --chown=10001:10001 HealthProbe.class /app/HealthProbe.class
USER 10001:10001
# Container-only overrides; ordinary host foundation safety defaults stay unchanged.
ENV SERVER_ADDRESS=0.0.0.0 SERVER_PORT=8080 SPRING_PROFILES_ACTIVE=foundation
EXPOSE 8080
STOPSIGNAL SIGTERM
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 \
  CMD ["java", "-cp", "/app", "HealthProbe", "http://127.0.0.1:8080/actuator/health"]
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-XX:MaxRAMPercentage=60.0", "-XX:InitialRAMPercentage=10.0", "-jar", "/app/app.jar"]
