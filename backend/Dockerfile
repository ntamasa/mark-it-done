# Build stage
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /build

# Copy gradle wrapper and configuration
COPY gradle/ ./gradle/
COPY gradlew gradlew.bat settings.gradle build.gradle ./

# Download dependencies in a separate layer for better caching
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src/ ./src/

# Build the application (boot jar only, no tests)
RUN ./gradlew bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:25-jre

WORKDIR /app

# Create system user
RUN useradd -r -u 10001 -s /sbin/nologin appuser && \
    mkdir -p /app/logs && \
    chown -R appuser:appuser /app

# Copy the boot JAR from the build stage
COPY --from=builder --chown=appuser:appuser /build/build/libs/markitdone-0.0.1-SNAPSHOT.jar /app/app.jar

# Set JVM memory to use 75% of container limit
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
