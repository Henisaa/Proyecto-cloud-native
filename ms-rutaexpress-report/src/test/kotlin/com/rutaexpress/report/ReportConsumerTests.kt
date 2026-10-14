package com.rutaexpress.report
import com.rutaexpress.report.consumidor.EnvioEventoConsumidor
import com.rutaexpress.report.repositorio.ReporteEnvioRepositorio
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertThrows
import org.mockito.Mockito.mock
import tools.jackson.databind.json.JsonMapper
class ReportConsumerTests {
    @Test fun `propaga JSON invalido para recuperar mediante DLT`() {
        val consumer = EnvioEventoConsumidor(mock(ReporteEnvioRepositorio::class.java), JsonMapper.builder().findAndAddModules().build())
        assertThrows(Exception::class.java) { consumer.consumirEventoEnvio("invalid-json") }
    }
}
