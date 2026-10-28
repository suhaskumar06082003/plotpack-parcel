# syntax=docker/dockerfile:1.7
# One Dockerfile for every service: docker build --build-arg SERVICE=package-service .
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
ARG SERVICE
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -DskipTests -pl ${SERVICE} -am package \
 && cp ${SERVICE}/target/${SERVICE}-1.0.0.jar /app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /app.jar app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
