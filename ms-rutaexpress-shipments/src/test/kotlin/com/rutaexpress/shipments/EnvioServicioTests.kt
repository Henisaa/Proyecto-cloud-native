package com.rutaexpress.shipments

import com.rutaexpress.shipments.cliente.CatalogoCliente
import com.rutaexpress.shipments.dominio.Envio
import com.rutaexpress.shipments.dominio.EnvioRepositorio
import com.rutaexpress.shipments.dominio.EstadoCapacidad
import com.rutaexpress.shipments.dominio.EstadoEnvio
import com.rutaexpress.shipments.dominio.RegistroAuditoriaRepositorio
import com.rutaexpress.shipments.eventos.EnvioEventPublisher
import com.rutaexpress.shipments.mensajeria.IdsComando
import com.rutaexpress.shipments.mensajeria.MensajeriaNoDisponibleException
import com.rutaexpress.shipments.mensajeria.MensajeriaPublicador
import com.rutaexpress.shipments.servicio.EnvioNoEncontradoException
import com.rutaexpress.shipments.servicio.EnvioServicio
import com.rutaexpress.shipments.servicio.TransicionInvalidaException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.inOrder
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.Optional

class EnvioServicioTests {
    private val repositorio = mock<EnvioRepositorio>()
    private val registros = mock<RegistroAuditoriaRepositorio>()
    private val eventos = mock<EnvioEventPublisher>()
    private val mensajeria = mock<MensajeriaPublicador>()
    private val servicio = EnvioServicio(
        repositorio,
        registros,
        CatalogoCliente(mock(), true),
        eventos,
        mensajeria,
        TransactionTemplate(mock<PlatformTransactionManager>()),
    )

    @AfterEach
    fun limpiarContexto() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `publica el comando de reserva despues de estados y notificaciones`() {
        val envio = envio(EstadoEnvio.CREADO)
        prepararGuardado(envio)

        servicio.cambiarEstado("shipment-1", "ACEPTADO", "token")

        val orden = inOrder(mensajeria, eventos)
        orden.verify(mensajeria).publicarEstado(any(), any())
        orden.verify(eventos).publicarCambioEstado(any(), any())
        orden.verify(mensajeria).enviarEmailEstado(any(), any(), any(), any())
        orden.verify(mensajeria).solicitarReserva(any(), any())
        assertThat(envio.capacityStatus).isEqualTo(EstadoCapacidad.PENDIENTE)
        assertThat(envio.lastReserveCommandId).isNotBlank()
    }

    @Test
    fun `el comando de reserva usa el commandId guardado en el envio`() {
        val envio = envio(EstadoEnvio.CREADO)
        prepararGuardado(envio)

        servicio.cambiarEstado("shipment-1", "ACEPTADO", "token")

        verify(mensajeria).solicitarReserva(any(), eq(envio.lastReserveCommandId!!))
    }

    @Test
    fun `con la capacidad rechazada no se puede pasar a en bodega`() {
        val envio = envio(EstadoEnvio.ACEPTADO, EstadoCapacidad.RECHAZADA)
        prepararGuardado(envio)

        assertThatThrownBy { servicio.cambiarEstado("shipment-1", "EN_BODEGA", "token") }
            .isInstanceOf(TransicionInvalidaException::class.java)
            .hasMessageContaining("rechazada")

        assertThat(envio.status).isEqualTo(EstadoEnvio.ACEPTADO)
        verifyNoInteractions(mensajeria)
    }

    @Test
    fun `con la capacidad pendiente no se puede pasar a en bodega`() {
        prepararGuardado(envio(EstadoEnvio.ACEPTADO, EstadoCapacidad.PENDIENTE))

        assertThatThrownBy { servicio.cambiarEstado("shipment-1", "EN_BODEGA", "token") }
            .isInstanceOf(TransicionInvalidaException::class.java)
            .hasMessageContaining("pendiente")
    }

    @Test
    fun `con la capacidad reservada si se puede pasar a en bodega`() {
        val envio = envio(EstadoEnvio.ACEPTADO, EstadoCapacidad.RESERVADA)
        prepararGuardado(envio)

        servicio.cambiarEstado("shipment-1", "EN_BODEGA", "token")

        assertThat(envio.status).isEqualTo(EstadoEnvio.EN_BODEGA)
        verify(mensajeria).generarTicketBodega(any())
    }

    @Test
    fun `cancelar con cupo reservado publica la liberacion al final con un id determinista`() {
        val envio = envio(EstadoEnvio.ACEPTADO, EstadoCapacidad.RESERVADA)
        prepararGuardado(envio)

        servicio.cambiarEstado("shipment-1", "CANCELADO", "token")

        val orden = inOrder(mensajeria, eventos)
        orden.verify(mensajeria).publicarEstado(any(), any())
        orden.verify(eventos).publicarCambioEstado(any(), any())
        orden.verify(mensajeria).solicitarLiberacion(any(), eq(IdsComando.liberacionPorCancelacion("shipment-1")))
    }

    @Test
    fun `si falla la publicacion del estado no se publica ninguna liberacion`() {
        val envio = envio(EstadoEnvio.ACEPTADO, EstadoCapacidad.RESERVADA)
        prepararGuardado(envio)
        doThrow(MensajeriaNoDisponibleException("broker caído")).whenever(mensajeria).publicarEstado(any(), any())

        assertThatThrownBy { servicio.cambiarEstado("shipment-1", "CANCELADO", "token") }
            .isInstanceOf(MensajeriaNoDisponibleException::class.java)

        verify(mensajeria, never()).solicitarLiberacion(any(), any())
    }

    @Test
    fun `cancelar sin cupo reservado no pide liberacion`() {
        prepararGuardado(envio(EstadoEnvio.ACEPTADO, EstadoCapacidad.PENDIENTE))

        servicio.cambiarEstado("shipment-1", "CANCELADO", "token")

        verify(mensajeria, never()).solicitarLiberacion(any(), any())
    }

    @Test
    fun `reintentar la reserva de un envio rechazado vuelve a pendiente y publica un comando nuevo`() {
        val envio = envio(EstadoEnvio.ACEPTADO, EstadoCapacidad.RECHAZADA).apply { lastReserveCommandId = "viejo" }
        prepararGuardado(envio)

        servicio.reintentarReserva("shipment-1")

        assertThat(envio.capacityStatus).isEqualTo(EstadoCapacidad.PENDIENTE)
        assertThat(envio.lastReserveCommandId).isNotEqualTo("viejo")
        verify(mensajeria).solicitarReserva(any(), eq(envio.lastReserveCommandId!!))
    }

    @Test
    fun `solo se reintenta la reserva cuando esta rechazada`() {
        prepararGuardado(envio(EstadoEnvio.ACEPTADO, EstadoCapacidad.RESERVADA))

        assertThatThrownBy { servicio.reintentarReserva("shipment-1") }
            .isInstanceOf(TransicionInvalidaException::class.java)
        verifyNoInteractions(mensajeria)
    }

    @Test
    fun `un cliente no puede ver el envio de otro cliente`() {
        autenticarCliente("oid-cliente-a")
        prepararGuardado(envio(EstadoEnvio.CREADO).apply { ownerOid = "oid-cliente-b" })

        assertThatThrownBy { servicio.obtener("shipment-1") }
            .isInstanceOf(EnvioNoEncontradoException::class.java)
    }

    @Test
    fun `un cliente ve su propio envio`() {
        autenticarCliente("oid-cliente-a")
        prepararGuardado(envio(EstadoEnvio.CREADO).apply { ownerOid = "oid-cliente-a" })

        assertThat(servicio.obtener("shipment-1").id).isEqualTo("shipment-1")
    }

    @Test
    fun `un cliente tampoco ve los envios antiguos sin dueno`() {
        autenticarCliente("oid-cliente-a")
        prepararGuardado(envio(EstadoEnvio.CREADO))

        assertThatThrownBy { servicio.obtener("shipment-1") }
            .isInstanceOf(EnvioNoEncontradoException::class.java)
    }

    @Test
    fun `un despachador ve cualquier envio`() {
        autenticar("oid-despachador", "DESPACHADOR")
        prepararGuardado(envio(EstadoEnvio.CREADO).apply { ownerOid = "oid-cliente-b" })

        assertThat(servicio.obtener("shipment-1").id).isEqualTo("shipment-1")
    }

    private fun prepararGuardado(envio: Envio) {
        whenever(repositorio.findById("shipment-1")).thenReturn(Optional.of(envio))
        whenever(repositorio.saveAndFlush(any<Envio>())).thenAnswer { it.getArgument<Envio>(0) }
    }

    private fun envio(estado: EstadoEnvio, capacidad: EstadoCapacidad = EstadoCapacidad.NO_APLICA) = Envio(
        id = "shipment-1",
        trackingCode = "RX-TEST1234",
        status = estado,
        capacityStatus = capacidad,
        recipientName = "Ana",
        recipientEmail = "ana@example.com",
    )

    private fun autenticarCliente(oid: String) = autenticar(oid, "CLIENTE")

    private fun autenticar(oid: String, rol: String) {
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("oid", oid)
            .claim("preferred_username", "$oid@rutaexpress.local")
            .build()
        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("ROLE_$rol")), "$oid@rutaexpress.local")
    }
}
