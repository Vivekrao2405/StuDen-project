# --- Build stage -----------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy only what's needed to resolve dependencies first, so this layer is
# cached across builds where only src/ changed.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# mvnw is tracked in git without the executable bit (mode 100644) — a fresh
# clone (like Render's build) needs it set explicitly or ./mvnw fails outright.
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src ./src
# Tests hit a real PostgreSQL database (no H2/Testcontainers) that isn't
# reachable during a container build, so they must be skipped here — the
# build environment has no database to connect to.
RUN ./mvnw clean package -DskipTests -B

# --- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN addgroup --system spring && adduser --system --ingroup spring spring
COPY --from=build /app/target/studen-backend-0.0.1-SNAPSHOT.jar app.jar
RUN chown spring:spring app.jar
USER spring:spring

# Documents the conventional default; the app actually binds to whatever
# $PORT resolves to at container start (see ENTRYPOINT below), which is what
# Render requires since it assigns the port dynamically.
EXPOSE 8080

# Spring Boot's server.port is set via -D (a higher-precedence PropertySource
# than application.yml's own ${SERVER_PORT:8080} placeholder), so this makes
# the app listen on Render's $PORT without touching any application code.
# Falls back to 8080 for local `docker run` where $PORT isn't set.
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar app.jar"]
