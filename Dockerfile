# ------------------------------------------------------------------
#  The backend as a Docker image, built in 2 stages:
#    1. "build": a big image with Maven + JDK compiles the code into one .jar
#    2. the final image: only a Java runtime + that .jar (smaller, no compiler, no source code)
#
#  You normally don't build this by hand: "docker compose up --build" does it (see docker-compose.yml).
# ------------------------------------------------------------------

# ---- 1. Build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# The cache mount keeps Maven's downloads between builds, so the 2nd build is much faster.
# Tests are skipped here because CI runs them before this step.
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package

# ---- 2. Run ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# Don't run as root inside the container: if someone broke in, they'd have almost no power
RUN useradd --system --uid 1001 gymapp
COPY --from=build /app/target/*.jar app.jar
USER gymapp
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
