FROM gradle:9.4.0-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle :MediaGuard-Client:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/MediaGuard-Client/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
