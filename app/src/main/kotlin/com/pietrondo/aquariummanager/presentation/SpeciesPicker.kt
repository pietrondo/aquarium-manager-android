package com.pietrondo.aquariummanager.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pietrondo.aquariummanager.R
import com.pietrondo.aquariummanager.domain.SpeciesCatalogEntry
import com.pietrondo.aquariummanager.domain.SpeciesKind
import com.pietrondo.aquariummanager.domain.SpeciesRank
import java.util.UUID

/**
 * Search box over the species catalog. It matches the common name and the Latin name.
 */
@Composable
fun SpeciesSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text(stringResource(R.string.species_search_label)) },
        singleLine = true,
        leadingIcon = {
            Image(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * The species catalog as a list: every species, then its subspecies indented under it,
 * each row showing the Latin name. The selected row is highlighted.
 */
@Composable
fun SpeciesPickerList(
    entries: List<SpeciesCatalogEntry>,
    selectedSpeciesId: UUID?,
    onSelect: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 320.dp,
) {
    if (entries.isEmpty()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            shape = MaterialTheme.shapes.small,
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.species_search_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.species_search_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        entries.forEach { entry ->
            SpeciesPickerRow(
                entry = entry,
                isSelected = entry.species.id == selectedSpeciesId,
                onSelect = { onSelect(entry.species.id) },
            )
        }
    }
}

@Composable
private fun SpeciesPickerRow(
    entry: SpeciesCatalogEntry,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val species = entry.species
    Surface(
        onClick = onSelect,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.small,
        border = if (isSelected) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (entry.isSubspecies) 18.dp else 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = species.commonName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (entry.isSubspecies) FontWeight.Normal else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                species.scientificName?.let { scientificName ->
                    Text(
                        text = scientificName,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (entry.rank != SpeciesRank.SPECIES) {
                    Text(
                        text = stringResource(
                            if (entry.rank == SpeciesRank.VARIETY) {
                                R.string.species_variety_of
                            } else {
                                R.string.species_subspecies_of
                            },
                            entry.parent.commonName,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            SpeciesKindBadge(kind = species.kind)
        }
    }
}

/** Small coloured label telling fauna from flora. */
@Composable
fun SpeciesKindBadge(kind: SpeciesKind) {
    val isFauna = kind == SpeciesKind.FAUNA
    Surface(
        color = if (isFauna) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = stringResource(
                if (isFauna) R.string.species_kind_fauna else R.string.species_kind_flora
            ),
            style = MaterialTheme.typography.labelSmall,
            color = if (isFauna) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
