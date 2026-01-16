FROM maven:3.8.6-eclipse-temurin-11 AS maven
LABEL maintainer="emmanuel.coulombe.1@ens.etsmtl.ca"

WORKDIR /usr/src/app

# Copy pom.xml first to leverage Docker layer caching for dependencies
# Dependencies will only be re-downloaded if pom.xml changes
COPY pom.xml .
# Download dependencies using BuildKit cache mount for faster subsequent builds
# Build with: DOCKER_BUILDKIT=1 docker build ...
RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline -B

# Now copy the source code
COPY src ./src

# Compile and package using BuildKit cache mount for Maven repository cache
# Tests can be run separately in CI/CD pipeline
RUN --mount=type=cache,target=/root/.m2 mvn package -DskipTests -B

FROM amazoncorretto:11-alpine-jdk AS runtime

# Install fonts and necessary packages for image generation
# Install curl for health checks
RUN apk --no-cache add \
    msttcorefonts-installer \
    fontconfig \
    ttf-dejavu \
    ttf-liberation \
    curl \
    && update-ms-fonts \
    && fc-cache -f \
    && rm -rf /var/cache/apk/*

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

ARG JAR_FILE=horaire-ets.jar
ARG APP_VERSION=1.0.0

# Add labels for better container management
LABEL org.opencontainers.image.title="HoraireETS" \
      org.opencontainers.image.description="Schedule generation service for ETS" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.maintainer="emmanuel.coulombe.1@ens.etsmtl.ca"

EXPOSE 8080

# Use environment variable for port configuration
ENV PORT=8080 \
    JAVA_OPTS="" \
    SPRING_PROFILES_ACTIVE=production

WORKDIR /opt/app

# Copy the JAR from the maven stage
COPY --from=maven /usr/src/app/target/${JAR_FILE} /opt/app/app.jar

# Change ownership to non-root user
RUN chown -R spring:spring /opt/app

# Switch to non-root user
USER spring:spring

# Health check for container orchestration
# Uses Spring Boot Actuator health endpoint (requires actuator dependency)
# Fallback: If actuator is not configured, remove HEALTHCHECK or adjust the endpoint
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:${PORT}/actuator/health || exit 1

# Optimized JVM options for containerized environments
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+UseStringDeduplication -Djava.security.egd=file:/dev/./urandom -Dserver.port=${PORT} -jar app.jar"]
