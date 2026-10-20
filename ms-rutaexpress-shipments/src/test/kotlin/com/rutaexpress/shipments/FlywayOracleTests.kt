package com.rutaexpress.shipments

import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.oracle.OracleContainer

/**
 * Las migraciones de shipments se prueban sobre Oracle real (no H2). Se activan con RUN_DOCKER_TESTS=true
 * (job de integración del CI). Regla: nunca editar una migración ya publicada.
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_DOCKER_TESTS", matches = "true")
class FlywayOracleTests {

    @Test
    fun `aplica todas las migraciones sobre una base vacia`() {
        val flyway = configuracion().load()
        flyway.clean()

        val resultado = flyway.migrate()

        assertThat(resultado.success).isTrue()
        assertThat(flyway.info().current().version.version).isEqualTo("4")
    }

    @Test
    fun `aplica V3 y V4 sobre una base que ya tenia la V2 original`() {
        configuracion().load().clean()
        configuracion().target("2").load().migrate()

        val completo = configuracion().load()
        val resultado = completo.migrate()

        assertThat(resultado.success).isTrue()
        assertThat(resultado.migrationsExecuted).isEqualTo(2)
        assertThat(completo.info().current().version.version).isEqualTo("4")
    }

    private fun configuracion(): FluentConfiguration = Flyway.configure()
        .dataSource(oracle.jdbcUrl, oracle.username, oracle.password)
        .locations("classpath:db/migration")
        .cleanDisabled(false)

    companion object {
        @Container
        @JvmStatic
        val oracle = OracleContainer("gvenzl/oracle-free:23-slim")
    }
}
