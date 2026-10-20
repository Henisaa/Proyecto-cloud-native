package com.rutaexpress.catalog.messaging

import com.rabbitmq.client.Channel
import com.rutaexpress.catalog.config.RabbitNombres
import com.rutaexpress.catalog.model.entity.EstadoReserva
import com.rutaexpress.catalog.model.entity.FleetCapacity
import com.rutaexpress.catalog.model.entity.RegistroComandoCapacidad
import com.rutaexpress.catalog.model.entity.ReservaCapacidad
import com.rutaexpress.catalog.model.enums.VehicleType
import com.rutaexpress.catalog.model.enums.ZoneType
import com.rutaexpress.catalog.model.repository.FleetCapacityRepository
import com.rutaexpress.catalog.model.repository.RegistroComandoCapacidadRepository
import com.rutaexpress.catalog.model.repository.ReservaCapacidadRepository
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

    private val capacidades = mock<FleetCapacityRepository>()
    private val registros = mock<RegistroComandoCapacidadRepository>()
    private val reservas = mock<ReservaCapacidadRepository>()

    @Test
    fun `reserva el cupo de la linea preferida y guarda la reserva del envio`() {
        val linea = linea(VehicleType.VAN, usados = 0)
        whenever(registros.findById("cmd-1")).thenReturn(Optional.empty())
        whenever(capacidades.findLockedAvailable(1L, ZoneType.URBAN_CENTER, 2.0, 0.1))
            .thenReturn(listOf(linea))

        val resultado = procesador().procesar(reserva("cmd-1", vehicleType = "VAN", paquetes = 3))

        assertThat(resultado.estado).isEqualTo(EstadoCapacidad.RESERVADA)
        assertThat(resultado.vehicleType).isEqualTo("VAN")
        assertThat(linea.currentBookedPackages).isEqualTo(3)
        assertThat(resultado.capacidadRestante).isEqualTo(7)
        val registro = argumentCaptor<RegistroComandoCapacidad>()
        verify(registros).save(registro.capture())
        assertThat(registro.firstValue.resultado).isEqualTo("RESERVADA")
        val reserva = argumentCaptor<ReservaCapacidad>()
        verify(reservas).saveAndFlush(reserva.capture())
        assertThat(reserva.firstValue.shipmentId).isEqualTo("RX-cmd-1")
        assertThat(reserva.firstValue.capacityId).isEqualTo(1L)
        assertThat(reserva.firstValue.paquetes).isEqualTo(3)
        assertThat(reserva.firstValue.estado).isEqualTo(EstadoReserva.ACTIVA)
    }

    @Test
    fun `sin capacidad responde RECHAZADA y no mueve el cupo ni crea reserva`() {
        val llena = linea(VehicleType.VAN, usados = 10)
        whenever(registros.findById("cmd-2")).thenReturn(Optional.empty())
        whenever(capacidades.findLockedAvailable(1L, ZoneType.URBAN_CENTER, 2.0, 0.1))
            .thenReturn(listOf(llena))

        val resultado = procesador().procesar(reserva("cmd-2", vehicleType = "VAN", paquetes = 1))

        assertThat(resultado.estado).isEqualTo(EstadoCapacidad.RECHAZADA)
        assertThat(llena.currentBookedPackages).isEqualTo(10)
        verify(reservas, never()).saveAndFlush(any<ReservaCapacidad>())
    }

    @Test
    fun `si la linea preferida no alcanza usa la primera alternativa con cupo`() {
        val llena = linea(VehicleType.VAN, usados = 10)
        val alternativa = linea(VehicleType.TRUCK, usados = 0).also { it.id = 2L }
        whenever(registros.findById("cmd-alt")).thenReturn(Optional.empty())
        whenever(capacidades.findLockedAvailable(1L, ZoneType.URBAN_CENTER, 2.0, 0.1)).thenReturn(listOf(llena, alternativa))

        val resultado = procesador().procesar(reserva("cmd-alt", vehicleType = "VAN", paquetes = 2))

        assertThat(resultado.estado).isEqualTo(EstadoCapacidad.RESERVADA)
        assertThat(resultado.vehicleType).isEqualTo("TRUCK")
        assertThat(alternativa.currentBookedPackages).isEqualTo(2)
        assertThat(llena.currentBookedPackages).isEqualTo(10)
    }

    @Test
    fun `un comando ya procesado devuelve el resultado guardado sin tocar la capacidad`() {
        whenever(registros.findById("cmd-3")).thenReturn(
            Optional.of(RegistroComandoCapacidad("cmd-3", "RESERVAR", "RX-3", "RESERVADA", "VAN", 4, "Capacidad reservada"))
        )

        val resultado = procesador().procesar(reserva("cmd-3", vehicleType = "VAN", paquetes = 1))

        assertThat(resultado.estado).isEqualTo(EstadoCapacidad.RESERVADA)
        assertThat(resultado.capacidadRestante).isEqualTo(4)
        verifyNoInteractions(capacidades)
        verifyNoInteractions(reservas)
        verify(registros, never()).save(any())
    }

    @Test
    fun `otro commandId del mismo envio devuelve la reserva existente sin volver a descontar`() {
        val linea = linea(VehicleType.VAN, usados = 3)
        whenever(registros.findById("cmd-new")).thenReturn(Optional.empty())
        whenever(reservas.findLockedByShipmentId("shipment-1")).thenReturn(
            Optional.of(ReservaCapacidad("shipment-1", 1L, 3, "VAN", EstadoReserva.ACTIVA, "cmd-old"))
        )
        whenever(capacidades.findById(1L)).thenReturn(Optional.of(linea))

        val resultado = procesador().procesar(
            reserva("cmd-new", vehicleType = "VAN", paquetes = 3).copy(shipmentId = "shipment-1"),
        )

        assertThat(resultado.estado).isEqualTo(EstadoCapacidad.RESERVADA)
        assertThat(linea.currentBookedPackages).isEqualTo(3)
        val registro = argumentCaptor<RegistroComandoCapacidad>()
        verify(registros).save(registro.capture())
        assertThat(registro.firstValue.commandId).isEqualTo("cmd-new")
        verify(reservas, never()).saveAndFlush(any<ReservaCapacidad>())
    }

    @Test
    fun `liberar descuenta en la linea que se reservo y marca la reserva liberada`() {
        val reservada = linea(VehicleType.TRUCK, usados = 5).also { it.id = 7L }
        val reserva = ReservaCapacidad("RX-cmd-4", 7L, 2, "TRUCK", EstadoReserva.ACTIVA, "cmd-r")
        whenever(registros.findById("cmd-4")).thenReturn(Optional.empty())
        whenever(reservas.findLockedByShipmentId("RX-cmd-4")).thenReturn(Optional.of(reserva))
        whenever(capacidades.findLockedById(7L)).thenReturn(Optional.of(reservada))

        // El comando dice VAN y 5 paquetes, pero se libera lo realmente reservado: 2 en la línea TRUCK.
        val resultado = procesador().procesar(
            reserva("cmd-4", vehicleType = "VAN", paquetes = 5, tipo = TipoComando.LIBERAR)
        )

        assertThat(resultado.estado).isEqualTo(EstadoCapacidad.LIBERADA)
        assertThat(reservada.currentBookedPackages).isEqualTo(3)
        assertThat(reserva.estado).isEqualTo(EstadoReserva.LIBERADA)
        assertThat(reserva.liberacionCommandId).isEqualTo("cmd-4")
    }

    @Test
    fun `una segunda liberacion del mismo envio no vuelve a descontar`() {
        val linea = linea(VehicleType.VAN, usados = 4)
        val liberada = ReservaCapacidad("RX-cmd-5", 1L, 2, "VAN", EstadoReserva.LIBERADA, "cmd-r", "cmd-previo")
        whenever(registros.findById("cmd-5")).thenReturn(Optional.empty())
        whenever(reservas.findLockedByShipmentId("RX-cmd-5")).thenReturn(Optional.of(liberada))

        val resultado = procesador().procesar(reserva("cmd-5", vehicleType = "VAN", paquetes = 2, tipo = TipoComando.LIBERAR))

        assertThat(resultado.estado).isEqualTo(EstadoCapacidad.LIBERADA)
        assertThat(linea.currentBookedPackages).isEqualTo(4)
        verify(capacidades, never()).findLockedById(any())
    }

    @Test
    fun `liberar un envio sin reserva no cambia ningun contador`() {
        whenever(registros.findById("cmd-6")).thenReturn(Optional.empty())

        val resultado = procesador().procesar(reserva("cmd-6", vehicleType = "VAN", paquetes = 5, tipo = TipoComando.LIBERAR))

        assertThat(resultado.estado).isEqualTo(EstadoCapacidad.LIBERADA)
        verifyNoInteractions(capacidades)
    }

    @Test
    fun `liberar nunca deja el cupo en negativo`() {
        val linea = linea(VehicleType.VAN, usados = 1)
        whenever(registros.findById("cmd-4b")).thenReturn(Optional.empty())
        whenever(reservas.findLockedByShipmentId("RX-cmd-4b"))
            .thenReturn(Optional.of(ReservaCapacidad("RX-cmd-4b", 1L, 5, "VAN", EstadoReserva.ACTIVA, "cmd-r")))
        whenever(capacidades.findLockedById(1L)).thenReturn(Optional.of(linea))

        procesador().procesar(reserva("cmd-4b", vehicleType = "VAN", paquetes = 5, tipo = TipoComando.LIBERAR))

        assertThat(linea.currentBookedPackages).isZero()
    }

    @Test
    fun `un comando con zona desconocida es un error no recuperable para el consumidor`() {
        whenever(registros.findById("cmd-5")).thenReturn(Optional.empty())

        val consumidor = listener(procesador(), publicadorQue(ok = true))
        val canal = mock<Channel>()
        consumidor.onComando(mensaje(7, "capacity.reserve", reservaJson("cmd-5", zona = "MARTE")), canal)

        verify(canal).basicNack(7, false, false)
        verify(canal, never()).basicAck(any(), any())
    }

    @Test
    fun `procesa y publica el resultado antes de confirmar con ACK`() {
        val publicador = publicadorQue(ok = true)
        whenever(registros.findById("cmd-6")).thenReturn(Optional.empty())
        whenever(capacidades.findLockedAvailable(1L, ZoneType.URBAN_CENTER, 2.0, 0.1))
            .thenReturn(listOf(linea(VehicleType.VAN, usados = 0)))
        val canal = mock<Channel>()

        listener(procesador(), publicador)
            .onComando(mensaje(8, "capacity.reserve", reservaJson("cmd-6", vehiculo = "VAN")), canal)

        verify(publicador).publicar(any())
        verify(canal).basicAck(8, false)
        verify(canal, never()).basicNack(any(), any(), any())
    }

    @Test
    fun `si el broker nunca confirma el resultado el comando va a la DLQ tras los reintentos`() {
        val publicador = publicadorQue(ok = false)
        whenever(registros.findById("cmd-7")).thenReturn(Optional.empty())
        whenever(capacidades.findLockedAvailable(1L, ZoneType.URBAN_CENTER, 2.0, 0.1))
            .thenReturn(listOf(linea(VehicleType.VAN, usados = 0)))
        val canal = mock<Channel>()

        listener(procesador(), publicador, intentos = 3)
            .onComando(mensaje(9, "capacity.reserve", reservaJson("cmd-7", vehiculo = "VAN")), canal)

        verify(publicador, times(3)).publicar(any())
        verify(canal).basicNack(9, false, false)
    }

    private fun procesador() = CapacidadComandoProcesador(capacidades, registros, reservas, mock<CacheManager>())

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
