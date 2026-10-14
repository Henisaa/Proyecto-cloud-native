package com.rutaexpress.mqadmin
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
@RestController
@RequestMapping("/api/mq-admin")
class MqAdminController(private val service: MqAdminService) {
    @GetMapping("/cluster") fun cluster() = service.cluster()
    @GetMapping("/queues") fun queues() = service.queues()
    @GetMapping("/alerts") fun alerts() = service.queues().filter { it["alert"] == true }
    @PostMapping("/topology") fun reconcile() = service.reconcile()
    @PutMapping("/retention") fun retention(@Valid @RequestBody request: RetentionRequest) = service.retention(request)
}
