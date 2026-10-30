# syntax=docker/dockerfile:1

# ==========================================
# Stage 1: Build the executable JAR (Maven)
# ==========================================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Resolve dependencies first so this layer is cached across source-only changes
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Copy sources last — only this layer rebuilds when code changes
COPY src ./src
RUN mvn -B clean package -DskipTests \
    && mv target/*.jar target/app.jar

# ==========================================
# Stage 2: Minimal runtime (JRE only)
# ==========================================
# Alpine-based JRE, multi-arch (linux/amd64 + linux/arm64).
# NOTE: eclipse-temurin:*-jre-alpine is published for amd64 only, so it
# cannot build or run natively on Apple Silicon — hence Liberica here.
FROM bellsoft/liberica-openjre-alpine:17 AS runtime

LABEL org.opencontainers.image.title="calculator-devops" \
      org.opencontainers.image.description="Calculator REST API with end-to-end CI/CD pipeline" \
      org.opencontainers.image.source="https://github.com/GarvRajput17/SPE_MiniProj" \
      org.opencontainers.image.licenses="MIT"

WORKDIR /app

# Security best practice: run as an unprivileged user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy only the built artifact out of the builder stage
COPY --from=builder --chown=appuser:appgroup /build/target/app.jar app.jar

USER appuser

# Service port, overridable at runtime (Spring Boot reads SERVER_PORT)
ENV SERVER_PORT=8081 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC"

EXPOSE 8081

# Container healthcheck backed by the Spring Boot Actuator probe
HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=3 \
  CMD wget -qO- "http://127.0.0.1:${SERVER_PORT}/actuator/health" | grep -q '"status":"UP"' || exit 1

# `sh -c` so JAVA_OPTS expands; `exec` keeps the JVM as PID 1 for clean signals
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
