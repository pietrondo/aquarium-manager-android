package com.pietrondo.aquariummanager.domain

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeciesCatalogGroupingTest {
    private val betta = SpeciesCatalogItem(
        id = UUID.fromString("00000000-0000-4000-8000-000000000219"),
        kind = SpeciesKind.FAUNA,
        commonName = "Betta",
        scientificName = "Betta splendens",
    )
    private val bettaPlakat = SpeciesCatalogItem(
        id = UUID.fromString("00000000-0000-4000-8000-000000000286"),
        kind = SpeciesKind.FAUNA,
        commonName = "Betta Plakat",
        scientificName = "Betta splendens var. plakat",
    )
    private val rasbora = SpeciesCatalogItem(
        id = UUID.fromString("00000000-0000-4000-8000-000000000221"),
        kind = SpeciesKind.FAUNA,
        commonName = "Rasbora arlecchino",
        scientificName = "Trigonostigma heteromorpha",
    )
    private val rasboraSumatrana = SpeciesCatalogItem(
        id = UUID.fromString("00000000-0000-4000-8000-000000000287"),
        kind = SpeciesKind.FAUNA,
        commonName = "Rasbora arlecchino di Sumatra",
        scientificName = "Trigonostigma heteromorpha sumatrana",
    )
    private val neon = SpeciesCatalogItem(
        id = UUID.fromString("00000000-0000-4000-8000-000000000203"),
        kind = SpeciesKind.FAUNA,
        commonName = "Neon",
        scientificName = "Paracheirodon innesi",
    )
    private val orphan = SpeciesCatalogItem(
        id = UUID.fromString("00000000-0000-4000-8000-000000000288"),
        kind = SpeciesKind.FAUNA,
        commonName = "Corydoras nano",
        scientificName = "Corydoras pygmaeus",
    )

    @Test
    fun aSubspeciesSitsUnderItsSpecies() {
        val grouped = listOf(betta, bettaPlakat, rasbora, rasboraSumatrana, neon, orphan)
            .groupedByParentSpecies()

        assertEquals(
            listOf(betta, bettaPlakat, orphan, neon, rasbora, rasboraSumatrana),
            grouped.map { it.species },
        )
        assertEquals(betta, grouped[1].parent)
        assertEquals(SpeciesRank.VARIETY, grouped[1].rank)
        assertEquals(rasbora, grouped[5].parent)
        assertEquals(SpeciesRank.SUBSPECIES, grouped[5].rank)
    }

    @Test
    fun aSpeciesOnItsOwnIsNotASubspecies() {
        val grouped = listOf(betta, bettaPlakat).groupedByParentSpecies()
        assertEquals(betta, grouped[0].parent)
        assertEquals(SpeciesRank.SPECIES, grouped[0].rank)
    }

    @Test
    fun aSubspeciesWithoutItsSpeciesInTheCatalogStaysOnItsOwn() {
        val grouped = listOf(bettaPlakat).groupedByParentSpecies()
        assertEquals(1, grouped.size)
        assertEquals(bettaPlakat, grouped[0].species)
        assertEquals(SpeciesRank.SPECIES, grouped[0].rank)
    }
}
