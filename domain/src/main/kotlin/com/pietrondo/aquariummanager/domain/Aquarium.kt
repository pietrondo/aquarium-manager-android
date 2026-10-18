package com.pietrondo.aquariummanager.domain

import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class AquariumSummary(val id: UUID, val name: String)

interface AquariumRepository {
    fun observeAquariums(): Flow<List<AquariumSummary>>
}

fun validateAquariumVolumes(netLitres: BigDecimal?, grossLitres: BigDecimal?) {
    require(netLitres == null || netLitres.signum() > 0) { "Net litres must be positive" }
    require(grossLitres == null || grossLitres.signum() > 0) { "Gross litres must be positive" }
    require(netLitres == null || grossLitres == null || netLitres <= grossLitres) {
        "Net litres cannot exceed gross litres"
    }
}
