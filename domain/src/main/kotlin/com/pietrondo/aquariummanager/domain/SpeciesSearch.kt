package com.pietrondo.aquariummanager.domain

import java.text.Normalizer

/**
 * True when the row matches a free-text query.
 *
 * The query matches the common name and the scientific (Latin) name, so a user can look
 * for "betta", for the Latin name of a subspecies such as "splendens" or for the
 * subspecies epithet alone. Matching ignores case and accents: "Perche" finds "Perché".
 */
fun SpeciesCatalogItem.matchesSpeciesQuery(query: String): Boolean {
    val needle = foldForSearch(query)
    if (needle.isBlank()) return true
    return foldForSearch(commonName).contains(needle) ||
        scientificName?.let { foldForSearch(it).contains(needle) } == true
}

/**
 * True when the row, or the species it belongs to, matches the query: looking for
 * "betta" keeps every betta subspecies, looking for "plakat" finds the single row.
 */
fun SpeciesCatalogEntry.matchesSpeciesQuery(query: String): Boolean =
    species.matchesSpeciesQuery(query) || parent.matchesSpeciesQuery(query)

/**
 * Searches the species catalog and groups the result: every species, then its
 * subspecies indented under it. A subspecies whose parent does not match the query
 * stays visible under its species, so a search never hides a match.
 */
fun List<SpeciesCatalogItem>.searchSpeciesCatalog(query: String): List<SpeciesCatalogEntry> =
    groupedByParentSpecies().filter { it.matchesSpeciesQuery(query) }

/**
 * Lowercases the text and drops the diacritics, so that the search is accent
 * insensitive on both sides of the comparison.
 */
internal fun foldForSearch(value: String): String =
    Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .replace(WHITESPACE, " ")
        .trim()

private val DIACRITICS = "\\p{M}+".toRegex()
private val WHITESPACE = "\\s+".toRegex()
