# DevFlow AI container image.
# Stage 1 compiles the app with Maven + JDK 21; stage 2 runs it on a small Java 21 runtime.
# You do not need Java or Maven installed on your computer to use this.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# Download dependencies first so they are cached between builds
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
# Run as an unprivileged user instead of root
RUN useradd --system --uid 10001 devflow
COPY --from=build /src/target/dev-productivity-platform-0.1.0.jar app.jar
USER devflow
# Inside the container the app must listen on all interfaces; docker-compose
# still publishes the port on 127.0.0.1 only, so it stays private to your machine.
ENV SERVER_ADDRESS=0.0.0.0
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
