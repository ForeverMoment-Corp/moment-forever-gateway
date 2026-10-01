# Use Maven 3.8.5 with OpenJDK 17 as the build environment
FROM maven:3.8.5-openjdk-17 AS build
# Set working directory inside the container
WORKDIR /app
# Copy all project files into the container
COPY . .
# Skip tests to speed up build, assuming tests are run in CI/CD
RUN mvn clean package -DskipTests

# Run Stage
# FROM openjdk:17-jdk-slim
FROM maven:3.9.9-eclipse-temurin-17-alpine
# Set working directory inside the container for runtime
WORKDIR /app
# Copy the jar from the core module where the main application resides
#COPY --from=build /app/moment-forever-gateway/target/*.jar app.jar
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080

# Define the entrypoint command to run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
