FROM gradle:9.4.0-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/home/gradle/.gradle gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
