package com.pietrondo.aquariummanager.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pietrondo.aquariummanager.domain.NfcTagBinding
import com.pietrondo.aquariummanager.domain.validateNfcTagId
import java.time.Instant
import java.util.UUID

/**
 * Binds the hardware UID of an NFC tag to one aquarium.
 *
 * The tag UID is the primary key, so a tag can only point at a single aquarium and
 * re-binding the same tag moves it instead of creating a duplicate.
 */
@Entity(
    tableName = "aquarium_nfc_tags",
    foreignKeys = [
        ForeignKey(
            entity = AquariumEntity::class,
            parentColumns = ["id"],
            childColumns = ["aquariumId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["aquariumId"])],
)
data class AquariumNfcTagEntity(
    @PrimaryKey val tagId: String,
    val aquariumId: UUID,
    val label: String? = null,
    val createdAt: Instant,
) {
    init {
        validateNfcTagId(tagId)
    }
}

fun AquariumNfcTagEntity.toDomain() = NfcTagBinding(
    tagId = tagId,
    aquariumId = aquariumId,
    label = label,
    createdAt = createdAt,
)
