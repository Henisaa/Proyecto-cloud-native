package com.rutaexpress.audit.dominio

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "AUDITORIA_EVENTOS")
class EventoAuditoria(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "id_entidad", nullable = false, length = 100)
    val idEntidad: String,

    @Column(name = "tipo_entidad", nullable = false, length = 50)
    val tipoEntidad: String,

    @Column(name = "tipo_evento", nullable = false, length = 50)
    val tipoEvento: String,

    @Column(name = "origen", length = 50)
    val origen: String? = null,

    @Lob
    @Column(name = "datos_payload", nullable = false)
    val datosPayload: String,

    @Column(name = "usuario_responsable", length = 100)
    val usuarioResponsable: String? = null,

    @Column(name = "fecha_evento", nullable = false)
    val fechaEvento: Instant = Instant.now()
)
