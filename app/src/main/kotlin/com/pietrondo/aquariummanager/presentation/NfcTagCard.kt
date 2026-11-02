package com.pietrondo.aquariummanager.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pietrondo.aquariummanager.R

/** Outcome of an NFC tag read, rendered with localized copy by [NfcTagCard]. */
sealed interface NfcStatus {
    data class TagBound(val aquariumName: String, val tagId: String) : NfcStatus
    data class TagUnbound(val tagId: String) : NfcStatus
    data class AquariumSelected(val aquariumName: String) : NfcStatus
    data class UnknownTag(val tagId: String) : NfcStatus
    data object NoAquariumSelected : NfcStatus
}

@Composable
fun NfcStatusText(status: NfcStatus, modifier: Modifier = Modifier) {
    val text = when (status) {
        is NfcStatus.TagBound -> stringResource(
            R.string.nfc_status_bound,
            status.tagId,
            status.aquariumName,
        )
        is NfcStatus.TagUnbound -> stringResource(R.string.nfc_status_unbound, status.tagId)
        is NfcStatus.AquariumSelected -> stringResource(
            R.string.nfc_status_selected,
            status.aquariumName,
        )
        is NfcStatus.UnknownTag -> stringResource(R.string.nfc_status_unknown, status.tagId)
        NfcStatus.NoAquariumSelected -> stringResource(R.string.nfc_status_no_aquarium)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Groups the hexadecimal chip UID in byte pairs so it can be read aloud. */
fun formatNfcTagId(tagId: String): String =
    tagId.chunked(2).joinToString(" ")

/**
 * Binds the physical NFC stickers of the user to the aquariums and shows the tags of
 * the active aquarium. Tapping a bound tag selects its aquarium from anywhere in the app.
 */
@Composable
fun NfcTagCard(
    state: AquariumListState.Success,
    isNfcAvailable: Boolean,
    isNfcEnabled: Boolean,
    onAssignTag: () -> Unit,
    onCancelAssignment: () -> Unit,
    onUnbindTag: (String) -> Unit,
    onDismissStatus: () -> Unit,
) {
    val activeName = state.activeAquarium?.name.orEmpty()
    val tags = state.activeAquariumNfcTags
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.nfc_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.nfc_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.nfcTagBindings.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.nfc_bound_count, state.nfcTagBindings.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isNfcAvailable) {
                Text(
                    text = stringResource(R.string.nfc_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (!isNfcEnabled) {
                Text(
                    text = stringResource(R.string.nfc_switched_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (state.isNfcAssigningTag) {
                Text(
                    text = stringResource(R.string.nfc_assigning_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = onCancelAssignment) {
                        Text(stringResource(R.string.nfc_cancel_assignment))
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onAssignTag,
                        enabled = state.activeAquarium != null,
                    ) {
                        Text(stringResource(R.string.nfc_assign_button))
                    }
                    if (state.activeAquarium != null) {
                        Text(
                            text = activeName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (tags.isEmpty()) {
                Text(
                    text = stringResource(R.string.nfc_no_tags),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                tags.forEach { tag ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.nfc_tag_label, formatNfcTagId(tag.tagId)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { onUnbindTag(tag.tagId) }) {
                            Text(
                                text = stringResource(R.string.nfc_unbind),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            state.nfcStatus?.let { status ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NfcStatusText(status = status, modifier = Modifier.weight(1f))
                            TextButton(onClick = onDismissStatus) {
                                Text(stringResource(R.string.dismiss))
                            }
                        }
                        if (status is NfcStatus.UnknownTag && activeName.isNotEmpty()) {
                            TextButton(onClick = onAssignTag) {
                                Text(
                                    stringResource(
                                        R.string.nfc_assign_now_to,
                                        activeName,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
