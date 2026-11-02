package com.pietrondo.aquariummanager.domain

import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

const val MIN_NFC_TAG_ID_LENGTH = 4
const val MAX_NFC_TAG_ID_LENGTH = 64

/**
 * A physical NFC tag (NTAG21x family) bound to one aquarium.
 *
 * [tagId] is the lowercase hexadecimal hardware UID of the chip. It is stable for the
 * life of the tag and readable without writing anything to it, so a tag keeps working
 * as an aquarium label even when the app is reinstalled.
 */
data class NfcTagBinding(
    val tagId: String,
    val aquariumId: UUID,
    val label: String? = null,
    val createdAt: Instant,
) {
    init {
        validateNfcTagId(tagId)
        validateOptionalTextLength(label, "NFC tag label")
    }
}

fun validateNfcTagId(tagId: String) {
    validateNonBlank(tagId, "NFC tag id")
    require(tagId.length in MIN_NFC_TAG_ID_LENGTH..MAX_NFC_TAG_ID_LENGTH) {
        "NFC tag id must be $MIN_NFC_TAG_ID_LENGTH..$MAX_NFC_TAG_ID_LENGTH characters"
    }
    require(tagId.all { it in '0'..'9' || it in 'a'..'f' }) {
        "NFC tag id must be lowercase hexadecimal"
    }
}

/** Normalizes a raw chip UID into the stored lowercase hexadecimal form. */
fun normalizeNfcTagId(rawTagId: String): String {
    val normalized = rawTagId.trim().lowercase()
    validateNfcTagId(normalized)
    return normalized
}

fun isValidNfcTagId(tagId: String): Boolean = try {
    validateNfcTagId(tagId)
    true
} catch (_: IllegalArgumentException) {
    false
}

interface NfcTagRepository {
    fun observeNfcTagBindings(): Flow<List<NfcTagBinding>> =
        flowOf(emptyList())

    suspend fun bindNfcTag(
        aquariumId: UUID,
        tagId: String,
        label: String? = null,
    ): NfcTagBinding =
        throw UnsupportedOperationException("bindNfcTag not implemented")

    suspend fun unbindNfcTag(tagId: String): Unit =
        throw UnsupportedOperationException("unbindNfcTag not implemented")

    suspend fun findAquariumIdForNfcTag(tagId: String): UUID? = null
}
