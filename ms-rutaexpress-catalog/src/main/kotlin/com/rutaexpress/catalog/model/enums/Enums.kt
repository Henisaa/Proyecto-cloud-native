package com.rutaexpress.catalog.model.enums

enum class ServiceCategory {
    STANDARD,
    EXPRESS,
    SAME_DAY,
    COLD_CHAIN,
    FRAGILE
}

enum class ServiceStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED
}

enum class VehicleType {
    MOTORCYCLE,
    VAN,
    TRUCK,
    ELECTRIC
}

enum class ZoneType {
    URBAN_CENTER,
    URBAN_PERIPHERY,
    RURAL,
    INTERURBAN
}
