# --- Build stage -------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy only what's needed to resolve dependencies first, so this layer is
# cached across builds where only src/ changed.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# mvnw is tracked in git without the executable bit (mode 100644) -- a fresh
# clone needs it set explicitly or ./mvnw fails outright.
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# --- Runtime stage -------------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Deliberately NOT a non-root user: this app needs to reach the Docker socket
# bind-mounted in by docker-compose.yml (Docker-outside-of-Docker), which on
# most host setups is only accessible to root or a specific "docker" group GID
# that varies per host. The hardening that matters -- network=none, read-only
# rootfs, cap-drop ALL, non-root 10001:10001 -- is applied to the ephemeral
# studen-code-runner SANDBOX containers this app creates, not to this app
# itself. This app is trusted orchestrator infrastructure, not student code.
COPY --from=build /app/target/studen-execution-server-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
