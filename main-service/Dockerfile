FROM gradle:jdk25 AS build
WORKDIR /app

COPY main-service/gradlew main-service/settings.gradle* main-service/build.gradle* main-service/gradle.properties* ./main-service/
COPY main-service/gradle ./main-service/gradle
WORKDIR /app/main-service
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

COPY main-service/src ./src
COPY stats-service/src/main/proto /app/stats-service/src/main/proto
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/main-service/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
