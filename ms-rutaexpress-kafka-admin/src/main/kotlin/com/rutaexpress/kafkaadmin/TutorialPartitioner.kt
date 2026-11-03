package com.rutaexpress.kafkaadmin

import org.apache.kafka.clients.producer.Partitioner
import org.apache.kafka.common.Cluster
import org.apache.kafka.common.utils.Utils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Particionador para el tutorial de Kafka:
 * - Sin Key: Distribuye en Round Robin entre las particiones disponibles (0, 1, 2...).
 * - Con Key: Sticky Partitioning mediante hash de la clave; todos los mensajes con la misma clave
 *   van a la misma partición, garantizando el orden de procesamiento.
 */
class TutorialPartitioner : Partitioner {
    companion object {
        private val topicCounters = ConcurrentHashMap<String, AtomicInteger>()
    }

    override fun partition(
        topic: String,
        key: Any?,
        keyBytes: ByteArray?,
        value: Any?,
        valueBytes: ByteArray?,
        cluster: Cluster
    ): Int {
        val partitions = cluster.partitionsForTopic(topic) ?: return 0
        val numPartitions = partitions.size
        if (numPartitions <= 0) return 0

        // Con Key: Kafka usa el hash de la key -> todos a la misma partición
        if (keyBytes != null && keyBytes.isNotEmpty()) {
            return Utils.toPositive(Utils.murmur2(keyBytes)) % numPartitions
        }

        // Sin Key: Round Robin entre particiones 0, 1, 2...
        val counter = topicCounters.computeIfAbsent(topic) { AtomicInteger(0) }
        val next = counter.getAndIncrement()
        return Utils.toPositive(next) % numPartitions
    }

    override fun close() {}
    override fun configure(configs: MutableMap<String, *>?) {}
}
