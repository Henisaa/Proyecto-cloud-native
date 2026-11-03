package com.rutaexpress.kafkaadmin

import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.TopologyTestDriver
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import org.apache.kafka.streams.state.ReadOnlyWindowStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.kafka.core.KafkaTemplate
import java.time.Instant

class KafkaStreamsServiceTests {

    @Test
    fun `topologia Kafka Streams ejecuta map, filter, branch, count, reduce, ventanas y actualiza State Stores`() {
        val service = KafkaStreamsService(autoStartup = false)
        val topology = service.buildTopology()
        val props = service.streamsProperties()

        TopologyTestDriver(topology, props).use { driver ->
            val stringSerde = Serdes.String()
            val longSerde = Serdes.Long()

            val inputTopic = driver.createInputTopic(
                KafkaStreamsService.INPUT_TOPIC,
                stringSerde.serializer(),
                stringSerde.serializer()
            )
            val estandarOut = driver.createOutputTopic(
                KafkaStreamsService.TOPIC_RAMA_ESTANDAR,
                stringSerde.deserializer(),
                stringSerde.deserializer()
            )
            val prioritarioOut = driver.createOutputTopic(
                KafkaStreamsService.TOPIC_RAMA_PRIORITARIA,
                stringSerde.deserializer(),
                stringSerde.deserializer()
            )
            val alertasOut = driver.createOutputTopic(
                KafkaStreamsService.TOPIC_ALERTAS_FILTRADAS,
                stringSerde.deserializer(),
                stringSerde.deserializer()
            )
            val conteoServicioOut = driver.createOutputTopic(
                KafkaStreamsService.TOPIC_CONTEO_SERVICIO,
                stringSerde.deserializer(),
                longSerde.deserializer()
            )
            val acumuladoZonaOut = driver.createOutputTopic(
                KafkaStreamsService.TOPIC_ACUMULADO_ZONA,
                stringSerde.deserializer(),
                stringSerde.deserializer()
            )

            val baseTime = Instant.parse("2026-11-03T15:00:10Z")

            // 1) Envío EXPRESS en zona NORTE (alta prioridad -> activa filter y va a rama prioritaria)
            inputTopic.pipeInput(
                "env-1",
                """{"shipmentId":"env-1","serviceType":"EXPRESS","status":"ACEPTADO","zone":"NORTE","weightKg":15.0,"packagesCount":3,"price":20000.0}""",
                baseTime
            )

            // 2) Envío ESTANDAR ligero en zona CENTRO (va a rama estándar)
            inputTopic.pipeInput(
                "env-2",
                """{"shipmentId":"env-2","serviceType":"ESTANDAR","status":"EN_TRANSITO","zone":"CENTRO","weightKg":3.0,"packagesCount":1,"price":4500.0}""",
                baseTime.plusSeconds(20)
            )

            // 3) Segundo envío EXPRESS en zona NORTE (suma en reduce de NORTE y count de EXPRESS)
            inputTopic.pipeInput(
                "env-3",
                """{"shipmentId":"env-3","serviceType":"EXPRESS","status":"ENTREGADO","zone":"NORTE","weightKg":10.5,"packagesCount":2,"price":15000.0}""",
                baseTime.plusSeconds(45)
            )

            assertThat(estandarOut.readValuesToList()).hasSize(1)
            assertThat(prioritarioOut.readValuesToList()).hasSize(2)
            assertThat(alertasOut.readValuesToList()).hasSize(2)
            assertThat(conteoServicioOut.readKeyValuesToMap()["EXPRESS"]).isEqualTo(2L)
            assertThat(acumuladoZonaOut.readValuesToList()).isNotEmpty

            val servicioStore: ReadOnlyKeyValueStore<String, Long> =
                driver.getKeyValueStore(KafkaStreamsService.STORE_CONTEO_SERVICIO)
            val estadoStore: ReadOnlyKeyValueStore<String, Long> =
                driver.getKeyValueStore(KafkaStreamsService.STORE_CONTEO_ESTADO)
            val zonaStore: ReadOnlyKeyValueStore<String, String> =
                driver.getKeyValueStore(KafkaStreamsService.STORE_ACUMULADO_ZONA)
            val tumblingStore: ReadOnlyWindowStore<String, Long> =
                driver.getWindowStore(KafkaStreamsService.STORE_VENTANA_TUMBLING_ZONA)
            val hoppingStore: ReadOnlyWindowStore<String, Long> =
                driver.getWindowStore(KafkaStreamsService.STORE_VENTANA_HOPPING_SERVICIO)

            val estado = service.extraerEstadoDeStores(
                servicioStore = servicioStore,
                estadoStore = estadoStore,
                zonaStore = zonaStore,
                tumblingStore = tumblingStore,
                hoppingStore = hoppingStore
            )

            assertThat(estado.applicationId).isEqualTo(KafkaStreamsService.APPLICATION_ID)
            assertThat(estado.countsByService["EXPRESS"]).isEqualTo(2L)
            assertThat(estado.countsByService["ESTANDAR"]).isEqualTo(1L)
            assertThat(estado.countsByStatus["ENTREGADO"]).isEqualTo(1L)

            val norte = estado.reducedByZone.first { it.zone == "NORTE" }
            assertThat(norte.totalShipments).isEqualTo(2L)
            assertThat(norte.totalPackages).isEqualTo(5)
            assertThat(norte.totalWeightKg).isEqualTo(25.5)
            assertThat(norte.totalRevenue).isEqualTo(35000.0)

            assertThat(estado.tumblingWindowsByZone).isNotEmpty
            assertThat(estado.hoppingWindowsByService).isNotEmpty
            assertThat(estado.recentAlertsAndFiltered).hasSize(2)
        }
    }

    @Test
    fun `simularEscenario y consultarEstado funcionan en modo local sin broker activo`() {
        @Suppress("UNCHECKED_CAST")
        val template = mock(KafkaTemplate::class.java) as KafkaTemplate<String, String>
        val service = KafkaStreamsService(kafkaTemplate = template, autoStartup = false)

        val rafaga = service.simularEscenario("rafaga")
        val prioritario = service.simularEscenario("prioritario")
        val estandar = service.simularEscenario("estandar")

        assertThat(rafaga).hasSize(5)
        assertThat(prioritario).hasSize(1)
        assertThat(estandar).hasSize(1)

        val estado = service.consultarEstado()
        assertThat(estado.totalProcessed).isEqualTo(7L)
        assertThat(estado.countsByService).containsKeys("EXPRESS", "ESTANDAR", "MISMO_DIA")
        assertThat(estado.reducedByZone).isNotEmpty
        assertThat(estado.tumblingWindowsByZone).isNotEmpty
        assertThat(estado.hoppingWindowsByService).isNotEmpty
        assertThat(estado.branchCounts["rama-estandar"]).isGreaterThan(0L)
        assertThat(estado.branchCounts["rama-prioritaria"]).isGreaterThan(0L)
    }
}
