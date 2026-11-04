package com.pietrondo.aquariummanager.domain

import java.util.UUID

/** The taxonomic rank a catalog row sits at, relative to its parent species. */
enum class SpeciesRank {
    /** The species itself. */
    SPECIES,

    /** A subspecies of the parent species: `Trigonostigma heteromorpha sumatrana`. */
    SUBSPECIES,

    /** A cultivated variety of the parent species: `Betta splendens 'Plakat'`. */
    VARIETY,
}

/**
 * A catalog row together with the species it belongs to.
 *
 * [parent] is the species itself for a top level row, and the parent species for a
 * subspecies or a variety.
 */
data class SpeciesCatalogEntry(
    val parent: SpeciesCatalogItem,
    val species: SpeciesCatalogItem,
    val rank: SpeciesRank,
) {
    val isSubspecies: Boolean
        get() = rank != SpeciesRank.SPECIES
}
/**
 * Groups a species catalog so that every subspecies and variety sits under its species.
 *
 * A row belongs to another row when its scientific name starts with the scientific name
 * of that row and carries a further word: a trinomial such as
 * `Trigonostigma heteromorpha sumatrana` is a subspecies, a quoted name such as
 * `Betta splendens 'Plakat'` is a cultivated variety. Rows whose parent is not in the
 * catalog stay on their own, so a subspecies never disappears because its species is
 * missing.
 */
fun List<SpeciesCatalogItem>.groupedByParentSpecies(): List<SpeciesCatalogEntry> {
    val byScientificName = mutableMapOf<String, SpeciesCatalogItem>()
    for (item in this) {
        val key = item.scientificName?.let(::normalizeScientificName)
        if (key != null) byScientificName.putIfAbsent(key, item)
    }

    val childrenOf = mutableMapOf<UUID, MutableList<SpeciesCatalogItem>>()
    val topLevel = mutableListOf<SpeciesCatalogItem>()
    for (item in this) {
        val parent = item.parentSpeciesIn(byScientificName)
        if (parent == null || parent.id == item.id) {
            topLevel += item
        } else {
            childrenOf.getOrPut(parent.id) { mutableListOf() } += item
        }
    }

    val entries = mutableListOf<SpeciesCatalogEntry>()
    for (parent in topLevel.sortedBy { it.commonName.lowercase() }) {
        entries += SpeciesCatalogEntry(parent = parent, species = parent, rank = SpeciesRank.SPECIES)
        for (child in childrenOf[parent.id].orEmpty().sortedBy { it.commonName.lowercase() }) {
            entries += SpeciesCatalogEntry(
                parent = parent,
                species = child,
                rank = child.rankUnder(parent),
            )
        }
    }
    return entries
}

/**
 * The species a row belongs to, or null when the row is a species on its own.
 */
private fun SpeciesCatalogItem.parentSpeciesIn(
    byScientificName: Map<String, SpeciesCatalogItem>,
): SpeciesCatalogItem? {
    val scientificName = scientificName ?: return null
    val words = scientificName.split(WHITESPACE).filter { it.isNotBlank() }
    if (words.size < 3) return null
    return byScientificName[normalizeScientificName(words.take(2).joinToString(" "))]
}

/** The rank a row holds under its parent species. */
private fun SpeciesCatalogItem.rankUnder(parent: SpeciesCatalogItem): SpeciesRank {
    val scientificName = scientificName.orEmpty()
    val words = scientificName.split(WHITESPACE).filter { it.isNotBlank() }
    val epithet = words.drop(2).joinToString(" ")
    return when {
        epithet.contains("'") || epithet.contains("\"") -> SpeciesRank.VARIETY
        epithet.split(WHITESPACE).any { it.trimEnd('.').equals("var", true) } -> SpeciesRank.VARIETY
        else -> SpeciesRank.SUBSPECIES
    }
}

/** Lowercases the name and drops the punctuation, so `Betta splendens var.` matches. */
private fun normalizeScientificName(value: String): String =
    value.lowercase()
        .filter { it.isLetterOrDigit() || it == ' ' }
        .replace(WHITESPACE, " ")
        .trim()

private val WHITESPACE = "\\s+".toRegex()
