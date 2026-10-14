package com.rutaexpress.catalog.model.repository

import com.rutaexpress.catalog.model.entity.CatalogService
import com.rutaexpress.catalog.model.enums.ServiceCategory
import com.rutaexpress.catalog.model.enums.ServiceStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface CatalogServiceRepository : JpaRepository<CatalogService, Long> {

    fun findByCode(code: String): Optional<CatalogService>

    fun existsByCode(code: String): Boolean

    fun findByActive(active: Boolean): List<CatalogService>

    fun findByStatus(status: ServiceStatus): List<CatalogService>

    fun findByCategory(category: ServiceCategory): List<CatalogService>

    @Query("""
        SELECT DISTINCT s FROM CatalogService s 
        LEFT JOIN FETCH s.capacities c 
        LEFT JOIN FETCH s.tariffs t 
        WHERE s.id = :id
    """)
    fun findByIdWithDetails(@Param("id") id: Long): Optional<CatalogService>

    @Query("""
        SELECT DISTINCT s FROM CatalogService s 
        LEFT JOIN FETCH s.capacities c 
        LEFT JOIN FETCH s.tariffs t 
        WHERE (:category IS NULL OR s.category = :category) 
          AND (:status IS NULL OR s.status = :status)
          AND (:active IS NULL OR s.active = :active)
    """)
    fun searchServices(
        @Param("category") category: ServiceCategory?,
        @Param("status") status: ServiceStatus?,
        @Param("active") active: Boolean?
    ): List<CatalogService>
}
