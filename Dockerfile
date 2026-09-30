FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Tests run in CI before the image is built.
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 gymapp
COPY --from=build /app/target/*.jar app.jar
USER gymapp
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
