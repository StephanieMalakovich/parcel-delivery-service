# --- build stage ---
FROM gradle:jdk25 AS build
WORKDIR /app

# Сначала только файлы сборки — чтобы кэшировался слой с зависимостями
COPY gradlew settings.gradle* build.gradle* gradle.properties* ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

# Теперь исходники
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

# --- runtime stage ---
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
