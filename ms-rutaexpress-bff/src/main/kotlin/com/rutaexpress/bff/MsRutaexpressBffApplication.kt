package com.rutaexpress.bff

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching

@SpringBootApplication
@EnableCaching
class MsRutaexpressBffApplication

fun main(args: Array<String>) {
    runApplication<MsRutaexpressBffApplication>(*args)
}
