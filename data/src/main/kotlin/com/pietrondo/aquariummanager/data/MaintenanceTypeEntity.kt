package com.pietrondo.aquariummanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pietrondo.aquariummanager.domain.validateNonBlank

/** User-defined maintenance type. Built-in types stay in the packaged catalog. */
@Entity(tableName = "maintenance_types")
data class MaintenanceTypeEntity(
    @PrimaryKey val code: String,
    val label: String,
) {
    init {
        validateNonBlank(code, "Maintenance type code")
        validateNonBlank(label, "Maintenance type label")
    }
}
