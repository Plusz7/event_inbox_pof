FROM eclipse-temurin:21-jre

# Created before the jar is copied so this layer stays cached across app rebuilds.
RUN useradd --create-home --shell /usr/sbin/nologin appuser

WORKDIR /app
COPY --chown=appuser:appuser target/event-producer-*.jar app.jar

# Without this the process runs as root, handing an attacker root inside the container.
USER appuser

# Without this a hung JVM still looks "up" to the orchestrator.
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -fsS "http://localhost:${SERVER_PORT:-8080}/actuator/health" || exit 1

ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]
