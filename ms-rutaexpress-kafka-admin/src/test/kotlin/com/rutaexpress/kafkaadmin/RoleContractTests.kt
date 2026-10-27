package com.rutaexpress.kafkaadmin

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class RoleContractTests {
    @Test
    fun `acepta solo roles y alias del caso sin convertir scopes en roles`() {
        for ((raw, expected) in mapOf(" Admin " to "ADMIN", "role_operador" to "DESPACHADOR",
            "Operador del dominio" to "DESPACHADOR", "Cliente del dominio" to "CLIENTE", "Auditor" to "AUDITOR")) {
            assertEquals(expected, normalizeRole(raw))
        }
        for (raw in listOf("INVENTADO", "SCOPE_ADMIN", "access_as_user", "")) assertNull(normalizeRole(raw))
    }
}
