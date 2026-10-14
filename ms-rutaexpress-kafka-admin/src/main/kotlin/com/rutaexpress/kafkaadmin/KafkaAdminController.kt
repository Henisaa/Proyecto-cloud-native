package com.rutaexpress.kafkaadmin
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
@RestController
@RequestMapping("/api/kafka-admin")
class KafkaAdminController(private val service: KafkaAdminService) {
    @GetMapping("/cluster") fun cluster() = service.cluster()
    @GetMapping("/topics") fun topics() = service.topics()
    @GetMapping("/topics/{name}") fun describe(@PathVariable name: String) = service.describe(name)
    @PostMapping("/topics") @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: TopicRequest) = service.create(request)
    @PostMapping("/topology") fun topology() = service.reconcile()
    @PutMapping("/topics/{name}/partitions") fun partitions(@PathVariable name: String, @Valid @RequestBody request: PartitionRequest) = service.partitions(name, request.partitions)
    @PutMapping("/topics/{name}/retention") fun retention(@PathVariable name: String, @Valid @RequestBody request: TopicRetention) = service.retention(name, request)
    @GetMapping("/groups/{group}/lag") fun lag(@PathVariable group: String) = service.lag(group)
}
