#!/usr/bin/env bash
set -euo pipefail
cat > /tmp/rutaexpress-server.properties <<EOF
broker.id=${KAFKA_BROKER_ID}
zookeeper.connect=${KAFKA_ZOOKEEPER_CONNECT}
listeners=${KAFKA_LISTENERS}
advertised.listeners=${KAFKA_ADVERTISED_LISTENERS}
listener.security.protocol.map=${KAFKA_LISTENER_SECURITY_PROTOCOL_MAP}
inter.broker.listener.name=INTERNAL
log.dirs=/var/lib/kafka/data
offsets.topic.replication.factor=3
transaction.state.log.replication.factor=3
transaction.state.log.min.isr=2
default.replication.factor=3
min.insync.replicas=2
num.partitions=3
auto.create.topics.enable=false
zookeeper.connection.timeout.ms=60000
log.retention.hours=${KAFKA_LOG_RETENTION_HOURS:-0}
log.retention.minutes=${KAFKA_LOG_RETENTION_MINUTES:-5}
log.retention.bytes=${KAFKA_LOG_RETENTION_BYTES:-52428800}
log.retention.check.interval.ms=${KAFKA_LOG_RETENTION_CHECK_INTERVAL_MS:-60000}
EOF
exec /opt/kafka/bin/kafka-server-start.sh /tmp/rutaexpress-server.properties
