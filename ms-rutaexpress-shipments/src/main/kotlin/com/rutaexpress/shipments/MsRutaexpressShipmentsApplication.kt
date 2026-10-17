package com.rutaexpress.shipments

import com.rutaexpress.shipments.configuracion.RabbitNombres
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(RabbitNombres::class)
class MsRutaexpressShipmentsApplication

fun main(args: Array<String>) {
    runApplication<MsRutaexpressShipmentsApplication>(*args)
}
