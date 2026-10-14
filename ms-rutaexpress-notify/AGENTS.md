# Notify repository guidelines

This directory is the implemented ms-rutaexpress-notify service in the RutaExpress monorepo. Its sources are in src/main/kotlin/com/rutaexpress/demo, not a nested demo directory. The other seven services and Angular frontend exist in sibling directories. Do not change or stage docs.

Use Kotlin 2.3.21, Spring Boot 4.1.1, Jackson 3 (tools.jackson), JVM bytecode 21 and the included Gradle 9.7.1 wrapper. Keep constructor injection, immutable DTOs, Jakarta field validation, SLF4J placeholders and MVVM dependencies. Keep Firebase and OpenPDF versions explicit as in build.gradle.kts.

RabbitMqConfig owns the three durable exchanges, six queues, routing keys and JSON converter. Keep manual ACK/NACK, validate before Redis idempotency claim, release failed claims and complete only after delivery. Retries run in-process with exponential backoff; do not add another retry layer. Never automatically replay DLQ into a cycle. Queue retention policies are administered by the sibling mq-admin service.

Run .\gradlew.bat test after changes. When changing AMQP topology, converter or consumers, also run tests with RUN_DOCKER_TESTS=true if Docker is available; report clearly if broker integration could not run. Tests must protect observable behavior. Preserve existing in-memory idempotency doubles. Security tests use StubJwtDecoderConfiguration and never require Azure connectivity.

The application port is 8082. The standalone compose is a single-node alternative using guest/guest; the shared infra/mq stack has two nodes and different credentials. Never run both on the same ports. See README.md for commands and environment variables. After validation remove only generated build, .gradle and .kotlin directories; preserve wrapper files and resources that existed before this work.
