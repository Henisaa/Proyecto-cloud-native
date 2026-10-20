package com.rutaexpress.shipments.configuracion

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

/**
 * Usuario autenticado que ejecuta la operación.
 *  - nombre: texto legible para eventos y auditoría (preferred_username, name o sub).
 *  - oid: identificador estable en Entra ID; es lo que se usa para decidir de quién es un envío.
 *  - email: solo para mostrar; Microsoft advierte que preferred_username puede cambiar y no debe autorizar.
 */
data class Actor(
    val nombre: String,
    val oid: String?,
    val email: String?,
    val roles: Set<String>,
) {
    /** Un cliente que no tiene ningún rol interno solo puede ver y operar sus propios envíos. */
    val soloCliente: Boolean
        get() = "CLIENTE" in roles && roles.none { it in ROLES_INTERNOS }

    companion object {
        private val ROLES_INTERNOS = setOf("ADMIN", "DESPACHADOR", "AUDITOR")

        fun actual(): Actor {
            val autenticacion = SecurityContextHolder.getContext().authentication
                ?: return Actor("LOCAL", null, null, emptySet())
            val roles = autenticacion.authorities
                .mapNotNull { it.authority }
                .filter { it.startsWith("ROLE_") }
                .map { it.removePrefix("ROLE_") }
                .toSet()
            if (autenticacion is JwtAuthenticationToken) {
                val claims = autenticacion.token.claims
                val correo = (claims["email"] ?: claims["preferred_username"]) as? String
                return Actor(
                    nombre = nombreLegible(autenticacion.token.claims, autenticacion.token.subject),
                    oid = claims["oid"] as? String,
                    email = correo,
                    roles = roles,
                )
            }
            return Actor(autenticacion.name ?: "LOCAL", null, null, roles)
        }

        /** preferred_username (correo), luego name; el sub es un identificador opaco y es el último recurso. */
        fun nombreLegible(claims: Map<String, Any?>, subject: String?): String =
            (claims["preferred_username"] as? String)?.takeIf { it.isNotBlank() }
                ?: (claims["name"] as? String)?.takeIf { it.isNotBlank() }
                ?: subject
                ?: "usuario"
    }
}
