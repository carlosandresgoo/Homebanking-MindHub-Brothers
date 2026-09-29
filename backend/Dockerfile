# Build context is the repository root (settings.gradle, wrapper and version catalog live there):
#   docker build -f backend/Dockerfile .

# --- Build ---
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew settings.gradle ./
COPY gradle gradle
COPY backend/build.gradle backend/
# Committed from Windows, gradlew may lack the executable bit.
RUN chmod +x gradlew && ./gradlew --no-daemon :backend:dependencies > /dev/null
COPY backend/src backend/src
RUN ./gradlew --no-daemon :backend:bootJar

# --- Run ---
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /workspace/backend/build/libs/backend-*-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
