# Stage 1: Build application using Maven and JDK 21
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

COPY pom.xml ./
# Pre-fetch dependencies for layer caching
RUN apt-get update && apt-get install -y maven && mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal runtime image
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=builder /build/target/veloce-engine-*.jar /app/veloce-engine.jar

# Run with low-latency JVM flags and pre-touched heap
ENTRYPOINT ["java", \
            "-XX:-RestrictContended", \
            "-XX:+UnlockExperimentalVMOptions", \
            "-XX:+UseZGC", \
            "-XX:+ZGenerational", \
            "-XX:+AlwaysPreTouch", \
            "-Xms2g", "-Xmx2g", \
            "-jar", "/app/veloce-engine.jar"]

CMD ["run", "--synthetic-load"]
