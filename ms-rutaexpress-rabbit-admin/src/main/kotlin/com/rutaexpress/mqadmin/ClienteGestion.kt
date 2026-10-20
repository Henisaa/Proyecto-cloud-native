package com.rutaexpress.mqadmin

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration

/**
 * Cliente de la API de gestión HTTP de RabbitMQ. Prueba los nodos en orden: si rabbit1 no responde
 * se consulta rabbit2, porque todos los nodos de un clúster comparten el mismo estado.
 */
@Component
class ClienteGestion(
    @Value("\${app.mq.management-urls}") urls: List<String>,
    @Value("\${spring.rabbitmq.username}") usuario: String,
    @Value("\${spring.rabbitmq.password}") clave: String,
) {
    private val bases = urls.map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }
    private val http = RestClient.builder()
        .requestFactory(
            // HTTP/1.1 fijo: el servidor de gestión de RabbitMQ no soporta bien el upgrade h2c que intenta el cliente por defecto.
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build()
            ).apply { setReadTimeout(Duration.ofSeconds(10)) }
        )
        .defaultHeaders { it.setBasicAuth(usuario, clave) }
        .build()

    /** Devuelve el cuerpo o null si el recurso no existe (404). Lanza BrokerNoDisponibleException si ningún nodo responde. */
    fun <T : Any> obtener(ruta: String, tipo: Class<T>): T? {
        var ultimo: RestClientException? = null
        for (base in bases) {
            try {
                return http.get().uri(URI.create(base + ruta)).retrieve().body(tipo)
            } catch (ex: HttpClientErrorException) {
                if (ex.statusCode.value() == 404) return null
                throw BrokerNoDisponibleException(
                    "RabbitMQ rechazó la consulta de gestión (HTTP ${ex.statusCode.value()}); revise RABBITMQ_USERNAME y RABBITMQ_PASSWORD",
                    ex,
                )
            } catch (ex: RestClientException) {
                ultimo = ex
            }
        }
        throw BrokerNoDisponibleException("Ningún nodo de gestión de RabbitMQ responde: ${bases.joinToString()}", ultimo)
    }

    fun existe(ruta: String): Boolean = obtener(ruta, Any::class.java) != null

    fun objeto(ruta: String): Map<*, *>? = obtener(ruta, Map::class.java)

    fun lista(ruta: String): List<Map<*, *>> =
        (obtener(ruta, List::class.java) ?: emptyList<Any>()).filterIsInstance<Map<*, *>>()

    /** PUT con cuerpo JSON (políticas). Un 400 de RabbitMQ se traduce a IllegalArgumentException. */
    fun escribir(ruta: String, cuerpo: Any) {
        var ultimo: RestClientException? = null
        for (base in bases) {
            try {
                http.put().uri(URI.create(base + ruta)).contentType(MediaType.APPLICATION_JSON).body(cuerpo)
                    .retrieve().toBodilessEntity()
                return
            } catch (ex: HttpClientErrorException) {
                if (ex.statusCode.value() == 400) {
                    throw IllegalArgumentException("RabbitMQ rechazó la configuración: ${ex.responseBodyAsString}", ex)
                }
                throw BrokerNoDisponibleException("RabbitMQ no pudo aplicar la configuración (HTTP ${ex.statusCode.value()})", ex)
            } catch (ex: RestClientException) {
                ultimo = ex
            }
        }
        throw BrokerNoDisponibleException("Ningún nodo de gestión de RabbitMQ responde: ${bases.joinToString()}", ultimo)
    }
}

/** Rutas de la API de gestión con los segmentos codificados (el vhost "/" viaja como %2F). */
object RutasGestion {
    fun ruta(vararg partes: String): String = "/" + partes.joinToString("/") { codificar(it) }

    fun colasDelVhost(vhost: String) = ruta("api", "queues", vhost)
    fun cola(vhost: String, nombre: String) = ruta("api", "queues", vhost, nombre)
    fun exchangesDelVhost(vhost: String) = ruta("api", "exchanges", vhost)
    fun exchange(vhost: String, nombre: String) = ruta("api", "exchanges", vhost, nombre)
    fun enlacesDelVhost(vhost: String) = ruta("api", "bindings", vhost)
    fun enlacesDeOrigen(vhost: String, origen: String) = ruta("api", "exchanges", vhost, origen, "bindings", "source")
    fun politica(vhost: String, nombre: String) = ruta("api", "policies", vhost, nombre)
    fun politicasDelVhost(vhost: String) = ruta("api", "policies", vhost)

    private fun codificar(valor: String): String =
        org.springframework.web.util.UriUtils.encodePathSegment(valor, Charsets.UTF_8)
}
