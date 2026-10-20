package com.pietrondo.aquariummanager.data

import com.pietrondo.aquariummanager.domain.AquariumProfile
import com.pietrondo.aquariummanager.domain.CatalogBundle
import com.pietrondo.aquariummanager.domain.DoseUnitCatalogItem
import com.pietrondo.aquariummanager.domain.DoseUnitDimension
import com.pietrondo.aquariummanager.domain.MaintenanceTypeCatalogItem
import com.pietrondo.aquariummanager.domain.SpeciesCatalogItem
import com.pietrondo.aquariummanager.domain.SpeciesKind
import java.util.UUID
import org.json.JSONException
import org.json.JSONObject

object CatalogJsonParser {
    const val DEFAULT_CATALOG_RESOURCE = "catalogs/default_catalog_v1.json"

    fun loadDefaultCatalog(): CatalogBundle {
        val stream = CatalogJsonParser::class.java.classLoader?.getResourceAsStream(DEFAULT_CATALOG_RESOURCE)
            ?: CatalogJsonParser::class.java.getResourceAsStream("/$DEFAULT_CATALOG_RESOURCE")
            ?: throw IllegalStateException("Missing packaged catalog resource: $DEFAULT_CATALOG_RESOURCE")
        val jsonText = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parseCatalogJson(jsonText)
    }

    fun parseCatalogJson(jsonText: String): CatalogBundle {
        val root = try {
            JSONObject(jsonText)
        } catch (e: JSONException) {
            throw IllegalArgumentException("Malformed catalog JSON: ${e.message}", e)
        }

        try {
            val format = root.requireNonNullString("format")
            if (!root.has("version") || root.isNull("version")) {
                throw IllegalArgumentException("Missing catalog version")
            }
            val version = root.getInt("version")
            val profilesArray = root.getJSONArray("profileLabels")
            val maintenanceArray = root.getJSONArray("maintenanceTypes")
            val doseUnitsArray = root.getJSONArray("doseUnits")
            val speciesArray = root.getJSONArray("species")

            val profileLabels = buildList(profilesArray.length()) {
                for (i in 0 until profilesArray.length()) {
                    val obj = profilesArray.getJSONObject(i)
                    val id = parseCanonicalUuid(obj.requireNonNullString("id"), "profileLabels[$i].id")
                    val name = obj.requireNonNullString("name").trim()
                    add(AquariumProfile(id = id, name = name))
                }
            }

            val maintenanceTypes = buildList(maintenanceArray.length()) {
                for (i in 0 until maintenanceArray.length()) {
                    val obj = maintenanceArray.getJSONObject(i)
                    val code = obj.requireNonNullString("code").trim()
                    val label = obj.requireNonNullString("label").trim()
                    add(MaintenanceTypeCatalogItem(code = code, label = label))
                }
            }

            val doseUnits = buildList(doseUnitsArray.length()) {
                for (i in 0 until doseUnitsArray.length()) {
                    val obj = doseUnitsArray.getJSONObject(i)
                    val code = obj.requireNonNullString("code").trim()
                    val label = obj.requireNonNullString("label").trim()
                    val rawDimension = obj.requireNonNullString("dimension").trim()
                    val dimension = try {
                        DoseUnitDimension.valueOf(rawDimension)
                    } catch (e: IllegalArgumentException) {
                        throw IllegalArgumentException("Unknown dose unit dimension: $rawDimension", e)
                    }
                    add(DoseUnitCatalogItem(code = code, label = label, dimension = dimension))
                }
            }

            val species = buildList(speciesArray.length()) {
                for (i in 0 until speciesArray.length()) {
                    val obj = speciesArray.getJSONObject(i)
                    val id = parseCanonicalUuid(obj.requireNonNullString("id"), "species[$i].id")
                    val rawKind = obj.requireNonNullString("kind").trim()
                    val kind = try {
                        SpeciesKind.valueOf(rawKind)
                    } catch (e: IllegalArgumentException) {
                        throw IllegalArgumentException("Unknown species kind: $rawKind", e)
                    }
                    val commonName = obj.requireNonNullString("commonName").trim()
                    val scientificName = if (obj.has("scientificName") && !obj.isNull("scientificName")) {
                        val rawScientific = obj.getString("scientificName")
                        require(rawScientific.isNotBlank()) { "Species scientific name cannot be blank" }
                        rawScientific.trim()
                    } else {
                        null
                    }
                    add(
                        SpeciesCatalogItem(
                            id = id,
                            kind = kind,
                            commonName = commonName,
                            scientificName = scientificName,
                        )
                    )
                }
            }

            return CatalogBundle(
                format = format,
                version = version,
                profileLabels = profileLabels,
                maintenanceTypes = maintenanceTypes,
                doseUnits = doseUnits,
                species = species,
            )
        } catch (e: JSONException) {
            throw IllegalArgumentException("Invalid catalog JSON structure: ${e.message}", e)
        }
    }

    private fun JSONObject.requireNonNullString(key: String): String {
        if (!has(key) || isNull(key)) {
            throw IllegalArgumentException("Missing required string field: $key")
        }
        return getString(key)
    }

    private fun parseCanonicalUuid(raw: String, fieldName: String): UUID {
        val uuid = try {
            UUID.fromString(raw)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid UUID for $fieldName: $raw", e)
        }
        require(uuid.toString() == raw) {
            "UUID for $fieldName must use canonical lowercase format: $raw"
        }
        return uuid
    }
}
