# syntax=docker/dockerfile:1.7
# Imagen "todo en uno" para probar RutaExpress en UN solo contenedor (por ejemplo, un Web Service de Render).
# Reúne el clúster RabbitMQ de dos nodos, Redis, Mailpit, los cinco microservicios EP2, el frontend y un nginx que
# es la única entrada pública. Oracle queda fuera: se indica con DB_URL. Configuración y variables: render/README.md.
# No reemplaza el despliegue de la evaluación (EC2 + docker compose + API Gateway), que sigue en infra/.

# ---------------------------------------------------------------- 1) Microservicios, uno tras otro con sus pruebas
# Igual que los Dockerfile de cada servicio: si una prueba falla, la imagen no se construye. Se compilan en secuencia
# para no agotar la memoria de la máquina de build; la caché de Gradle se comparte entre los cinco.
FROM eclipse-temurin:21-jdk AS services
WORKDIR /src
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.jvmargs=-Xmx1g -Dorg.gradle.workers.max=2 -Dkotlin.compiler.execution.strategy=in-process"
COPY ms-rutaexpress-rabbit-admin/ rabbit-admin/
RUN cd rabbit-admin && chmod +x gradlew && ./gradlew --no-daemon build
COPY ms-rutaexpress-notify/ notify/
RUN cd notify && chmod +x gradlew && ./gradlew --no-daemon build
COPY ms-rutaexpress-catalog/ catalog/
RUN cd catalog && chmod +x gradlew && ./gradlew --no-daemon build
COPY ms-rutaexpress-shipments/ shipments/
RUN cd shipments && chmod +x gradlew && ./gradlew --no-daemon build
COPY ms-rutaexpress-bff/ bff/
RUN cd bff && chmod +x gradlew && ./gradlew --no-daemon build

# ---------------------------------------------------------------- 2) Frontend Angular de producción (con sus pruebas)
FROM node:22-alpine AS frontend
WORKDIR /app
RUN apk add --no-cache chromium
ENV CHROME_BIN=/usr/bin/chromium-browser
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm test -- --watch=false --browsers=ChromeHeadlessDocker --code-coverage
RUN npm run build

# ---------------------------------------------------------------- 3) Ejecución: RabbitMQ (Erlang incluido) + JRE + resto
FROM rabbitmq:4-management AS runtime
RUN apt-get update \
 && apt-get install -y --no-install-recommends nginx redis-server supervisor jq curl ca-certificates \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --home-dir /app app
COPY --from=eclipse-temurin:21-jre /opt/java/openjdk /opt/java/openjdk
COPY --from=axllent/mailpit:v1.27 /mailpit /usr/local/bin/mailpit
ENV JAVA_HOME=/opt/java/openjdk \
    PATH=/opt/java/openjdk/bin:$PATH \
    TZ=UTC \
    PORT=10000

WORKDIR /app
COPY --from=services /src/rabbit-admin/build/libs/app.jar /app/rabbit-admin.jar
COPY --from=services /src/notify/build/libs/app.jar /app/notify.jar
COPY --from=services /src/catalog/build/libs/app.jar /app/catalog.jar
COPY --from=services /src/shipments/build/libs/app.jar /app/shipments.jar
COPY --from=services /src/bff/build/libs/app.jar /app/bff.jar
COPY --from=frontend /app/dist/frontend-rutaexpress/browser /usr/share/rutaexpress/html
COPY render/ /opt/rutaexpress/
RUN chmod +x /opt/rutaexpress/*.sh \
 && mkdir -p /etc/rutaexpress \
 && chown -R app:app /app

EXPOSE 10000
ENTRYPOINT ["bash", "/opt/rutaexpress/start.sh"]
CMD []
