# Stage 1: Build
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /build

COPY pom.xml .
COPY src ./src

RUN apt-get update && apt-get install -y maven && \
    mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:25-jdk
WORKDIR /app

COPY --from=builder /build/target/dq-0.0.1-SNAPSHOT.jar app.jar

VOLUME ["/app/controls", "/app/repository", "/app/scheduler", "/app/certs"]

EXPOSE 8090

ENTRYPOINT ["java", "-jar", "app.jar"]
