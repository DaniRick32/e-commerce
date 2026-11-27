# Build the executable Spring Boot jar.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Cache dependencies before copying the source.
COPY pom.xml .
RUN mvn -B -e dependency:go-offline

COPY src ./src
RUN mvn -B -e clean package -Dmaven.test.skip=true

# Run the jar in a small JRE image as a non-root user.
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build --chown=spring:spring /workspace/target/server-0.0.1-SNAPSHOT.jar app.jar

USER spring
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
