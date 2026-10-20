package com.rutaexpress.shipments.mensajeria

import com.rutaexpress.shipments.dominio.Envio
import com.rutaexpress.shipments.dominio.EnvioRepositorio
import com.rutaexpress.shipments.dominio.EstadoCapacidad
import com.rutaexpress.shipments.dominio.EstadoEnvio
import com.rutaexpress.shipments.dominio.RegistroAuditoriaEnvio
import com.rutaexpress.shipments.dominio.RegistroAuditoriaRepositorio
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper
import java.util.Optional

/** Idempotencia y orden de los resultados de capacidad (P1-02). */
class AuditoriaEnvioServiceTests {
    private val mapper: JsonMapper = JsonMapper.builder().findAndAddModules().build()
    private val envios = mock<EnvioRepositorio>()
    private val registros = mock<RegistroAuditoriaRepositorio>()
    private val servicio = AuditoriaEnvioService(envios, registros)

    @Test
    fun `una reserva confirmada de un envio pendiente lo deja reservado`() {
        val envio = envio(EstadoEnvio.ACEPTADO, EstadoCapacidad.PENDIENTE, comando = "cmd-1")
        val resultado = aplicar(envio, "cmd-1", "RESERVADA")

        assertThat(envio.capacityStatus).isEqualTo(EstadoCapacidad.RESERVADA)
        assertThat(envio.capacityReserved).isTrue()
        assertThat(resultado.accion).isEqualTo(AccionPosterior.NINGUNA)
        verify(registros).save(any<RegistroAuditoriaEnvio>())
    }

    @Test
    fun `un resultado duplicado no cambia nada`() {
        val envio = envio(EstadoEnvio.ACEPTADO, EstadoCapacidad.LIBERADA, comando = "cmd-1")
        whenever(registros.existsById("cmd-1:resultado")).thenReturn(true)

        val resultado = servicio.aplicarResultadoCapacidad(
            sobre("cmd-1:resultado", "RESERVADA"), "catalog.capacity.reserved"
        )

        assertThat(resultado.accion).isEqualTo(AccionPosterior.NINGUNA)
        assertThat(envio.capacityStatus).isEqualTo(EstadoCapacidad.LIBERADA)
        verify(envios, never()).save(any<Envio>())
        verify(registros, never()).save(any<RegistroAuditoriaEnvio>())
    }

    @Test
    fun `una reserva atrasada no devuelve a reservada un envio ya liberado`() {
        val envio = envio(EstadoEnvio.CANCELADO, EstadoCapacidad.LIBERADA, comando = "cmd-1")

        val resultado = aplicar(envio, "cmd-1", "RESERVADA")

        assertThat(envio.capacityStatus).isEqualTo(EstadoCapacidad.LIBERADA)
        assertThat(resultado.accion).isEqualTo(AccionPosterior.NINGUNA)
        val registro = argumentCaptor<RegistroAuditoriaEnvio>()
        verify(registros).save(registro.capture())
        assertThat(registro.firstValue.detalle).contains("ignorado")
    }

    @Test
    fun `un resultado de un comando anterior se descarta`() {
        val envio = envio(EstadoEnvio.ACEPTADO, EstadoCapacidad.PENDIENTE, comando = "cmd-nuevo")

        aplicar(envio, "cmd-viejo", "RECHAZADA")

        assertThat(envio.capacityStatus).isEqualTo(EstadoCapacidad.PENDIENTE)
    }

    @Test
    fun `una reserva que llega despues de cancelar pide una unica compensacion`() {
        val envio = envio(EstadoEnvio.CANCELADO, EstadoCapacidad.PENDIENTE, comando = "cmd-1")

        val resultado = aplicar(envio, "cmd-1", "RESERVADA")

        assertThat(envio.capacityStatus).isEqualTo(EstadoCapacidad.LIBERACION_PENDIENTE)
        assertThat(resultado.accion).isEqualTo(AccionPosterior.COMPENSAR)
        assertThat(resultado.envio).isSameAs(envio)

        // La reentrega del mismo evento ya no vuelve a compensar.
        whenever(registros.existsById("cmd-1:resultado")).thenReturn(true)
        val repetido = servicio.aplicarResultadoCapacidad(sobre("cmd-1:resultado", "RESERVADA"), "catalog.capacity.reserved")
        assertThat(repetido.accion).isEqualTo(AccionPosterior.NINGUNA)
    }

    @Test
    fun `una reserva rechazada avisa al cliente`() {
        val envio = envio(EstadoEnvio.ACEPTADO, EstadoCapacidad.PENDIENTE, comando = "cmd-1")

        val resultado = aplicar(envio, "cmd-1", "RECHAZADA")

        assertThat(envio.capacityStatus).isEqualTo(EstadoCapacidad.RECHAZADA)
        assertThat(resultado.accion).isEqualTo(AccionPosterior.NOTIFICAR_RECHAZO)
    }

    @Test
    fun `la liberacion confirmada cierra tanto la reservada como la pendiente de liberar`() {
        val reservada = envio(EstadoEnvio.CANCELADO, EstadoCapacidad.RESERVADA, comando = "cmd-1")
        aplicar(reservada, "cmd-liberar", "LIBERADA")
        assertThat(reservada.capacityStatus).isEqualTo(EstadoCapacidad.LIBERADA)

        val pendiente = envio(EstadoEnvio.CANCELADO, EstadoCapacidad.LIBERACION_PENDIENTE, comando = "cmd-1")
        aplicar(pendiente, "cmd-comp", "LIBERADA")
        assertThat(pendiente.capacityStatus).isEqualTo(EstadoCapacidad.LIBERADA)
    }

    @Test
    fun `una liberacion de un envio pendiente se ignora`() {
        val envio = envio(EstadoEnvio.CANCELADO, EstadoCapacidad.PENDIENTE, comando = "cmd-1")

        aplicar(envio, "cmd-liberar", "LIBERADA")

        assertThat(envio.capacityStatus).isEqualTo(EstadoCapacidad.PENDIENTE)
    }

    private fun aplicar(envio: Envio, comando: String, resultado: String): ResultadoAplicado {
        whenever(envios.findById(envio.id)).thenReturn(Optional.of(envio))
        return servicio.aplicarResultadoCapacidad(
            sobre("$comando:resultado", resultado, envio.id),
            "catalog.capacity.${resultado.lowercase()}",
        )
    }

    private fun sobre(eventId: String, resultado: String, envioId: String = "s-1") = mapper.readTree(
        """{"eventId":"$eventId","type":"catalog.capacity.x","timestamp":"2026-10-20T10:00:00Z",
           "payload":{"shipmentId":"$envioId","result":"$resultado","vehicleType":"VAN"}}"""
    )

    private fun envio(estado: EstadoEnvio, capacidad: EstadoCapacidad, comando: String?) = Envio(
        id = "s-1",
        trackingCode = "RX-TEST1234",
        status = estado,
        capacityStatus = capacidad,
        lastReserveCommandId = comando,
        recipientName = "Ana",
    )
}
