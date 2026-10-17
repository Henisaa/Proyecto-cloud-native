Infraestructura RutaExpress
Ejecute los comandos desde la raíz del repositorio. Docker Compose v2 es necesario.

Desarrollo local con aplicaciones ejecutadas mediante Gradle
docker network create rutaexpress
docker compose -p rutaexpress-mq -f infra/mq/compose.yml up -d
docker compose -p rutaexpress-kafka -f infra/kafka/compose.yml up -d
docker compose -p rutaexpress-apps -f infra/apps/compose.yml up -d redis mailpit
docker compose -p rutaexpress-mq -f infra/mq/compose.yml exec rabbit1 rabbitmqctl cluster_status
docker compose -p rutaexpress-kafka -f infra/kafka/compose.yml ps
RabbitMQ Management: localhost:15672 y localhost:15673, usuario rutaexpress, password rutaexpress-local. SMTP: localhost:1025; Mailpit: localhost:8025; Redis: localhost:6379; Kafka UI: localhost:8090.
Arranque los administradores según sus README. En modo local sin JWT:
Invoke-RestMethod -Method Post -Uri http://localhost:8086/api/mq-admin/topology
Invoke-RestMethod -Method Post -Uri http://localhost:8087/api/kafka-admin/topology
Luego arranque los consumidores y productores. No cree tópicos con réplicas 3 antes de que los tres brokers estén disponibles.

Aplicaciones en contenedores
Los tres Compose utilizan la red externa rutaexpress. En infra/apps puede copiar .env.example a .env. Para desarrollo sin Azure defina SECURITY_ENABLED=false en el entorno o en ese archivo. El stack de aplicaciones inicia Oracle y espera a que esté saludable antes de iniciar catalog, audit, report y shipments.
docker compose --env-file infra/apps/.env -p rutaexpress-apps -f infra/apps/compose.yml up -d --build
Kafka y RabbitMQ deben levantarse antes de apps. Las URLs internas usan nombres de servicios Docker; desde el host se usan localhost y los puertos publicados.
Los puertos se enlazan a 127.0.0.1 para desarrollo. En EC2 reemplace hosts y redes según las instancias, listeners EXTERNAL de Kafka con DNS/IP privados, y limite los Security Groups. No reutilice las credenciales locales. No hay aprovisionamiento automático de AWS API Gateway en estos Compose.

Oracle
El stack Compose usa Oracle Free en la misma red y espera su healthcheck antes de arrancar las cuatro aplicaciones con persistencia. El perfil predeterminado es `prod`; el `application.yml` de cada servicio configura Oracle y Flyway. El script inicial crea `ruta_catalog`, `ruta_shipments`, `ruta_audit` y `ruta_report` en `FREEPDB1` con password `Rutaexpress123`. Se ejecuta únicamente cuando el volumen Oracle se inicializa. `DB_PASSWORD` cambia la contraseña de `SYSTEM` del contenedor; cambie también el script o las contraseñas de los usuarios si usa otro valor.

Para usar una base Oracle cloud externa, defina `DB_URL=jdbc:oracle:thin:@//<host-cloud>:1521/<servicio>` en `infra/apps/.env`; mantenga `SPRING_PROFILES_ACTIVE=prod` y configure `SPRING_DATASOURCE_PASSWORD` para cada esquema. El compose seguirá esperando al contenedor Oracle local; para despliegues con base externa, use el compose del entorno correspondiente sin el servicio `oracle` ni esas dependencias. No exponga credenciales en Git.

RabbitMQ
Las aplicaciones usan los nombres de host `rabbit1` y `rabbit2`. mq-admin prueba `RABBITMQ_MANAGEMENT_URLS=http://rabbit1:15672,http://rabbit2:15672` en orden, igual que el compose compartido.

Persistencia y límites
RabbitMQ tiene dos nodos con descubrimiento automático, cookie compartida y volúmenes separados. Las colas classic durables conservan datos en disco, pero no se replican automáticamente por estar en un clúster. Esta configuración cumple los dos nodos del caso y no promete continuidad de una cola cuando su nodo se cae. Alta disponibilidad de mensajes requiere planificar migración a quorum y preferiblemente tres nodos; dos réplicas quorum no toleran perder un nodo.
start-zookeeper-mode.sh inicia el broker directamente en modo ZooKeeper, evitando el launcher KRaft por defecto de la imagen Apache.
Kafka tiene tres brokers 3.9.2 y tres ZooKeeper 3.9. Las réplicas de tópicos son tres; min.insync.replicas=2. La configuración es PLAINTEXT para una red privada de desarrollo. Producción debe configurar TLS/SASL y credenciales de administradores con privilegios mínimos.
Las políticas mq-admin limitan comandos y DLQ; expiración de DLQ es eliminación deliberada según su retención. No se reprocesan DLQ automáticamente ni se crea un ciclo entre DLQ y comandos.

Limpieza de recursos creados por estos comandos
Detenga antes las aplicaciones ejecutadas con Gradle.
docker compose -p rutaexpress-apps -f infra/apps/compose.yml down --volumes --rmi local
docker compose -p rutaexpress-kafka -f infra/kafka/compose.yml down --volumes
docker compose -p rutaexpress-mq -f infra/mq/compose.yml down --volumes
docker network rm rutaexpress
Los comandos down --volumes borran los datos locales de los stacks indicados; úselos solo cuando ya no necesite esos datos. Las imágenes descargadas compartidas no se borran con estos comandos. Elimine manualmente una imagen únicamente si fue creada o descargada para esta prueba y no la usa otro proyecto. No utilice prune global.
