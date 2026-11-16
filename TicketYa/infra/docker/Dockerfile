# Imagen genérica: construye UN servicio del reactor Maven (junto con el starter) y lo empaqueta.
#   docker build -f infra/docker/Dockerfile --build-arg SERVICE=ms-catalog-service .
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
ARG SERVICE

COPY . .
# -pl/-am: compila el servicio y sus dependencias locales (el starter). La caché de ~/.m2 se reutiliza entre builds.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -q -pl "services/${SERVICE}" -am package -Dmaven.test.skip=true \
    && cp "services/${SERVICE}"/target/*.jar /tmp/app.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --home-dir /app app
COPY --from=build /tmp/app.jar app.jar
USER app
ENTRYPOINT ["java", "-jar", "app.jar"]
