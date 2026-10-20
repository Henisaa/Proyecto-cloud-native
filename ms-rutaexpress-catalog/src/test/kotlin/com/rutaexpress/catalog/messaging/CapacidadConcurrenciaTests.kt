package com.rutaexpress.catalog.messaging

import com.rutaexpress.catalog.model.enums.ServiceCategory
import com.rutaexpress.catalog.model.enums.VehicleType
import com.rutaexpress.catalog.model.enums.ZoneType
import com.rutaexpress.catalog.model.repository.FleetCapacityRepository
import com.rutaexpress.catalog.model.repository.ReservaCapacidadRepository
import com.rutaexpress.catalog.viewmodel.request.CreateCapacityViewModel
import com.rutaexpress.catalog.viewmodel.request.CreateServiceViewModel
import com.rutaexpress.catalog.viewmodel.service.CatalogViewModelService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Concurrencia real sobre la base (H2): dos comandos del mismo envío no pueden reservar ni liberar dos veces.
 * No es @Transactional: cada hilo confirma su propia transacción.
 */
@SpringBootTest
@ActiveProfiles("test")
class CapacidadConcurrenciaTests {

    @Autowired private lateinit var procesador: CapacidadComandoProcesador
    @Autowired private lateinit var catalogo: CatalogViewModelService
    @Autowired private lateinit var capacidades: FleetCapacityRepository
    @Autowired private lateinit var reservas: ReservaCapacidadRepository

    @Test
    fun `dos reservas simultaneas del mismo envio descuentan una sola vez`() {
        val (servicioId, capacidadId) = crearLinea("SRV-CONC-RES")
        val envio = UUID.randomUUID().toString()

        ejecutarEnParalelo(
            { reintentando { procesador.procesar(comando(servicioId, envio, TipoComando.RESERVAR, 3)) } },
            { reintentando { procesador.procesar(comando(servicioId, envio, TipoComando.RESERVAR, 3)) } },
        )

        assertThat(capacidades.findById(capacidadId).get().currentBookedPackages).isEqualTo(3)
        assertThat(reservas.findById(envio)).isPresent
    }

    @Test
    fun `dos liberaciones simultaneas del mismo envio descuentan una sola vez`() {
        val (servicioId, capacidadId) = crearLinea("SRV-CONC-REL")
        val envioA = UUID.randomUUID().toString()
        val envioB = UUID.randomUUID().toString()
        procesador.procesar(comando(servicioId, envioA, TipoComando.RESERVAR, 3))
        procesador.procesar(comando(servicioId, envioB, TipoComando.RESERVAR, 2))
        assertThat(capacidades.findById(capacidadId).get().currentBookedPackages).isEqualTo(5)

        ejecutarEnParalelo(
            { reintentando { procesador.procesar(comando(servicioId, envioA, TipoComando.LIBERAR, 3)) } },
            { reintentando { procesador.procesar(comando(servicioId, envioA, TipoComando.LIBERAR, 3)) } },
        )

        // Solo se devolvieron los 3 paquetes del envío A; los 2 del envío B siguen reservados.
        assertThat(capacidades.findById(capacidadId).get().currentBookedPackages).isEqualTo(2)
    }

    private fun crearLinea(codigo: String): Pair<Long, Long> {
        val servicio = catalogo.createService(
            CreateServiceViewModel(code = codigo, name = "Servicio $codigo", category = ServiceCategory.EXPRESS)
        )
        val capacidad = catalogo.addCapacity(
            servicio.id,
            CreateCapacityViewModel(
                zone = ZoneType.URBAN_CENTER,
                vehicleType = VehicleType.VAN,
                maxDailyPackages = 50,
                maxWeightKg = 500.0,
                maxVolumeM3 = 20.0,
            )
        )
        return servicio.id to capacidad.id
    }

    private fun comando(servicioId: Long, envio: String, tipo: TipoComando, paquetes: Int) = ComandoCapacidad(
        commandId = UUID.randomUUID().toString(),
        tipo = tipo,
        shipmentId = envio,
        serviceId = servicioId,
        zona = ZoneType.URBAN_CENTER.name,
        vehicleType = VehicleType.VAN.name,
        packagesCount = paquetes,
        weightKg = 2.0,
        volumeM3 = 0.1,
    )

    /** El consumidor real reintenta los fallos recuperables (por ejemplo, la clave duplicada del perdedor de la carrera). */
    private fun reintentando(accion: () -> Unit) {
        var ultimo: RuntimeException? = null
        repeat(3) {
            try {
                accion()
                return
            } catch (ex: RuntimeException) {
                ultimo = ex
            }
        }
        throw checkNotNull(ultimo)
    }

    private fun ejecutarEnParalelo(vararg tareas: () -> Unit) {
        val pool = Executors.newFixedThreadPool(tareas.size)
        val salida = CountDownLatch(1)
        try {
            val futuros = tareas.map { tarea ->
                pool.submit(Callable {
                    salida.await()
                    tarea()
                })
            }
            salida.countDown()
            futuros.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }
}
