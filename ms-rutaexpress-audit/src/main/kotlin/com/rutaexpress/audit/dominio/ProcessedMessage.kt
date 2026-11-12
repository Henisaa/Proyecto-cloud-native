package com.rutaexpress.audit.dominio

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "PROCESSED_MESSAGES")
class ProcessedMessage(
    @Id
    @Column(name = "message_id", nullable = false, length = 100)
    val messageId: String,

    @Column(name = "topic", length = 150)
    val topic: String? = null,

    @Column(name = "processed_at", nullable = false)
    val processedAt: Instant = Instant.now()
)
