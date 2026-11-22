package com.rutaexpress.kafkaadmin
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(KafkaAdminController::class)
@Import(SeguridadConfig::class)
class AdminSecurityTests {
    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var service: KafkaAdminService
    @MockitoBean lateinit var decoder: JwtDecoder
    @Test fun `rechaza acceso anonimo`() { mvc.perform(get("/api/kafka-admin/topics")).andExpect(status().isUnauthorized) }
    @Test @WithMockUser(roles=["CLIENTE"])
    fun `rechaza acceso sin rol administrador`() { mvc.perform(get("/api/kafka-admin/topics")).andExpect(status().isForbidden) }
    @Test @WithMockUser(roles=["ADMIN"])
    fun `permite consultas de administrador`() {
        `when`(service.topics()).thenReturn(emptyList())
        mvc.perform(get("/api/kafka-admin/topics")).andExpect(status().isOk)
    }
    @Test @WithMockUser(roles=["ADMIN"])
    fun `rechaza configuracion invalida antes de consultar broker`() {
        mvc.perform(post("/api/kafka-admin/topics")
            .contentType("application/json").content("""{"name":"system-topic","partitions":0,"replicas":0,"retentionMs":0,"cleanupPolicy":"unknown"}"""))
            .andExpect(status().isBadRequest)
    }

    @Test @WithMockUser(roles=["DESPACHADOR"])
    fun `rechaza operaciones CRUD y de grupos sin rol ADMIN`() {
        mvc.perform(delete("/api/kafka-admin/topics/rutaexpress.demo.ep5")).andExpect(status().isForbidden)
        mvc.perform(put("/api/kafka-admin/topics/shipments.events/configs")
            .contentType("application/json").content("""{"configs":{"retention.ms":"300000"}}""")).andExpect(status().isForbidden)
        mvc.perform(delete("/api/kafka-admin/topics/shipments.events/configs/retention.ms")).andExpect(status().isForbidden)
        mvc.perform(get("/api/kafka-admin/groups")).andExpect(status().isForbidden)
        mvc.perform(get("/api/kafka-admin/groups/ms-rutaexpress-audit-group")).andExpect(status().isForbidden)
    }

    @Test @WithMockUser(roles=["ADMIN"])
    fun `ADMIN ejecuta eliminacion, configs y consulta de grupos`() {
        `when`(service.deleteTopic("rutaexpress.demo.ep5", false)).thenReturn(mapOf("name" to "rutaexpress.demo.ep5", "deleted" to true))
        `when`(service.updateConfigs("shipments.events", TopicConfigUpdateRequest(mapOf("retention.ms" to "300000"))))
            .thenReturn(mapOf("name" to "shipments.events"))
        `when`(service.deleteConfig("shipments.events", "retention.ms")).thenReturn(mapOf("name" to "shipments.events"))
        `when`(service.listGroups()).thenReturn(listOf(mapOf("groupId" to "ms-rutaexpress-audit-group", "state" to "STABLE")))
        `when`(service.describeGroup("ms-rutaexpress-audit-group")).thenReturn(mapOf("groupId" to "ms-rutaexpress-audit-group", "state" to "STABLE"))

        mvc.perform(delete("/api/kafka-admin/topics/rutaexpress.demo.ep5")).andExpect(status().isOk)
        mvc.perform(put("/api/kafka-admin/topics/shipments.events/configs")
            .contentType("application/json").content("""{"configs":{"retention.ms":"300000"}}""")).andExpect(status().isOk)
        mvc.perform(delete("/api/kafka-admin/topics/shipments.events/configs/retention.ms")).andExpect(status().isOk)
        mvc.perform(get("/api/kafka-admin/groups")).andExpect(status().isOk)
        mvc.perform(get("/api/kafka-admin/groups/ms-rutaexpress-audit-group")).andExpect(status().isOk)
    }
}
