FROM maven:3.8.6 AS maven
LABEL MAINTAINER="emmanuel.coulombe.1@ens.etsmtl.ca"

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

# Install fonts in a single layer
RUN apk --no-cache add msttcorefonts-installer fontconfig && \
    update-ms-fonts && \
    fc-cache -f

ARG JAR_FILE=horaire-ets.jar

EXPOSE 8080
ENV PORT=8080
WORKDIR /opt/app

# Copy the JAR from the maven stage
COPY --from=maven /usr/src/app/target/${JAR_FILE} /opt/app/

ENTRYPOINT ["java", "-jar", "-Dserver.port=8080", "horaire-ets.jar"]
