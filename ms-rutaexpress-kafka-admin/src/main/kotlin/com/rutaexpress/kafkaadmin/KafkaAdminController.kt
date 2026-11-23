package com.rutaexpress.kafkaadmin

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/kafka-admin")
class KafkaAdminController(private val service: KafkaAdminService) {
    @GetMapping("/cluster")
    fun cluster() = service.cluster()

    @GetMapping("/topics")
    fun topics() = service.topics()

    @GetMapping("/topics/{name}")
    fun describe(@PathVariable name: String) = service.describe(name)

    @PostMapping("/topics")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: TopicRequest) = service.create(request)

    @DeleteMapping("/topics/{name}")
    fun delete(
        @PathVariable name: String,
        @RequestParam(defaultValue = "false") force: Boolean
    ) = service.deleteTopic(name, force)

    @PostMapping("/topology")
    fun topology() = service.reconcile()

    @PutMapping("/topics/{name}/partitions")
    fun partitions(
        @PathVariable name: String,
        @Valid @RequestBody request: PartitionRequest
    ) = service.partitions(name, request.partitions)

    @PutMapping("/topics/{name}/retention")
    fun retention(
        @PathVariable name: String,
        @Valid @RequestBody request: TopicRetention
    ) = service.retention(name, request)

    @PutMapping("/topics/{name}/configs")
    fun updateConfigs(
        @PathVariable name: String,
        @Valid @RequestBody request: TopicConfigUpdateRequest
    ) = service.updateConfigs(name, request)

    @DeleteMapping("/topics/{name}/configs/{configKey}")
    fun deleteConfig(
        @PathVariable name: String,
        @PathVariable configKey: String
    ) = service.deleteConfig(name, configKey)

    @GetMapping("/groups")
    fun groups() = service.listGroups()

    @GetMapping("/groups/{group}")
    fun describeGroup(@PathVariable group: String) = service.describeGroup(group)

    @GetMapping("/groups/{group}/lag")
    fun lag(@PathVariable group: String) = service.lag(group)

    @PostMapping("/groups/{group}/reset-offsets")
    fun resetGroupOffsets(
        @PathVariable group: String,
        @Valid @RequestBody request: ResetOffsetsRequest
    ) = service.resetGroupOffsets(group, request)

    @DeleteMapping("/groups/{group}")
    fun deleteGroup(
        @PathVariable group: String,
        @RequestParam(defaultValue = "false") force: Boolean
    ) = service.deleteGroup(group, force)
}
