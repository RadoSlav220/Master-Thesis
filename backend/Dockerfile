# --- Build stage: compile and package the Spring Boot jar ---
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

# Cache dependencies first (layer is reused unless pom.xml changes).
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Build the application.
COPY src ./src
RUN mvn -B clean package -DskipTests

# --- Runtime stage: slim JRE image running the jar as a non-root user ---
FROM eclipse-temurin:25-jre
WORKDIR /app

# Run as an unprivileged user.
RUN useradd --system --uid 1001 spring
USER spring

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
