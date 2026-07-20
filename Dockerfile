# ---- Build stage: compile the Spring Boot fat jar with the Gradle wrapper ----
# The Gradle build runs inside the container (Linux, ASCII paths), sidestepping the host's
# Korean-path issue entirely.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
# Tests need H2 and are already verified on the host; skip them for a faster image build.
RUN chmod +x gradlew && ./gradlew --no-daemon clean bootJar -x test

# ---- Runtime stage: slim JRE image running the jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar
# Cloud Run (and Render) inject $PORT at runtime; bind Spring to it. Everything runs in UTC.
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Duser.timezone=UTC -Dserver.port=${PORT:-8080} -jar app.jar"]
