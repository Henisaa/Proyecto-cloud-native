package com.rutaexpress.catalog.messaging

import com.rabbitmq.client.Channel
import com.rutaexpress.catalog.config.RabbitNombres
import com.rutaexpress.catalog.model.entity.FleetCapacity
import com.rutaexpress.catalog.model.entity.RegistroComandoCapacidad
import com.rutaexpress.catalog.model.enums.VehicleType
import com.rutaexpress.catalog.model.enums.ZoneType
import com.rutaexpress.catalog.model.repository.FleetCapacityRepository
import com.rutaexpress.catalog.model.repository.RegistroComandoCapacidadRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.cache.CacheManager
import tools.jackson.databind.json.JsonMapper
import java.util.Optional

class CapacidadComandoTests {
    private val mapper: JsonMapper = JsonMapper.builder().findAndAddModules().build()
    private val nombres = RabbitNombres()

    @Test
    fun `reserva el cupo de la linea preferida y registra el resultado`() {
        val capacidades = mock<FleetCapacityRepository>()
        val registros = mock<RegistroComandoCapacidadRepository>()
        val linea = linea(VehicleType.VAN, usados = 0)
        whenever(registros.findById("cmd-1")).thenReturn(Optional.empty())
        whenever(capacidades.findLockedByServiceIdAndZoneAndVehicleType(1L, ZoneType.URBAN_CENTER, VehicleType.VAN))
            .thenReturn(Optional.of(linea))

        val resultado = procesador(capacidades, registros).procesar(reserva("cmd-1", vehicleType = "VAN", paquetes = 3))

        assertThat(resultado.estado).isEqualTo(EstadoCapacidad.RESERVADA)
        assertThat(resultado.vehicleType).isEqualTo("VAN")
        assertThat(linea.currentBookedPackages).isEqualTo(3)
        assertThat(resultado.capacidadRestante).isEqualTo(7)
        val registro = argumentCaptor<RegistroComandoCapacidad>()
        verify(registros).save(registro.capture())
        assertThat(registro.firstValue.resultado).isEqualTo("RESERVADA")
    }

    @Test
    fun `sin capacidad responde RECHAZADA y no mueve el cupo`() {
        val capacidades = mock<FleetCapacityRepository>()
        val registros = mock<RegistroComandoCapacidadRepository>()
        val llena = linea(VehicleType.VAN, usados = 10)
        whenever(registros.findById("cmd-2")).thenReturn(Optional.empty())
        whenever(capacidades.findLockedByServiceIdAndZoneAndVehicleType(1L, ZoneType.URBAN_CENTER, VehicleType.VAN))
            .thenReturn(Optional.of(llena))
        whenever(capacidades.findAvailableCapacities(1L, ZoneType.URBAN_CENTER, 2.0, 0.1)).thenReturn(emptyList())

        val resultado = procesador(capacidades, registros).procesar(reserva("cmd-2", vehicleType = "VAN", paquetes = 1))

        assertThat(resultado.estado).isEqualTo(EstadoCapacidad.RECHAZADA)
        assertThat(llena.currentBookedPackages).isEqualTo(10)
    }

    @Test
    fun `un comando ya procesado devuelve el resultado guardado sin tocar la capacidad`() {
        val capacidades = mock<FleetCapacityRepository>()
        val registros = mock<RegistroComandoCapacidadRepository>()
        whenever(registros.findById("cmd-3")).thenReturn(
            Optional.of(RegistroComandoCapacidad("cmd-3", "RESERVAR", "RX-3", "RESERVADA", "VAN", 4, "Capacidad reservada"))
        )

        val resultado = procesador(capacidades, registros).procesar(reserva("cmd-3", vehicleType = "VAN", paquetes = 1))

        assertThat(resultado.estado).isEqualTo(EstadoCapacidad.RESERVADA)
        assertThat(resultado.capacidadRestante).isEqualTo(4)
        verifyNoInteractions(capacidades)
        verify(registros, never()).save(any())
    }

    @Test
    fun `liberar nunca deja el cupo en negativo`() {
        val capacidades = mock<FleetCapacityRepository>()
        val registros = mock<RegistroComandoCapacidadRepository>()
        val linea = linea(VehicleType.VAN, usados = 1)
        whenever(registros.findById("cmd-4")).thenReturn(Optional.empty())
        whenever(capacidades.findLockedByServiceIdAndZoneAndVehicleType(1L, ZoneType.URBAN_CENTER, VehicleType.VAN))
            .thenReturn(Optional.of(linea))

        val resultado = procesador(capacidades, registros).procesar(
            reserva("cmd-4", vehicleType = "VAN", paquetes = 5, tipo = TipoComando.LIBERAR)
        )

        assertThat(resultado.estado).isEqualTo(EstadoCapacidad.LIBERADA)
        assertThat(linea.currentBookedPackages).isZero()
    }

    @Test
    fun `un comando con zona desconocida es un error no recuperable para el consumidor`() {
        val capacidades = mock<FleetCapacityRepository>()
        val registros = mock<RegistroComandoCapacidadRepository>()
        whenever(registros.findById("cmd-5")).thenReturn(Optional.empty())

        val consumidor = listener(procesador(capacidades, registros), publicadorQue(ok = true))
        val canal = mock<Channel>()
        consumidor.onComando(mensaje(7, "capacity.reserve", reservaJson("cmd-5", zona = "MARTE")), canal)

        verify(canal).basicNack(7, false, false)
        verify(canal, never()).basicAck(any(), any())
    }

    @Test
    fun `procesa y publica el resultado antes de confirmar con ACK`() {
        val capacidades = mock<FleetCapacityRepository>()
        val registros = mock<RegistroComandoCapacidadRepository>()
        val publicador = publicadorQue(ok = true)
        whenever(registros.findById("cmd-6")).thenReturn(Optional.empty())
        whenever(capacidades.findLockedByServiceIdAndZoneAndVehicleType(1L, ZoneType.URBAN_CENTER, VehicleType.VAN))
            .thenReturn(Optional.of(linea(VehicleType.VAN, usados = 0)))
        val canal = mock<Channel>()

        listener(procesador(capacidades, registros), publicador)
            .onComando(mensaje(8, "capacity.reserve", reservaJson("cmd-6", vehiculo = "VAN")), canal)

        verify(publicador).publicar(any())
        verify(canal).basicAck(8, false)
        verify(canal, never()).basicNack(any(), any(), any())
    }

    @Test
    fun `si el broker nunca confirma el resultado el comando va a la DLQ tras los reintentos`() {
        val capacidades = mock<FleetCapacityRepository>()
        val registros = mock<RegistroComandoCapacidadRepository>()
        val publicador = publicadorQue(ok = false)
        whenever(registros.findById("cmd-7")).thenReturn(Optional.empty())
        whenever(capacidades.findLockedByServiceIdAndZoneAndVehicleType(1L, ZoneType.URBAN_CENTER, VehicleType.VAN))
            .thenReturn(Optional.of(linea(VehicleType.VAN, usados = 0)))
        val canal = mock<Channel>()

        listener(procesador(capacidades, registros), publicador, intentos = 3)
            .onComando(mensaje(9, "capacity.reserve", reservaJson("cmd-7", vehiculo = "VAN")), canal)

        verify(publicador, times(3)).publicar(any())
        verify(canal).basicNack(9, false, false)
    }

    private fun procesador(capacidades: FleetCapacityRepository, registros: RegistroComandoCapacidadRepository) =
        CapacidadComandoProcesador(capacidades, registros, mock<CacheManager>())

    private fun publicadorQue(ok: Boolean): CapacidadEventoPublicador {
        val publicador = mock<CapacidadEventoPublicador>()
        if (!ok) {
            doThrow(MensajeriaNoDisponibleException("broker caído")).`when`(publicador).publicar(any())
        }
        return publicador
    }

    private fun listener(
        procesador: CapacidadComandoProcesador,
        publicador: CapacidadEventoPublicador,
        intentos: Int = 3,
    ) = CapacidadComandoListener(
        procesador,
        publicador,
        nombres.copy(consumer = nombres.consumer.copy(maxAttempts = intentos, initialIntervalMs = 0)),
        mapper,
    )

    private fun linea(tipo: VehicleType, usados: Int) = FleetCapacity(
        id = 1L,
        service = null,
        zone = ZoneType.URBAN_CENTER,
        vehicleType = tipo,
        maxDailyPackages = 10,
        currentBookedPackages = usados,
        maxWeightKg = 100.0,
        maxVolumeM3 = 10.0,
        active = true,
    )

    private fun reserva(
        commandId: String,
        vehicleType: String?,
        paquetes: Int,
        tipo: TipoComando = TipoComando.RESERVAR,
    ) = ComandoCapacidad(
        commandId = commandId,
        tipo = tipo,
        shipmentId = "RX-${commandId}",
        serviceId = 1L,
        zona = "URBAN_CENTER",
        vehicleType = vehicleType,
        packagesCount = paquetes,
        weightKg = 2.0,
        volumeM3 = 0.1,
    )

    private fun reservaJson(
        commandId: String,
        zona: String = "URBAN_CENTER",
        vehiculo: String? = null,
    ): String = mapper.writeValueAsString(
        mapOf(
            "type" to "capacity.reserve.requested",
            "eventId" to commandId,
            "timestamp" to "2026-09-10T12:00:00Z",
            "traceId" to "trace",
            "correlationId" to "RX-$commandId",
            "payload" to mapOf(
                "shipmentId" to "RX-$commandId",
                "serviceId" to 1,
                "zone" to zona,
                "vehicleType" to vehiculo,
                "packagesCount" to 3,
                "weightKg" to 2.0,
                "volumeM3" to 0.1,
            ),
        )
    )

    private fun mensaje(etiqueta: Long, clave: String, cuerpo: String) = Message(
        cuerpo.toByteArray(),
        MessageProperties().apply {
            deliveryTag = etiqueta
            receivedRoutingKey = clave
            messageId = "msg-$etiqueta"
        },
    )
}
