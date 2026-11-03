package com.rutaexpress.mqadmin
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
class Application
fun main(args: Array<String>) { runApplication<Application>(*args) }
