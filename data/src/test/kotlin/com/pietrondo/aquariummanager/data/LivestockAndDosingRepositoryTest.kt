package com.pietrondo.aquariummanager.data

import androidx.room.Room
import com.pietrondo.aquariummanager.domain.AquariumDraft
import com.pietrondo.aquariummanager.domain.DoseUnitDimension
import com.pietrondo.aquariummanager.domain.DosingEventDraft
import com.pietrondo.aquariummanager.domain.DosingFrequency
import com.pietrondo.aquariummanager.domain.DosingPlanDraft
import com.pietrondo.aquariummanager.domain.SpeciesDraft
import com.pietrondo.aquariummanager.domain.SpeciesKind
import com.pietrondo.aquariummanager.domain.StockEntryDraft
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LivestockAndDosingRepositoryTest {
    private lateinit var db: AquariumDatabase
    private lateinit var dao: AquariumDao
    private lateinit var repo: RoomAquariumRepository
    private var currentInstant: Instant = Instant.parse("2026-10-19T12:00:00Z")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AquariumDatabase::class.java,
        ).build()
        dao = db.aquariumDao()
        repo = RoomAquariumRepository(dao, clock = { currentInstant })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun speciesCatalogFilteringCreationDuplicatePreventionAndReferencedEditDeletePolicy() = runBlocking {
        repo.ensureCatalogSeeded()

        val allSpecies = repo.observeSpeciesCatalog(null).first()
        val faunaOnly = repo.observeSpeciesCatalog(SpeciesKind.FAUNA).first()
        val floraOnly = repo.observeSpeciesCatalog(SpeciesKind.FLORA).first()

        assertEquals(6, allSpecies.size)
        assertEquals(3, faunaOnly.size)
        assertTrue(faunaOnly.all { it.kind == SpeciesKind.FAUNA })
        assertEquals(3, floraOnly.size)
        assertTrue(floraOnly.all { it.kind == SpeciesKind.FLORA })

        // Create custom fauna and flora species
        val otocinclus = repo.createSpecies(
            SpeciesDraft(
                kind = SpeciesKind.FAUNA,
                commonName = "  Otocinclus Catfish  ",
                scientificName = "  Otocinclus vittatus ",
            )
        )
        assertEquals("Otocinclus Catfish", otocinclus.commonName)
        assertEquals("Otocinclus vittatus", otocinclus.scientificName)

        val rotala = repo.createSpecies(
            SpeciesDraft(
                kind = SpeciesKind.FLORA,
                commonName = "Rotala Rotundifolia",
                scientificName = null,
            )
        )
        assertNull(rotala.scientificName)

        // Duplicate common name within same kind or duplicate scientific name is rejected
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repo.createSpecies(
                    SpeciesDraft(
                        kind = SpeciesKind.FAUNA,
                        commonName = "otocinclus catfish",
                        scientificName = null,
                    )
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repo.createSpecies(
                    SpeciesDraft(
                        kind = SpeciesKind.FAUNA,
                        commonName = "Dwarf Suckermouth",
                        scientificName = "OTOCINCLUS VITTATUS",
                    )
                )
            }
        }

        // Reference otocinclus in an aquarium stock entry
        val tankId = repo.createAquarium(AquariumDraft(name = "Planted 60P", netLitres = BigDecimal("54")))
        val stockId = repo.createStockEntry(
            aquariumId = tankId,
            draft = StockEntryDraft(
                speciesId = otocinclus.id,
                quantity = BigDecimal("6"),
                introducedAt = Instant.parse("2026-10-01T10:00:00Z"),
                notes = "Algae crew",
            ),
        )

        // Editing referenced species updates in-place and reflects in stock entries
        val updatedOto = repo.updateSpecies(
            id = otocinclus.id,
            draft = SpeciesDraft(
                kind = SpeciesKind.FAUNA,
                commonName = "Dwarf Otocinclus",
                scientificName = "Otocinclus macrospilus",
            ),
        )
        assertEquals(otocinclus.id, updatedOto.id)
        assertEquals("Dwarf Otocinclus", updatedOto.commonName)

        val stockAfterSpeciesEdit = repo.observeStockEntries(tankId).first().single { it.id == stockId }
        assertEquals("Dwarf Otocinclus", stockAfterSpeciesEdit.species.commonName)
        assertEquals("Otocinclus macrospilus", stockAfterSpeciesEdit.species.scientificName)

        // Deleting a referenced species is blocked with a clear error and preserves the species & stock
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.deleteSpecies(otocinclus.id) }
        }
        assertTrue(repo.observeSpeciesCatalog(null).first().any { it.id == otocinclus.id })

        // Unreferenced species (rotala) can be deleted cleanly
        repo.deleteSpecies(rotala.id)
        assertFalse(repo.observeSpeciesCatalog(null).first().any { it.id == rotala.id })
    }

    @Test
    fun stockEntriesSupportUnknownNullQuantityFractionalFloraExitedHistoryAndAquariumIsolation() = runBlocking {
        repo.ensureCatalogSeeded()
        val tankA = repo.createAquarium(AquariumDraft(name = "Alpha Shrimp", netLitres = BigDecimal("30")))
        val tankB = repo.createAquarium(AquariumDraft(name = "Beta Reef", netLitres = BigDecimal("90")))

        val species = repo.observeSpeciesCatalog(null).first()
        val cherryShrimp = species.first { it.commonName == "Cherry Shrimp" }
        val javaFern = species.first { it.commonName == "Java Fern" }
        val neonTetra = species.first { it.commonName == "Neon Tetra" }

        val tIntro1 = Instant.parse("2026-10-01T09:00:00Z")
        val tIntro2 = Instant.parse("2026-10-05T14:00:00Z")
        val tExit2 = Instant.parse("2026-10-18T19:00:00Z")

        // 1. Unknown quantity (null, never 0)
        val shrimpColonyId = repo.createStockEntry(
            aquariumId = tankA,
            draft = StockEntryDraft(
                speciesId = cherryShrimp.id,
                quantity = null,
                introducedAt = tIntro1,
                exitedAt = null,
                notes = "  Breeding colony, count unknown  ",
            ),
        )

        // 2. Fractional flora quantity (e.g. 1.5 mats/pots)
        val fernId = repo.createStockEntry(
            aquariumId = tankA,
            draft = StockEntryDraft(
                speciesId = javaFern.id,
                quantity = BigDecimal("1.5"),
                introducedAt = tIntro2,
                exitedAt = null,
                notes = "Attached to driftwood",
            ),
        )

        // 3. Separate stock on Tank B
        val tankBTetraId = repo.createStockEntry(
            aquariumId = tankB,
            draft = StockEntryDraft(
                speciesId = neonTetra.id,
                quantity = BigDecimal("12"),
                introducedAt = tIntro2,
            ),
        )

        // Reject exit before introduction on update
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repo.markStockExited(fernId, Instant.parse("2026-10-04T00:00:00Z"))
            }
        }

        // Mark fern exited on Tank A -> past stock remains visible with exitedAt populated!
        repo.markStockExited(fernId, tExit2)

        val tankAStock = repo.observeStockEntries(tankA).first()
        assertEquals(listOf(fernId, shrimpColonyId), tankAStock.map { it.id })

        val exitedFern = tankAStock.first { it.id == fernId }
        assertEquals(0, BigDecimal("1.5").compareTo(exitedFern.quantity))
        assertEquals(tIntro2, exitedFern.introducedAt)
        assertEquals(tExit2, exitedFern.exitedAt)
        assertFalse(exitedFern.isActive)

        val activeShrimp = tankAStock.first { it.id == shrimpColonyId }
        assertNull(activeShrimp.quantity)
        assertNull(activeShrimp.exitedAt)
        assertTrue(activeShrimp.isActive)
        assertEquals("Breeding colony, count unknown", activeShrimp.notes)

        // Update stock entry quantity and notes
        repo.updateStockEntry(
            id = shrimpColonyId,
            aquariumId = tankA,
            draft = StockEntryDraft(
                speciesId = cherryShrimp.id,
                quantity = BigDecimal("25"),
                introducedAt = tIntro1,
                exitedAt = null,
                notes = "Counted during water change",
            ),
        )
        val updatedShrimp = repo.observeStockEntries(tankA).first().first { it.id == shrimpColonyId }
        assertEquals(0, BigDecimal("25").compareTo(updatedShrimp.quantity))
        assertEquals("Counted during water change", updatedShrimp.notes)

        // Tank B isolation
        assertEquals(listOf(tankBTetraId), repo.observeStockEntries(tankB).first().map { it.id })
    }

    @Test
    fun productLibraryDoseUnitCatalogAndImmutableDosingHistoryWithRollbackProtection() = runBlocking {
        repo.ensureCatalogSeeded()
        val tankA = repo.createAquarium(AquariumDraft(name = "Alpha High-Tech", netLitres = BigDecimal("60")))
        val tankB = repo.createAquarium(AquariumDraft(name = "Beta Low-Tech", netLitres = BigDecimal("40")))

        val doseUnits = repo.observeDoseUnitCatalog().first()
        assertEquals(setOf("mL", "g", "drops", "tabs"), doseUnits.map { it.code }.toSet())
        assertEquals(
            DoseUnitDimension.VOLUME,
            doseUnits.first { it.code == "mL" }.dimension,
        )

        // Create shared products; reject blank or duplicate product names
        val flourish = repo.createProduct("  Seachem Flourish  ")
        assertEquals("Seachem Flourish", flourish.name)
        val rootTabs = repo.createProduct("Tropica Nutrition Capsules")

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.createProduct("   ") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.createProduct(" seachem flourish ") }
        }

        // Reject unknown dose unit in dosing plan or ad-hoc dosing event
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repo.createDosingPlan(
                    aquariumId = tankA,
                    draft = DosingPlanDraft(
                        productId = flourish.id,
                        dose = BigDecimal("2"),
                        unit = "spoons",
                        frequency = DosingFrequency.DAILY,
                        startDate = LocalDate.of(2026, 10, 19),
                        timeZone = ZoneId.of("Europe/Rome"),
                    ),
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repo.recordAdHocDosingEvent(
                    aquariumId = tankA,
                    draft = DosingEventDraft(
                        productNameSnapshot = "Flourish",
                        dose = BigDecimal("2"),
                        unit = "teaspoons",
                    ),
                )
            }
        }

        // Create a weekly dosing plan on Tank A
        val planId = repo.createDosingPlan(
            aquariumId = tankA,
            draft = DosingPlanDraft(
                productId = flourish.id,
                dose = BigDecimal("2.5"),
                unit = "mL",
                frequency = DosingFrequency.WEEKLY,
                weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                startDate = LocalDate.of(2026, 10, 19),
                endDate = LocalDate.of(2026, 12, 31),
                timeZone = ZoneId.of("Europe/Rome"),
            ),
        )

        // Deleting a product referenced by a dosing plan is blocked
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.deleteProduct(flourish.id) }
        }
        // Unreferenced product can be deleted
        repo.deleteProduct(rootTabs.id)
        assertEquals(listOf(flourish.id), repo.observeProducts().first().map { it.id })

        // Record a plan-triggered dosing event on Tank A at t1
        val t1 = Instant.parse("2026-10-19T08:00:00Z")
        val planEventId = repo.recordDosingFromPlan(
            aquariumId = tankA,
            planId = planId,
            occurredAt = t1,
        )

        // Record an ad-hoc dosing event on Tank A at t2 (planId = null)
        val t2 = Instant.parse("2026-10-19T12:30:00Z")
        val adHocEventId = repo.recordAdHocDosingEvent(
            aquariumId = tankA,
            draft = DosingEventDraft(
                planId = null,
                occurredAt = t2,
                productNameSnapshot = "  APT Zero  ",
                dose = BigDecimal("3"),
                unit = "mL",
            ),
        )

        // Transaction rollback test: attempting to record a dosing event from Tank A's plan onto Tank B
        // MUST throw IllegalArgumentException and MUST NOT insert any dosing event on Tank A or Tank B!
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repo.recordDosingFromPlan(
                    aquariumId = tankB,
                    planId = planId,
                    occurredAt = Instant.parse("2026-10-19T15:00:00Z"),
                )
            }
        }
        assertTrue(repo.observeDosingEvents(tankB).first().isEmpty())
        assertEquals(2, repo.observeDosingEvents(tankA).first().size)

        // Now rename the product AND update the plan's dose/unit:
        // Existing dosing_events MUST retain their exact original snapshot values!
        repo.updateProduct(flourish.id, "Seachem Flourish Comprehensive")
        repo.updateDosingPlan(
            id = planId,
            aquariumId = tankA,
            draft = DosingPlanDraft(
                productId = flourish.id,
                dose = BigDecimal("5.0"),
                unit = "drops",
                frequency = DosingFrequency.DAILY,
                weekdays = emptySet(),
                startDate = LocalDate.of(2026, 10, 19),
                timeZone = ZoneId.of("Europe/Rome"),
            ),
        )

        // Active plan reflects the renamed product and new dose/unit
        val updatedPlan = repo.observeDosingPlans(tankA).first().single()
        assertEquals("Seachem Flourish Comprehensive", updatedPlan.product.name)
        assertEquals(0, BigDecimal("5.0").compareTo(updatedPlan.dose))
        assertEquals("drops", updatedPlan.unit)

        // Historical event recorded before the edits still has "Seachem Flourish", 2.5, "mL"
        val eventsAfterEdit = repo.observeDosingEvents(tankA).first()
        val historicalPlanEvent = eventsAfterEdit.first { it.id == planEventId }
        assertEquals(planId, historicalPlanEvent.planId)
        assertEquals("Seachem Flourish", historicalPlanEvent.productNameSnapshot)
        assertEquals(0, BigDecimal("2.5").compareTo(historicalPlanEvent.dose))
        assertEquals("mL", historicalPlanEvent.unit)

        // Delete the dosing plan -> ON DELETE SET NULL sets planId to null, while preserving the event snapshot!
        repo.deleteDosingPlan(planId)
        assertTrue(repo.observeDosingPlans(tankA).first().isEmpty())

        val eventsAfterPlanDelete = repo.observeDosingEvents(tankA).first()
        assertEquals(listOf(adHocEventId, planEventId), eventsAfterPlanDelete.map { it.id })
        val survivingPlanEvent = eventsAfterPlanDelete.first { it.id == planEventId }
        assertNull(survivingPlanEvent.planId)
        assertEquals("Seachem Flourish", survivingPlanEvent.productNameSnapshot)
        assertEquals(0, BigDecimal("2.5").compareTo(survivingPlanEvent.dose))
        assertEquals("mL", survivingPlanEvent.unit)
    }
}
