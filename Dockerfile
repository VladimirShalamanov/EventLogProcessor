# 1: Build
# "docker build -t event-log-processor ."
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn package -DskipTests -q

# 2: Run
# "docker run --rm -v "${PWD}:/data" event-log-processor /data/input.jsonl"
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/EventLogProcessor-1.0-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
