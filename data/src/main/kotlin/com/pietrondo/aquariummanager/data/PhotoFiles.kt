package com.pietrondo.aquariummanager.data

import java.util.UUID

/**
 * Photos live next to the database, one JPEG per entity, named after its UUID. Keeping
 * the name derivable from the row means no schema change and the backup ZIP carries them
 * as plain entries.
 */
object PhotoFiles {
    const val DIR_NAME = "photos"
    const val MAX_PHOTO_BYTES = 8 * 1024 * 1024

    private val PhotoNamePattern =
        Regex("^(aquarium|event)-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg$")

    fun aquariumName(id: UUID): String = "aquarium-$id.jpg"

    fun eventName(id: UUID): String = "event-$id.jpg"

    fun isPhotoName(name: String): Boolean = PhotoNamePattern.matches(name)
}
