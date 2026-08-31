FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package
FROM eclipse-temurin:25-jre-jammy
ARG APP_VERSION=dev
ARG GIT_COMMIT=local
LABEL org.opencontainers.image.version=$APP_VERSION org.opencontainers.image.revision=$GIT_COMMIT
ENV APP_VERSION=$APP_VERSION GIT_COMMIT=$GIT_COMMIT
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --system --uid 10001 campus && mkdir -p /app/uploads && chown -R campus:campus /app
COPY --from=build /workspace/target/*.jar app.jar
USER campus
EXPOSE 8082
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
