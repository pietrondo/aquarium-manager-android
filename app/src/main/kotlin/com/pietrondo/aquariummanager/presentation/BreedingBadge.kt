package com.pietrondo.aquariummanager.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pietrondo.aquariummanager.R
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val BreedingDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/**
 * Tells that the animals of an aquarium are breeding, since when, and any note the
 * user attached to the breeding period.
 */
@Composable
fun BreedingBadge(
    since: Instant?,
    notes: String?,
    modifier: Modifier = Modifier,
) {
    val sinceLabel = since?.atZone(ZoneOffset.UTC)?.toLocalDate()?.format(BreedingDateFormatter)
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.wrapContentWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = if (sinceLabel != null) {
                    stringResource(R.string.breeding_badge_since, sinceLabel)
                } else {
                    stringResource(R.string.breeding_badge)
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            notes?.takeIf { it.isNotBlank() }?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}
