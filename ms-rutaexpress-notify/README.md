ms-rutaexpress-notify
Notificaciones email, push y PDF

Funcionamiento
No tiene base relacional ni API pública de negocio. Consume q.cmd.email, q.cmd.warehouse y q.cmd.label desde cmd.direct o cmd.topic. Los mensajes incluyen type, eventId, timestamp, traceId, correlationId y payload. Redis reclama eventId antes de ejecutar la acción, lo libera ante error y conserva eventos completados durante siete días. Email usa SMTP; tickets y etiquetas se generan en memoria con OpenPDF y se adjuntan por email. Push Firebase es opcional mediante FIREBASE_CREDENTIALS_PATH. No persiste PDFs temporales.
ACK manual tras éxito; tres intentos con backoff exponencial; NACK sin reencolar al agotar intentos, hacia cmd.dead.dlx y la DLQ correspondiente. rutaexpress.notify.dlq.messages cuenta mensajes fallidos. La idempotencia evita repetir eventos completados, pero una caída después del envío SMTP y antes de marcar Redis puede repetir un email.

Requisitos
JDK 21 o superior, JAVA_HOME configurado y acceso a Maven Central. El bytecode generado es Java 21. Se usa Kotlin 2.3.21, Spring Boot 4.1.1, Jackson 3 y Gradle 9.7.1 mediante el wrapper incluido. No se necesita Gradle instalado. En Linux/macOS sustituya .\gradlew.bat por ./gradlew y defina las variables con export.

Verificación y limpieza
.\gradlew.bat test bootJar
.\gradlew.bat clean
.\gradlew.bat --stop
Remove-Item -LiteralPath .gradle,.kotlin -Recurse -Force -ErrorAction SilentlyContinue
No elimine gradle/wrapper: contiene los archivos versionados necesarios para compilar.

Ejecución local desde la raíz del repositorio
Antes de arrancar, levante la infraestructura según infra/README.md. Necesita RabbitMQ, Redis en 6379 y SMTP en 1025. Puede levantarlos con docker compose up -d rabbitmq redis mailpit desde esta carpeta; ese compose es una alternativa de nodo único para desarrollo, usa guest/guest, y no debe ejecutarse junto con el stack infra que usa los mismos puertos. Use RABBITMQ_USERNAME=guest y RABBITMQ_PASSWORD=guest para esa alternativa.

cd ms-rutaexpress-notify
$env:SECURITY_ENABLED="false"
$env:RABBITMQ_USERNAME="rutaexpress"
$env:RABBITMQ_PASSWORD="rutaexpress-local"
.\gradlew.bat bootRun

La aplicación escucha en http://localhost:8082. Salud: /actuator/health. Métricas: /actuator/prometheus.

Endpoints
GET /actuator/health
GET /actuator/prometheus con rol Admin en modo seguro
Mailpit: http://localhost:8025 para revisar correos y sus adjuntos.

Seguridad
En despliegue seguro, SECURITY_ENABLED=true. Configure AZURE_AD_ISSUER_URI con el tenant corporativo y AZURE_AD_AUDIENCE con el identificador de la API. AZURE_AD_CLIENT_ID es el GUID de la API cuando se utiliza el validador automático de Spring. Se valida firma, issuer, audiencia y vigencia. Roles reconocidos: Admin, Despachador, Cliente y Auditor. Operador del dominio y Cliente del dominio se normalizan a Despachador y Cliente.
La desactivación de seguridad se utiliza solamente en desarrollo local. No despliegue ese modo detrás de un Gateway público.
