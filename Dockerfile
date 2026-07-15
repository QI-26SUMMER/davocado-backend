# ---- Build stage: compile the Spring Boot fat jar with the Gradle wrapper ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy wrapper + build definition first so dependency resolution is cached
# separately from source changes.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true

# Copy sources and build the runnable boot jar (skip tests — they need a DB).
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- Runtime stage: slim JRE image running the jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# Render injects $PORT at runtime; bind Spring to it. Everything runs in UTC.
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Duser.timezone=UTC -Dserver.port=${PORT:-8080} -jar app.jar"]
