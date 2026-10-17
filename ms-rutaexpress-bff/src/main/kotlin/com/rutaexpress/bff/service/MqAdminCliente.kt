package com.rutaexpress.bff.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.net.http.HttpClient
import java.time.Duration

/** Reenvía las operaciones del BFF al administrador RabbitMQ conservando estado y cuerpo. */
@Component
class MqAdminCliente(@Value("\${rutaexpress.services.mq-admin-url}") url: String) {
    private val client = RestClient.builder().baseUrl(url)
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
            ).apply { setReadTimeout(Duration.ofSeconds(15)) },
        )
        .build()

    fun enviar(
        method: HttpMethod,
        path: String,
        token: String?,
        body: Any? = null,
        uriVariables: Array<out Any> = emptyArray(),
    ): ResponseEntity<String> = try {
        val request = client.method(method).uri(path, *uriVariables)
            .headers { headers -> token?.let(headers::setBearerAuth) }
        val response = if (body == null) {
            request.retrieve().toEntity(String::class.java)
        } else {
            request.contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().toEntity(String::class.java)
        }
        val builder = ResponseEntity.status(response.statusCode)
        response.headers.contentType?.let(builder::contentType)
        builder.body(response.body)
    } catch (ex: RestClientResponseException) {
        ResponseEntity.status(ex.statusCode)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(ex.responseBodyAsString)
    } catch (ex: RestClientException) {
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body("""{"type":"about:blank","title":"Service Unavailable","status":503,"detail":"Administrador de RabbitMQ no disponible"}""")
    }
}
