package com.rutaexpress.report

import com.rutaexpress.report.dominio.SnapshotEnvio
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.hibernate.cfg.Configuration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.oracle.OracleContainer
import java.math.BigDecimal
import java.time.OffsetDateTime

@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_DOCKER_TESTS", matches = "true")
class FlywayOracleTests {
    @Test
    fun `migracion Oracle valida el modelo Hibernate y conserva minutos decimales`() {
        val flyway = Flyway.configure().dataSource(oracle.jdbcUrl, oracle.username, oracle.password)
            .locations("classpath:db/migration").load()
        assertThat(flyway.migrate().success).isTrue()
        val configuration = Configuration().addAnnotatedClass(SnapshotEnvio::class.java)
            .setProperty("hibernate.connection.url", oracle.jdbcUrl)
            .setProperty("hibernate.connection.username", oracle.username)
            .setProperty("hibernate.connection.password", oracle.password)
            .setProperty("hibernate.hbm2ddl.auto", "validate")
        configuration.buildSessionFactory().use { factory ->
            factory.openSession().use { session ->
                val tx = session.beginTransaction()
                session.persist(SnapshotEnvio("oracle-ci", "EXPRESS", "ENTREGADO", OffsetDateTime.now(),
                    leadTimeMinutes = BigDecimal("12.50")))
                tx.commit()
                session.clear()
                val stored = session.find(SnapshotEnvio::class.java, "oracle-ci")
                assertThat(stored.leadTimeMinutes).isEqualByComparingTo("12.50")
            }
        }
    }
    companion object {
        @Container @JvmStatic val oracle = OracleContainer("gvenzl/oracle-free:23-slim")
    }
}
