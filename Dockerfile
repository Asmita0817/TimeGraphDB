# Stage 1: Build the application using Maven and Temurin JDK 21
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

# Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build executable JAR
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Lightweight JRE runtime for production
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Add unprivileged system user for container security
RUN addgroup -S timegraph && adduser -S timegraph -G timegraph
USER timegraph

# Copy application JAR using wildcard to prevent version mismatch breakages
COPY --from=builder /workspace/target/*.jar app.jar

# Expose port (default 8080, dynamically overridable by Render's $PORT)
ENV PORT=8080
EXPOSE 8080

# Execute Spring Boot respecting dynamic PORT injection
ENTRYPOINT ["sh", "-c", "java -XX:+UseG1GC -XX:MaxRAMPercentage=75 -Dserver.port=${PORT:-8080} -jar app.jar"]
