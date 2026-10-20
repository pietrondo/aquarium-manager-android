package com.pietrondo.aquariummanager.data

import com.pietrondo.aquariummanager.domain.AquariumProfileLinkSnapshotRecord
import com.pietrondo.aquariummanager.domain.AquariumProfileSnapshotRecord
import com.pietrondo.aquariummanager.domain.AquariumSnapshotRecord
import com.pietrondo.aquariummanager.domain.BACKUP_FORMAT_ID
import com.pietrondo.aquariummanager.domain.BackupManifest
import com.pietrondo.aquariummanager.domain.BackupRestorePreview
import com.pietrondo.aquariummanager.domain.BackupScheduleSettings
import com.pietrondo.aquariummanager.domain.BackupTableCounts
import com.pietrondo.aquariummanager.domain.CatalogBundle
import com.pietrondo.aquariummanager.domain.DEFAULT_AUTO_BACKUP_RETENTION_COUNT
import com.pietrondo.aquariummanager.domain.DosingEventSnapshotRecord
import com.pietrondo.aquariummanager.domain.DosingFrequency
import com.pietrondo.aquariummanager.domain.DosingPlanSnapshotRecord
import com.pietrondo.aquariummanager.domain.EXPANSION_RATIO_MIN_COMPRESSED_BYTES
import com.pietrondo.aquariummanager.domain.LibrarySnapshot
import com.pietrondo.aquariummanager.domain.MAX_EXPANSION_RATIO
import com.pietrondo.aquariummanager.domain.MAX_LIBRARY_BYTES
import com.pietrondo.aquariummanager.domain.MAX_MANIFEST_BYTES
import com.pietrondo.aquariummanager.domain.MaintenanceEventSnapshotRecord
import com.pietrondo.aquariummanager.domain.MeasurementSnapshotRecord
import com.pietrondo.aquariummanager.domain.ProductSnapshotRecord
import com.pietrondo.aquariummanager.domain.SpeciesKind
import com.pietrondo.aquariummanager.domain.SpeciesSnapshotRecord
import com.pietrondo.aquariummanager.domain.StockEntrySnapshotRecord
import com.pietrondo.aquariummanager.domain.parseBoundedDecimal
import com.pietrondo.aquariummanager.domain.validateBoundedDecimal
import com.pietrondo.aquariummanager.domain.validateBackupManifest
import com.pietrondo.aquariummanager.domain.validateLibrarySnapshot
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class VerifiedBackupArchive(
    val manifest: BackupManifest,
    val snapshot: LibrarySnapshot,
) {
    fun toPreview(sourceUri: String? = null): BackupRestorePreview = BackupRestorePreview(
        formatVersion = manifest.formatVersion,
        appVersion = manifest.appVersion,
        createdAt = manifest.createdAt,
        counts = manifest.counts,
        totalRows = manifest.counts.totalRows.toInt(),
        librarySha256 = manifest.librarySha256,
        sourceUri = sourceUri,
    )
}

object LibraryBackupCodec {
    private const val MANIFEST_ENTRY_NAME = "manifest.json"
    private const val LIBRARY_ENTRY_NAME = "library.json"
    private val ALLOWED_ZIP_ENTRIES = setOf(MANIFEST_ENTRY_NAME, LIBRARY_ENTRY_NAME)

    private val MANIFEST_ROOT_KEYS = setOf(
        "format",
        "formatVersion",
        "appVersion",
        "createdAt",
        "counts",
        "librarySha256",
    )

    private val TABLE_KEYS = setOf(
        "aquariums",
        "aquariumProfiles",
        "aquariumProfileLinks",
        "measurements",
        "maintenanceEvents",
        "products",
        "dosingPlans",
        "dosingEvents",
        "species",
        "stockEntries",
    )

    private val AQUARIUM_KEYS = setOf("id", "name", "netLitres", "grossLitres", "notes", "createdAt")
    private val PROFILE_KEYS = setOf("id", "name")
    private val PROFILE_LINK_KEYS = setOf("aquariumId", "profileId")
    private val MEASUREMENT_KEYS = setOf(
        "id",
        "aquariumId",
        "recordedAt",
        "ph",
        "khDkh",
        "ghDgh",
        "no2MgL",
        "no3MgL",
        "tdsPpm",
        "temperatureC",
    )
    private val MAINTENANCE_KEYS = setOf("id", "aquariumId", "type", "occurredAt", "changedLitres", "notes")
    private val PRODUCT_KEYS = setOf("id", "name")
    private val DOSING_PLAN_KEYS = setOf(
        "id",
        "aquariumId",
        "productId",
        "dose",
        "unit",
        "frequency",
        "weekdays",
        "startDate",
        "endDate",
        "timeZone",
    )
    private val DOSING_EVENT_KEYS = setOf(
        "id",
        "planId",
        "aquariumId",
        "occurredAt",
        "productNameSnapshot",
        "dose",
        "unit",
    )
    private val SPECIES_KEYS = setOf("id", "kind", "scientificName", "commonName")
    private val STOCK_ENTRY_KEYS = setOf(
        "id",
        "aquariumId",
        "speciesId",
        "quantity",
        "introducedAt",
        "exitedAt",
        "notes",
    )

    fun computeSha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            sb.append(Character.forDigit(v ushr 4, 16))
            sb.append(Character.forDigit(v and 0x0F, 16))
        }
        return sb.toString()
    }

    fun serializeLibraryJson(snapshot: LibrarySnapshot): ByteArray {
        val root = JSONObject()

        val aquariumsArr = JSONArray()
        for (item in snapshot.aquariums) {
            aquariumsArr.put(
                JSONObject()
                    .put("id", item.id.toString())
                    .put("name", item.name)
                    .put("netLitres", item.netLitres.toJsonDecimalOrNull())
                    .put("grossLitres", item.grossLitres.toJsonDecimalOrNull())
                    .put("notes", item.notes ?: JSONObject.NULL)
                    .put("createdAt", item.createdAt.toString())
            )
        }
        root.put("aquariums", aquariumsArr)

        val profilesArr = JSONArray()
        for (item in snapshot.aquariumProfiles) {
            profilesArr.put(
                JSONObject()
                    .put("id", item.id.toString())
                    .put("name", item.name)
            )
        }
        root.put("aquariumProfiles", profilesArr)

        val linksArr = JSONArray()
        for (item in snapshot.aquariumProfileLinks) {
            linksArr.put(
                JSONObject()
                    .put("aquariumId", item.aquariumId.toString())
                    .put("profileId", item.profileId.toString())
            )
        }
        root.put("aquariumProfileLinks", linksArr)

        val measurementsArr = JSONArray()
        for (item in snapshot.measurements) {
            measurementsArr.put(
                JSONObject()
                    .put("id", item.id.toString())
                    .put("aquariumId", item.aquariumId.toString())
                    .put("recordedAt", item.recordedAt.toString())
                    .put("ph", item.ph.toJsonDecimalOrNull())
                    .put("khDkh", item.khDkh.toJsonDecimalOrNull())
                    .put("ghDgh", item.ghDgh.toJsonDecimalOrNull())
                    .put("no2MgL", item.no2MgL.toJsonDecimalOrNull())
                    .put("no3MgL", item.no3MgL.toJsonDecimalOrNull())
                    .put("tdsPpm", item.tdsPpm.toJsonDecimalOrNull())
                    .put("temperatureC", item.temperatureC.toJsonDecimalOrNull())
            )
        }
        root.put("measurements", measurementsArr)

        val maintenanceArr = JSONArray()
        for (item in snapshot.maintenanceEvents) {
            maintenanceArr.put(
                JSONObject()
                    .put("id", item.id.toString())
                    .put("aquariumId", item.aquariumId.toString())
                    .put("type", item.type)
                    .put("occurredAt", item.occurredAt.toString())
                    .put("changedLitres", item.changedLitres.toJsonDecimalOrNull())
                    .put("notes", item.notes ?: JSONObject.NULL)
            )
        }
        root.put("maintenanceEvents", maintenanceArr)

        val productsArr = JSONArray()
        for (item in snapshot.products) {
            productsArr.put(
                JSONObject()
                    .put("id", item.id.toString())
                    .put("name", item.name)
            )
        }
        root.put("products", productsArr)

        val dosingPlansArr = JSONArray()
        for (item in snapshot.dosingPlans) {
            val weekdaysArr = JSONArray()
            item.weekdays.map { it.value }.sorted().forEach { weekdaysArr.put(it) }
            dosingPlansArr.put(
                JSONObject()
                    .put("id", item.id.toString())
                    .put("aquariumId", item.aquariumId.toString())
                    .put("productId", item.productId.toString())
                    .put("dose", item.dose.toJsonDecimal())
                    .put("unit", item.unit)
                    .put("frequency", item.frequency.name)
                    .put("weekdays", weekdaysArr)
                    .put("startDate", item.startDate.toString())
                    .put("endDate", item.endDate?.toString() ?: JSONObject.NULL)
                    .put("timeZone", item.timeZone.id)
            )
        }
        root.put("dosingPlans", dosingPlansArr)

        val dosingEventsArr = JSONArray()
        for (item in snapshot.dosingEvents) {
            dosingEventsArr.put(
                JSONObject()
                    .put("id", item.id.toString())
                    .put("planId", item.planId?.toString() ?: JSONObject.NULL)
                    .put("aquariumId", item.aquariumId.toString())
                    .put("occurredAt", item.occurredAt.toString())
                    .put("productNameSnapshot", item.productNameSnapshot)
                    .put("dose", item.dose.toJsonDecimal())
                    .put("unit", item.unit)
            )
        }
        root.put("dosingEvents", dosingEventsArr)

        val speciesArr = JSONArray()
        for (item in snapshot.species) {
            speciesArr.put(
                JSONObject()
                    .put("id", item.id.toString())
                    .put("kind", item.kind.name)
                    .put("scientificName", item.scientificName ?: JSONObject.NULL)
                    .put("commonName", item.commonName)
            )
        }
        root.put("species", speciesArr)

        val stockArr = JSONArray()
        for (item in snapshot.stockEntries) {
            stockArr.put(
                JSONObject()
                    .put("id", item.id.toString())
                    .put("aquariumId", item.aquariumId.toString())
                    .put("speciesId", item.speciesId.toString())
                    .put("quantity", item.quantity.toJsonDecimalOrNull())
                    .put("introducedAt", item.introducedAt.toString())
                    .put("exitedAt", item.exitedAt?.toString() ?: JSONObject.NULL)
                    .put("notes", item.notes ?: JSONObject.NULL)
            )
        }
        root.put("stockEntries", stockArr)

        return root.toString(2).toByteArray(Charsets.UTF_8)
    }

    fun serializeManifestJson(manifest: BackupManifest): ByteArray {
        val countsObj = JSONObject()
            .put("aquariums", manifest.counts.aquariums)
            .put("aquariumProfiles", manifest.counts.aquariumProfiles)
            .put("aquariumProfileLinks", manifest.counts.aquariumProfileLinks)
            .put("measurements", manifest.counts.measurements)
            .put("maintenanceEvents", manifest.counts.maintenanceEvents)
            .put("products", manifest.counts.products)
            .put("dosingPlans", manifest.counts.dosingPlans)
            .put("dosingEvents", manifest.counts.dosingEvents)
            .put("species", manifest.counts.species)
            .put("stockEntries", manifest.counts.stockEntries)

        val root = JSONObject()
            .put("format", manifest.format)
            .put("formatVersion", manifest.formatVersion)
            .put("appVersion", manifest.appVersion)
            .put("createdAt", manifest.createdAt.toString())
            .put("counts", countsObj)
            .put("librarySha256", manifest.librarySha256)

        return root.toString(2).toByteArray(Charsets.UTF_8)
    }

    fun parseManifestJson(bytes: ByteArray): BackupManifest {
        require(bytes.size <= MAX_MANIFEST_BYTES) {
            "manifest.json exceeds maximum size of $MAX_MANIFEST_BYTES bytes"
        }
        val text = bytes.toString(Charsets.UTF_8)
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw IllegalArgumentException("Malformed manifest.json: ${e.message}", e)
        }

        root.requireExactKeys(MANIFEST_ROOT_KEYS, "manifest.json")

        val format = root.requireStrictString("format", "manifest.json")
        val formatVersion = root.requireStrictInt("formatVersion", "manifest.json")
        val appVersion = root.requireStrictString("appVersion", "manifest.json")
        val createdAtRaw = root.requireStrictString("createdAt", "manifest.json")
        val createdAt = parseInstant(createdAtRaw, "manifest.createdAt")
        val librarySha256 = root.requireStrictString("librarySha256", "manifest.json")

        val countsObj = try {
            root.getJSONObject("counts")
        } catch (e: JSONException) {
            throw IllegalArgumentException("Missing or invalid counts object in manifest.json", e)
        }
        countsObj.requireExactKeys(TABLE_KEYS, "manifest.counts")

        val counts = BackupTableCounts(
            aquariums = countsObj.requireStrictInt("aquariums", "manifest.counts"),
            aquariumProfiles = countsObj.requireStrictInt("aquariumProfiles", "manifest.counts"),
            aquariumProfileLinks = countsObj.requireStrictInt("aquariumProfileLinks", "manifest.counts"),
            measurements = countsObj.requireStrictInt("measurements", "manifest.counts"),
            maintenanceEvents = countsObj.requireStrictInt("maintenanceEvents", "manifest.counts"),
            products = countsObj.requireStrictInt("products", "manifest.counts"),
            dosingPlans = countsObj.requireStrictInt("dosingPlans", "manifest.counts"),
            dosingEvents = countsObj.requireStrictInt("dosingEvents", "manifest.counts"),
            species = countsObj.requireStrictInt("species", "manifest.counts"),
            stockEntries = countsObj.requireStrictInt("stockEntries", "manifest.counts"),
        )

        val manifest = BackupManifest(
            format = format,
            formatVersion = formatVersion,
            appVersion = appVersion,
            createdAt = createdAt,
            counts = counts,
            librarySha256 = librarySha256,
        )
        validateBackupManifest(manifest)
        return manifest
    }

    fun parseLibraryJson(
        bytes: ByteArray,
        formatVersion: Int,
        catalog: CatalogBundle,
    ): LibrarySnapshot {
        require(bytes.size <= MAX_LIBRARY_BYTES) {
            "library.json exceeds maximum size of $MAX_LIBRARY_BYTES bytes"
        }
        require(formatVersion in com.pietrondo.aquariummanager.domain.SUPPORTED_BACKUP_FORMAT_VERSIONS) {
            "Unsupported backup formatVersion: $formatVersion"
        }
        val text = bytes.toString(Charsets.UTF_8)
        val rawRoot = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw IllegalArgumentException("Malformed library.json: ${e.message}", e)
        }

        val root = if (formatVersion == 0) {
            migrateV0LibraryJsonToV1(rawRoot)
        } else {
            rawRoot
        }

        root.requireExactKeys(TABLE_KEYS, "library.json")

        val aquariums = root.requireArray("aquariums").mapObjects("aquariums") { obj, idx ->
            obj.requireAllowedKeys(AQUARIUM_KEYS, setOf("id", "name", "createdAt"), "aquariums[$idx]")
            AquariumSnapshotRecord(
                id = parseCanonicalUuid(obj.requireStrictString("id", "aquariums[$idx]"), "aquariums[$idx].id"),
                name = obj.requireStrictString("name", "aquariums[$idx]"),
                netLitres = obj.optStrictDecimal("netLitres", "aquariums[$idx].netLitres"),
                grossLitres = obj.optStrictDecimal("grossLitres", "aquariums[$idx].grossLitres"),
                notes = obj.optNullableString("notes"),
                createdAt = parseInstant(obj.requireStrictString("createdAt", "aquariums[$idx]"), "aquariums[$idx].createdAt"),
            )
        }

        val profiles = root.requireArray("aquariumProfiles").mapObjects("aquariumProfiles") { obj, idx ->
            obj.requireAllowedKeys(PROFILE_KEYS, setOf("id", "name"), "aquariumProfiles[$idx]")
            AquariumProfileSnapshotRecord(
                id = parseCanonicalUuid(obj.requireStrictString("id", "aquariumProfiles[$idx]"), "aquariumProfiles[$idx].id"),
                name = obj.requireStrictString("name", "aquariumProfiles[$idx]"),
            )
        }

        val links = root.requireArray("aquariumProfileLinks").mapObjects("aquariumProfileLinks") { obj, idx ->
            obj.requireAllowedKeys(PROFILE_LINK_KEYS, PROFILE_LINK_KEYS, "aquariumProfileLinks[$idx]")
            AquariumProfileLinkSnapshotRecord(
                aquariumId = parseCanonicalUuid(obj.requireStrictString("aquariumId", "aquariumProfileLinks[$idx]"), "aquariumProfileLinks[$idx].aquariumId"),
                profileId = parseCanonicalUuid(obj.requireStrictString("profileId", "aquariumProfileLinks[$idx]"), "aquariumProfileLinks[$idx].profileId"),
            )
        }

        val measurements = root.requireArray("measurements").mapObjects("measurements") { obj, idx ->
            obj.requireAllowedKeys(MEASUREMENT_KEYS, setOf("id", "aquariumId", "recordedAt"), "measurements[$idx]")
            MeasurementSnapshotRecord(
                id = parseCanonicalUuid(obj.requireStrictString("id", "measurements[$idx]"), "measurements[$idx].id"),
                aquariumId = parseCanonicalUuid(obj.requireStrictString("aquariumId", "measurements[$idx]"), "measurements[$idx].aquariumId"),
                recordedAt = parseInstant(obj.requireStrictString("recordedAt", "measurements[$idx]"), "measurements[$idx].recordedAt"),
                ph = obj.optStrictDecimal("ph", "measurements[$idx].ph"),
                khDkh = obj.optStrictDecimal("khDkh", "measurements[$idx].khDkh"),
                ghDgh = obj.optStrictDecimal("ghDgh", "measurements[$idx].ghDgh"),
                no2MgL = obj.optStrictDecimal("no2MgL", "measurements[$idx].no2MgL"),
                no3MgL = obj.optStrictDecimal("no3MgL", "measurements[$idx].no3MgL"),
                tdsPpm = obj.optStrictDecimal("tdsPpm", "measurements[$idx].tdsPpm"),
                temperatureC = obj.optStrictDecimal("temperatureC", "measurements[$idx].temperatureC"),
            )
        }

        val maintenanceEvents = root.requireArray("maintenanceEvents").mapObjects("maintenanceEvents") { obj, idx ->
            obj.requireAllowedKeys(MAINTENANCE_KEYS, setOf("id", "aquariumId", "type", "occurredAt"), "maintenanceEvents[$idx]")
            MaintenanceEventSnapshotRecord(
                id = parseCanonicalUuid(obj.requireStrictString("id", "maintenanceEvents[$idx]"), "maintenanceEvents[$idx].id"),
                aquariumId = parseCanonicalUuid(obj.requireStrictString("aquariumId", "maintenanceEvents[$idx]"), "maintenanceEvents[$idx].aquariumId"),
                type = obj.requireStrictString("type", "maintenanceEvents[$idx]"),
                occurredAt = parseInstant(obj.requireStrictString("occurredAt", "maintenanceEvents[$idx]"), "maintenanceEvents[$idx].occurredAt"),
                changedLitres = obj.optStrictDecimal("changedLitres", "maintenanceEvents[$idx].changedLitres"),
                notes = obj.optNullableString("notes"),
            )
        }

        val products = root.requireArray("products").mapObjects("products") { obj, idx ->
            obj.requireAllowedKeys(PRODUCT_KEYS, PRODUCT_KEYS, "products[$idx]")
            ProductSnapshotRecord(
                id = parseCanonicalUuid(obj.requireStrictString("id", "products[$idx]"), "products[$idx].id"),
                name = obj.requireStrictString("name", "products[$idx]"),
            )
        }

        val dosingPlans = root.requireArray("dosingPlans").mapObjects("dosingPlans") { obj, idx ->
            obj.requireAllowedKeys(
                DOSING_PLAN_KEYS,
                setOf("id", "aquariumId", "productId", "dose", "unit", "frequency", "weekdays", "startDate", "timeZone"),
                "dosingPlans[$idx]",
            )
            val freqRaw = obj.requireStrictString("frequency", "dosingPlans[$idx]")
            val frequency = try {
                DosingFrequency.valueOf(freqRaw)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Unknown dosing frequency in dosingPlans[$idx]: $freqRaw", e)
            }
            val weekdaysArr = try {
                obj.getJSONArray("weekdays")
            } catch (e: JSONException) {
                throw IllegalArgumentException("Invalid weekdays array in dosingPlans[$idx]", e)
            }
            val weekdays = linkedSetOf<DayOfWeek>()
            for (w in 0 until weekdaysArr.length()) {
                val isoDay = weekdaysArr.requireStrictIntAt(w, "dosingPlans[$idx].weekdays[$w]")
                require(isoDay in 1..7) { "Weekday ISO value out of range 1..7 in dosingPlans[$idx]: $isoDay" }
                require(weekdays.add(DayOfWeek.of(isoDay))) {
                    "Duplicate weekday in dosingPlans[$idx]: $isoDay"
                }
            }
            val endDateRaw = obj.optNullableString("endDate")
            DosingPlanSnapshotRecord(
                id = parseCanonicalUuid(obj.requireStrictString("id", "dosingPlans[$idx]"), "dosingPlans[$idx].id"),
                aquariumId = parseCanonicalUuid(obj.requireStrictString("aquariumId", "dosingPlans[$idx]"), "dosingPlans[$idx].aquariumId"),
                productId = parseCanonicalUuid(obj.requireStrictString("productId", "dosingPlans[$idx]"), "dosingPlans[$idx].productId"),
                dose = obj.requireStrictDecimal("dose", "dosingPlans[$idx].dose"),
                unit = obj.requireStrictString("unit", "dosingPlans[$idx]"),
                frequency = frequency,
                weekdays = if (frequency == DosingFrequency.DAILY) emptySet() else weekdays,
                startDate = parseLocalDate(obj.requireStrictString("startDate", "dosingPlans[$idx]"), "dosingPlans[$idx].startDate"),
                endDate = endDateRaw?.let { parseLocalDate(it, "dosingPlans[$idx].endDate") },
                timeZone = parseZoneId(obj.requireStrictString("timeZone", "dosingPlans[$idx]"), "dosingPlans[$idx].timeZone"),
            )
        }

        val dosingEvents = root.requireArray("dosingEvents").mapObjects("dosingEvents") { obj, idx ->
            obj.requireAllowedKeys(
                DOSING_EVENT_KEYS,
                setOf("id", "aquariumId", "occurredAt", "productNameSnapshot", "dose", "unit"),
                "dosingEvents[$idx]",
            )
            val planIdRaw = obj.optNullableString("planId")
            DosingEventSnapshotRecord(
                id = parseCanonicalUuid(obj.requireStrictString("id", "dosingEvents[$idx]"), "dosingEvents[$idx].id"),
                planId = planIdRaw?.let { parseCanonicalUuid(it, "dosingEvents[$idx].planId") },
                aquariumId = parseCanonicalUuid(obj.requireStrictString("aquariumId", "dosingEvents[$idx]"), "dosingEvents[$idx].aquariumId"),
                occurredAt = parseInstant(obj.requireStrictString("occurredAt", "dosingEvents[$idx]"), "dosingEvents[$idx].occurredAt"),
                productNameSnapshot = obj.requireStrictString("productNameSnapshot", "dosingEvents[$idx]"),
                dose = obj.requireStrictDecimal("dose", "dosingEvents[$idx].dose"),
                unit = obj.requireStrictString("unit", "dosingEvents[$idx]"),
            )
        }

        val species = root.requireArray("species").mapObjects("species") { obj, idx ->
            obj.requireAllowedKeys(SPECIES_KEYS, setOf("id", "kind", "commonName"), "species[$idx]")
            val kindRaw = obj.requireStrictString("kind", "species[$idx]")
            val kind = try {
                SpeciesKind.valueOf(kindRaw)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Unknown species kind in species[$idx]: $kindRaw", e)
            }
            SpeciesSnapshotRecord(
                id = parseCanonicalUuid(obj.requireStrictString("id", "species[$idx]"), "species[$idx].id"),
                kind = kind,
                scientificName = obj.optNullableString("scientificName"),
                commonName = obj.requireStrictString("commonName", "species[$idx]"),
            )
        }

        val stockEntries = root.requireArray("stockEntries").mapObjects("stockEntries") { obj, idx ->
            obj.requireAllowedKeys(
                STOCK_ENTRY_KEYS,
                setOf("id", "aquariumId", "speciesId", "introducedAt"),
                "stockEntries[$idx]",
            )
            val exitedRaw = obj.optNullableString("exitedAt")
            StockEntrySnapshotRecord(
                id = parseCanonicalUuid(obj.requireStrictString("id", "stockEntries[$idx]"), "stockEntries[$idx].id"),
                aquariumId = parseCanonicalUuid(obj.requireStrictString("aquariumId", "stockEntries[$idx]"), "stockEntries[$idx].aquariumId"),
                speciesId = parseCanonicalUuid(obj.requireStrictString("speciesId", "stockEntries[$idx]"), "stockEntries[$idx].speciesId"),
                quantity = obj.optStrictDecimal("quantity", "stockEntries[$idx].quantity"),
                introducedAt = parseInstant(obj.requireStrictString("introducedAt", "stockEntries[$idx]"), "stockEntries[$idx].introducedAt"),
                exitedAt = exitedRaw?.let { parseInstant(it, "stockEntries[$idx].exitedAt") },
                notes = obj.optNullableString("notes"),
            )
        }

        val snapshot = LibrarySnapshot(
            aquariums = aquariums,
            aquariumProfiles = profiles,
            aquariumProfileLinks = links,
            measurements = measurements,
            maintenanceEvents = maintenanceEvents,
            products = products,
            dosingPlans = dosingPlans,
            dosingEvents = dosingEvents,
            species = species,
            stockEntries = stockEntries,
        )
        validateLibrarySnapshot(snapshot, catalog)
        return snapshot
    }

    private fun migrateV0LibraryJsonToV1(rawRoot: JSONObject): JSONObject {
        val migrated = JSONObject(rawRoot.toString())
        if (migrated.has("profileLinks") && !migrated.has("aquariumProfileLinks")) {
            val links = migrated.remove("profileLinks")
            migrated.put("aquariumProfileLinks", links)
        }
        if (!migrated.has("aquariumProfileLinks")) {
            migrated.put("aquariumProfileLinks", JSONArray())
        }
        if (!migrated.has("dosingEvents")) {
            migrated.put("dosingEvents", JSONArray())
        }
        if (migrated.has("species")) {
            val speciesArr = migrated.optJSONArray("species")
            if (speciesArr != null) {
                for (i in 0 until speciesArr.length()) {
                    val sp = speciesArr.optJSONObject(i) ?: continue
                    if (sp.has("name") && !sp.has("commonName")) {
                        val legacyName = sp.remove("name")
                        sp.put("commonName", legacyName)
                    }
                }
            }
        }
        return migrated
    }

    fun inspectAndParseZip(
        inputStream: InputStream,
        catalog: CatalogBundle,
    ): VerifiedBackupArchive {
        val countingStream = CountingInputStream(inputStream)
        val seenEntries = mutableSetOf<String>()
        var manifestBytes: ByteArray? = null
        var libraryBytes: ByteArray? = null

        ZipInputStream(countingStream).use { zip ->
            while (true) {
                val entryStartCompressedOffset = countingStream.bytesRead
                val entry = try {
                    zip.nextEntry
                } catch (e: Exception) {
                    throw IllegalArgumentException("Invalid or corrupted ZIP archive: ${e.message}", e)
                } ?: break

                val name = entry.name
                require(!entry.isDirectory) {
                    "Directory entries are not allowed in backup ZIP: $name"
                }
                require(!name.contains('/') && !name.contains('\\') && !name.contains("..")) {
                    "Path traversal or subdirectory entry rejected in backup ZIP: $name"
                }
                require(name in ALLOWED_ZIP_ENTRIES) {
                    "Unexpected entry in backup ZIP: $name"
                }
                require(seenEntries.add(name)) {
                    "Duplicate entry in backup ZIP: $name"
                }
                val method = entry.method
                require(method == -1 || method == ZipEntry.DEFLATED || method == ZipEntry.STORED) {
                    "Unsupported ZIP compression method for $name: $method"
                }

                val maxBytes = if (name == MANIFEST_ENTRY_NAME) MAX_MANIFEST_BYTES else MAX_LIBRARY_BYTES
                val buffer = ByteArray(8 * 1024)
                val out = ByteArrayOutputStream()
                var totalUncompressed = 0L

                while (true) {
                    val read = try {
                        zip.read(buffer)
                    } catch (e: Exception) {
                        throw IllegalArgumentException("Failed reading ZIP entry $name: ${e.message}", e)
                    }
                    if (read < 0) break
                    totalUncompressed += read
                    require(totalUncompressed <= maxBytes) {
                        "ZIP entry $name exceeds maximum uncompressed size of $maxBytes bytes"
                    }
                    val streamConsumed = maxOf(1L, countingStream.bytesRead - entryStartCompressedOffset)
                    if (totalUncompressed > 16_384L && totalUncompressed > streamConsumed * MAX_EXPANSION_RATIO) {
                        throw IllegalArgumentException(
                            "ZIP entry $name exceeds maximum compression expansion ratio ($MAX_EXPANSION_RATIO:1)"
                        )
                    }
                    out.write(buffer, 0, read)
                }

                zip.closeEntry()
                val reportedCompressed = entry.compressedSize
                val effectiveCompressed = if (reportedCompressed > 0) {
                    reportedCompressed
                } else {
                    maxOf(1L, countingStream.bytesRead - entryStartCompressedOffset)
                }
                if (totalUncompressed > EXPANSION_RATIO_MIN_COMPRESSED_BYTES &&
                    totalUncompressed > maxOf(effectiveCompressed, 1L) * MAX_EXPANSION_RATIO
                ) {
                    throw IllegalArgumentException(
                        "ZIP entry $name exceeds maximum compression expansion ratio ($MAX_EXPANSION_RATIO:1)"
                    )
                }

                if (name == MANIFEST_ENTRY_NAME) {
                    manifestBytes = out.toByteArray()
                } else if (name == LIBRARY_ENTRY_NAME) {
                    libraryBytes = out.toByteArray()
                }
            }
        }

        require(seenEntries == ALLOWED_ZIP_ENTRIES) {
            val missing = ALLOWED_ZIP_ENTRIES - seenEntries
            "Backup ZIP is missing required entries: $missing"
        }

        val finalManifestBytes = manifestBytes
            ?: throw IllegalArgumentException("Missing manifest.json bytes")
        val finalLibraryBytes = libraryBytes
            ?: throw IllegalArgumentException("Missing library.json bytes")

        val manifest = parseManifestJson(finalManifestBytes)
        val actualSha256 = computeSha256Hex(finalLibraryBytes)
        require(actualSha256 == manifest.librarySha256) {
            "Backup library.json SHA-256 mismatch: expected ${manifest.librarySha256}, actual $actualSha256"
        }

        val snapshot = parseLibraryJson(
            bytes = finalLibraryBytes,
            formatVersion = manifest.formatVersion,
            catalog = catalog,
        )
        val actualCounts = snapshot.counts()
        require(actualCounts == manifest.counts) {
            "Backup table counts mismatch between manifest.json (${manifest.counts}) and library.json ($actualCounts)"
        }

        return VerifiedBackupArchive(
            manifest = manifest,
            snapshot = snapshot,
        )
    }

    fun buildVerifiedBackupZipBytes(
        snapshot: LibrarySnapshot,
        createdAt: Instant,
        appVersion: String,
        catalog: CatalogBundle,
        tempDir: File? = null,
    ): Pair<ByteArray, BackupManifest> {
        validateLibrarySnapshot(snapshot, catalog)
        val libraryBytes = serializeLibraryJson(snapshot)
        val sha256 = computeSha256Hex(libraryBytes)
        val manifest = BackupManifest(
            format = BACKUP_FORMAT_ID,
            formatVersion = com.pietrondo.aquariummanager.domain.CURRENT_BACKUP_FORMAT_VERSION,
            appVersion = appVersion,
            createdAt = createdAt,
            counts = snapshot.counts(),
            librarySha256 = sha256,
        )
        validateBackupManifest(manifest)
        val manifestBytes = serializeManifestJson(manifest)

        if (tempDir != null) {
            tempDir.mkdirs()
            val tempFile = File(tempDir, "backup-stage-${UUID.randomUUID()}.zip.tmp")
            try {
                tempFile.outputStream().use { fileOut ->
                    writeZipEntries(fileOut, manifestBytes, libraryBytes)
                }
                val verified = tempFile.inputStream().use { fileIn ->
                    inspectAndParseZip(fileIn, catalog)
                }
                require(verified.manifest.librarySha256 == manifest.librarySha256) {
                    "Temporary backup ZIP verification hash mismatch"
                }
                return tempFile.readBytes() to verified.manifest
            } finally {
                tempFile.delete()
            }
        } else {
            val out = ByteArrayOutputStream()
            writeZipEntries(out, manifestBytes, libraryBytes)
            val zipBytes = out.toByteArray()
            val verified = inspectAndParseZip(ByteArrayInputStream(zipBytes), catalog)
            return zipBytes to verified.manifest
        }
    }

    private fun writeZipEntries(
        outputStream: java.io.OutputStream,
        manifestBytes: ByteArray,
        libraryBytes: ByteArray,
    ) {
        ZipOutputStream(outputStream).use { zip ->
            val manifestEntry = ZipEntry(MANIFEST_ENTRY_NAME)
            zip.putNextEntry(manifestEntry)
            zip.write(manifestBytes)
            zip.closeEntry()

            val libraryEntry = ZipEntry(LIBRARY_ENTRY_NAME)
            zip.putNextEntry(libraryEntry)
            zip.write(libraryBytes)
            zip.closeEntry()
        }
    }

    fun serializeScheduleSettings(settings: BackupScheduleSettings): String {
        val genArr = JSONArray()
        settings.verifiedAutoGenerations.forEach { genArr.put(it) }
        val cleanupArr = JSONArray()
        settings.pendingCleanupUris.forEach { cleanupArr.put(it) }
        val pendingGenerationArr = JSONArray()
        settings.pendingGenerationUris.forEach { pendingGenerationArr.put(it) }
        val pendingGenerationNamesArr = JSONArray()
        settings.pendingGenerationFileNames.forEach { pendingGenerationNamesArr.put(it) }
        return JSONObject()
            .put("autoBackupEnabled", settings.autoBackupEnabled)
            .put("treeUri", settings.treeUri ?: JSONObject.NULL)
            .put("retentionCount", settings.retentionCount)
            .put("lastBackupAt", settings.lastBackupAt?.toString() ?: JSONObject.NULL)
            .put("lastBackupStatus", settings.lastBackupStatus ?: JSONObject.NULL)
            .put("verifiedAutoGenerations", genArr)
            .put("pendingCleanupUris", cleanupArr)
            .put("pendingGenerationUris", pendingGenerationArr)
            .put("pendingGenerationFileNames", pendingGenerationNamesArr)
            .toString(2)
    }

    fun parseScheduleSettings(jsonText: String): BackupScheduleSettings {
        val obj = JSONObject(jsonText)
        val genArr = obj.optJSONArray("verifiedAutoGenerations") ?: JSONArray()
        val generations = buildList(genArr.length()) {
            for (i in 0 until genArr.length()) {
                val uri = genArr.optString(i, "")
                if (uri.isNotBlank()) add(uri)
            }
        }
        val cleanupArr = obj.optJSONArray("pendingCleanupUris") ?: JSONArray()
        val cleanupUris = buildList(cleanupArr.length()) {
            for (i in 0 until cleanupArr.length()) {
                val uri = cleanupArr.optString(i, "")
                if (uri.isNotBlank()) add(uri)
            }
        }
        val pendingGenerationArr = obj.optJSONArray("pendingGenerationUris") ?: JSONArray()
        val pendingGenerationUris = buildList(pendingGenerationArr.length()) {
            for (i in 0 until pendingGenerationArr.length()) {
                val uri = pendingGenerationArr.optString(i, "")
                if (uri.isNotBlank()) add(uri)
            }
        }
        val pendingGenerationNamesArr = obj.optJSONArray("pendingGenerationFileNames") ?: JSONArray()
        val pendingGenerationFileNames = buildList(pendingGenerationNamesArr.length()) {
            for (i in 0 until pendingGenerationNamesArr.length()) {
                val name = pendingGenerationNamesArr.optString(i, "")
                if (name.isNotBlank()) add(name)
            }
        }
        val treeUri = if (obj.has("treeUri") && !obj.isNull("treeUri")) obj.getString("treeUri") else null
        val lastBackupAt = if (obj.has("lastBackupAt") && !obj.isNull("lastBackupAt")) {
            Instant.parse(obj.getString("lastBackupAt"))
        } else {
            null
        }
        val lastBackupStatus = if (obj.has("lastBackupStatus") && !obj.isNull("lastBackupStatus")) {
            obj.getString("lastBackupStatus")
        } else {
            null
        }
        val retention = obj.optInt("retentionCount", DEFAULT_AUTO_BACKUP_RETENTION_COUNT).coerceAtLeast(1)
        return BackupScheduleSettings(
            autoBackupEnabled = obj.optBoolean("autoBackupEnabled", false),
            treeUri = treeUri,
            retentionCount = retention,
            lastBackupAt = lastBackupAt,
            lastBackupStatus = lastBackupStatus,
            verifiedAutoGenerations = generations,
            pendingCleanupUris = cleanupUris,
            pendingGenerationUris = pendingGenerationUris,
            pendingGenerationFileNames = pendingGenerationFileNames,
        )
    }

    private fun BigDecimal.toJsonDecimal(): String {
        validateBoundedDecimal(this, "Backup decimal")
        return stripTrailingZeros().toPlainString()
    }

    private fun BigDecimal?.toJsonDecimalOrNull(): Any =
        this?.let {
            validateBoundedDecimal(it, "Backup decimal")
            it.stripTrailingZeros().toPlainString()
        } ?: JSONObject.NULL

    private fun JSONObject.requireExactKeys(expectedKeys: Set<String>, context: String) {
        val actualKeys = keys().asSequence().toSet()
        val unknown = actualKeys - expectedKeys
        require(unknown.isEmpty()) {
            "Unexpected field(s) in $context: $unknown"
        }
        val missing = expectedKeys - actualKeys
        require(missing.isEmpty()) {
            "Missing required field(s) in $context: $missing"
        }
    }

    private fun JSONObject.requireAllowedKeys(
        allowedKeys: Set<String>,
        requiredKeys: Set<String>,
        context: String,
    ) {
        val actualKeys = keys().asSequence().toSet()
        val unknown = actualKeys - allowedKeys
        require(unknown.isEmpty()) {
            "Unexpected field(s) in $context: $unknown"
        }
        for (req in requiredKeys) {
            require(has(req) && !isNull(req)) {
                "Missing required field '$req' in $context"
            }
        }
    }

    private fun JSONObject.requireStrictString(key: String, context: String): String {
        require(has(key) && !isNull(key)) { "Missing required string field '$key' in $context" }
        val raw = get(key)
        require(raw is String) { "Field '$key' in $context must be a string" }
        return raw
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val raw = get(key)
        require(raw is String) { "Field '$key' must be a string or null" }
        return raw
    }

    private fun JSONObject.requireStrictInt(key: String, context: String): Int {
        require(has(key) && !isNull(key)) { "Missing required integer field '$key' in $context" }
        val raw = get(key)
        require(raw is Int || raw is Long) { "Field '$key' in $context must be an integer" }
        val longVal = (raw as Number).toLong()
        require(longVal in Int.MIN_VALUE..Int.MAX_VALUE) {
            "Integer field '$key' out of range in $context: $longVal"
        }
        return longVal.toInt()
    }

    private fun JSONArray.requireStrictIntAt(index: Int, context: String): Int {
        require(!isNull(index)) { "Null integer at $context" }
        val raw = get(index)
        require(raw is Int || raw is Long) { "Element at $context must be an integer" }
        val longValue = (raw as Number).toLong()
        require(longValue in 1L..7L) { "ISO weekday at $context must be between 1 and 7" }
        return longValue.toInt()
    }

    private fun JSONObject.requireStrictDecimal(key: String, context: String): BigDecimal {
        return optStrictDecimal(key, context)
            ?: throw IllegalArgumentException("Missing required decimal field '$key' in $context")
    }

    private fun JSONObject.optStrictDecimal(key: String, context: String): BigDecimal? {
        if (!has(key) || isNull(key)) return null
        val raw = get(key)
        val text = when (raw) {
            is String -> raw
            is Int, is Long, is BigDecimal -> raw.toString()
            is Double -> {
                require(raw.isFinite()) { "Non-finite number in $context" }
                raw.toString()
            }
            is Float -> {
                require(raw.isFinite()) { "Non-finite number in $context" }
                raw.toString()
            }
            else -> throw IllegalArgumentException("Invalid decimal value for $context: $raw")
        }
        require(text.isNotBlank()) { "Blank decimal value for $context" }
        return parseBoundedDecimal(text, context)
    }

    private fun JSONObject.requireArray(key: String): JSONArray {
        require(has(key) && !isNull(key)) { "Missing required array '$key' in library.json" }
        val raw = get(key)
        require(raw is JSONArray) { "Field '$key' in library.json must be a JSON array" }
        return raw
    }

    private inline fun <T> JSONArray.mapObjects(
        arrayName: String,
        transform: (JSONObject, Int) -> T,
    ): List<T> {
        val arr = this
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val elem = arr.get(i)
                require(elem is JSONObject) { "Element $arrayName[$i] must be a JSON object" }
                add(transform(elem, i))
            }
        }
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

    private fun parseInstant(raw: String, fieldName: String): Instant = try {
        Instant.parse(raw)
    } catch (e: Exception) {
        throw IllegalArgumentException("Invalid ISO-8601 UTC Instant for $fieldName: $raw", e)
    }

    private fun parseLocalDate(raw: String, fieldName: String): LocalDate = try {
        LocalDate.parse(raw)
    } catch (e: Exception) {
        throw IllegalArgumentException("Invalid LocalDate for $fieldName: $raw", e)
    }

    private fun parseZoneId(raw: String, fieldName: String): ZoneId = try {
        ZoneId.of(raw)
    } catch (e: Exception) {
        throw IllegalArgumentException("Invalid IANA ZoneId for $fieldName: $raw", e)
    }
}

private class CountingInputStream(inStream: InputStream) : FilterInputStream(inStream) {
    var bytesRead: Long = 0L
        private set

    override fun read(): Int {
        val b = super.read()
        if (b >= 0) bytesRead++
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        if (n > 0) bytesRead += n
        return n
    }
}
