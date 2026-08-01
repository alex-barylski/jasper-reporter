# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Download dependencies in a separate layer for better cache reuse
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build the fat-jar
COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /build/target/jasper-reporter-1.0.0.jar app.jar

# Reports directory shared with the PHP / host container
VOLUME /reports

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
