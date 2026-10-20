FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 app
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar
USER 10001
ENTRYPOINT ["java", "-jar", "app.jar"]
