package com.rutaexpress.audit
import com.rutaexpress.audit.consumidor.AuditoriaKafkaConsumidor
import com.rutaexpress.audit.dominio.EventoAuditoria
import com.rutaexpress.audit.repositorio.AuditoriaRepositorio
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertThrows
import org.mockito.Mockito.*
import org.mockito.ArgumentCaptor
import org.assertj.core.api.Assertions.assertThat
import tools.jackson.databind.json.JsonMapper
class AuditConsumerTests {
    private val repository = mock(AuditoriaRepositorio::class.java)
    private val consumer = AuditoriaKafkaConsumidor(repository, JsonMapper.builder().findAndAddModules().build())
    private val json = """{"eventId":"evt-1","shipmentId":"env-1","timestamp":"2026-10-14T12:00:00Z","actor":"dispatcher","status":"ACEPTADO"}"""
    @Test fun `conserva identidad y fecha del evento original`() {
        consumer.consumirEvento(ConsumerRecord("shipments.events",0,0,"env-1",json))
        val captured = ArgumentCaptor.forClass(EventoAuditoria::class.java)
        verify(repository).save(captured.capture())
        assertThat(captured.value.usuarioResponsable).isEqualTo("dispatcher")
        assertThat(captured.value.eventId).isEqualTo("evt-1")
        assertThat(captured.value.idEntidad).isEqualTo("env-1")
    }
    @Test fun `omite evento ya persistido desde otro topic`() {
        `when`(repository.existsByEventId("evt-1")).thenReturn(true)
        consumer.consumirEvento(ConsumerRecord("audit.timeline",0,0,"evt-1",json))
        verify(repository,never()).save(any(EventoAuditoria::class.java))
    }
    @Test fun `propaga evento invalido para reintentar y enviar a DLT`() {
        assertThrows(IllegalArgumentException::class.java) { consumer.consumirEvento(ConsumerRecord("shipments.events",0,0,"env-1","{}")) }
    }
}
