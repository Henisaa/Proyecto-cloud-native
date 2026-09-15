FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
COPY src ./src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 rutaexpress
COPY --from=build /workspace/build/libs/*.jar app.jar
USER rutaexpress
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
