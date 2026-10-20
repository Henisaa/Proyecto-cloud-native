# syntax=docker/dockerfile:1.7
# Imagen estándar de RutaExpress: dependencias -> compilación y pruebas -> ejecución.
# Si una prueba falla, `docker build` falla y la imagen no se construye.

# 1) Dependencias: solo cambia cuando cambian los archivos de build, así esta capa se reutiliza.
FROM eclipse-temurin:21-jdk AS deps
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew \
 && ./gradlew --no-daemon --version \
 && (./gradlew --no-daemon dependencies --configuration compileClasspath > /dev/null \
     && ./gradlew --no-daemon dependencies --configuration runtimeClasspath > /dev/null \
     && ./gradlew --no-daemon dependencies --configuration testRuntimeClasspath > /dev/null || true)

# 2) Compilación, pruebas (unitarias y de integración ligera) y jar. Las pruebas con Testcontainers
#    se omiten solas sin Docker; se ejecutan en el job de integración del CI.
FROM deps AS build
COPY src ./src
RUN ./gradlew --no-daemon build

# 3) Reportes de pruebas (opcional): docker build --target reports --output type=local,dest=out .
FROM scratch AS reports
COPY --from=build /app/build/reports /reports
COPY --from=build /app/build/test-results /test-results

# 4) Ejecución: JRE, usuario sin privilegios y jar con nombre fijo.
FROM eclipse-temurin:21-jre AS runtime
ARG SERVICE_PORT=8083
ARG VCS_REF=unknown
ARG VERSION=dev
LABEL org.opencontainers.image.title="ms-rutaexpress-audit" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.source="https://github.com/Henisaa/Proyecto-cloud-native"
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 app
# MaxRAMPercentage=65 deja espacio para metaspace, hilos y buffers dentro del límite de memoria del contenedor.
ENV TZ=UTC \
    PORT=${SERVICE_PORT} \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8"
WORKDIR /app
COPY --from=build --chown=10001:10001 /app/build/libs/app.jar app.jar
USER 10001
EXPOSE ${SERVICE_PORT}
HEALTHCHECK --interval=15s --timeout=3s --start-period=90s --retries=5 \
  CMD curl -fsS "http://localhost:${PORT}/actuator/health/readiness" || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
