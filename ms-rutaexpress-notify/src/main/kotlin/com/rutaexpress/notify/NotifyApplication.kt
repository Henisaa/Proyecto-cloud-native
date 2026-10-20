package com.rutaexpress.notify

import com.rutaexpress.notify.config.NotificationProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(NotificationProperties::class)
class NotifyApplication

fun main(args: Array<String>) {
	runApplication<NotifyApplication>(*args)
}
