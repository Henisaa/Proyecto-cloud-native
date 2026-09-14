package com.rutaexpress.catalog

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class CatalogApplicationTests {

    @Test
    fun contextLoads() {
        // Verifies Spring application context starts up correctly
    }
}
