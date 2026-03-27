# Multi-stage build for entry-auth-service
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /app

# Copy gradle wrapper and build files
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle .
COPY settings.gradle .

# Make gradlew executable
RUN chmod +x gradlew;

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code
COPY src/ src/

# Build the application
RUN ./gradlew bootJar --no-daemon -x test;

# Runtime stage
FROM eclipse-temurin:25-jre AS runner

WORKDIR /app

# Install curl for healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*;

# Copy the built jar
COPY --from=builder /app/build/libs/*.jar app.jar

# Run as non-root user
RUN useradd -r -u 1001 appuser && chown -R appuser:appuser /app;
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
