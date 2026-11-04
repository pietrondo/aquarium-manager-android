package com.pietrondo.aquariummanager.domain

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeciesSearchTest {
    private val betta = SpeciesCatalogItem(
        id = UUID.fromString("00000000-0000-4000-8000-000000000219"),
        kind = SpeciesKind.FAUNA,
        commonName = "Betta",
        scientificName = "Betta splendens",
    )
    private val bettaVariety = SpeciesCatalogItem(
        id = UUID.fromString("00000000-0000-4000-8000-000000000286"),
        kind = SpeciesKind.FAUNA,
        commonName = "Betta Plakat",
        scientificName = "Betta splendens var. plakat",
    )
    private val neon = SpeciesCatalogItem(
        id = UUID.fromString("00000000-0000-4000-8000-000000000203"),
        kind = SpeciesKind.FAUNA,
        commonName = "Neon",
        scientificName = "Paracheirodon innesi",
    )
    private val fern = SpeciesCatalogItem(
        id = UUID.fromString("00000000-0000-4000-8000-000000000204"),
        kind = SpeciesKind.FLORA,
        commonName = "Felce di Giava",
        scientificName = "Microsorum pteropus",
    )

    private val catalog = listOf(betta, bettaVariety, neon, fern)

    @Test
    fun anEmptyQueryKeepsTheWholeCatalog() {
        assertEquals(4, catalog.searchSpeciesCatalog("").size)
        assertEquals(4, catalog.searchSpeciesCatalog("   ").size)
    }

    @Test
    fun theQueryMatchesTheCommonNameAndTheLatinName() {
        assertEquals(listOf(betta, bettaVariety), catalog.searchSpeciesCatalog("betta").map { it.species })
        assertEquals(listOf(betta, bettaVariety), catalog.searchSpeciesCatalog("splendens").map { it.species })
        assertEquals(listOf(bettaVariety), catalog.searchSpeciesCatalog("plakat").map { it.species })
        assertEquals(listOf(neon), catalog.searchSpeciesCatalog("innesi").map { it.species })
    }

    @Test
    fun theQueryIgnoresCaseAndAccents() {
        assertEquals(listOf(fern), catalog.searchSpeciesCatalog("FELCE DI GIAVA").map { it.species })
        assertEquals(listOf(fern), catalog.searchSpeciesCatalog("felce").map { it.species })
    }

    @Test
    fun aQueryWithoutMatchesReturnsNothing() {
        assertTrue(catalog.searchSpeciesCatalog("ciclide").isEmpty())
    }

    @Test
    fun aSubspeciesKeepsItsParentWhenOnlyTheSubspeciesMatches() {
        val entry = catalog.searchSpeciesCatalog("plakat").single()
        assertEquals(betta, entry.parent)
        assertEquals(SpeciesRank.VARIETY, entry.rank)
    }
}
