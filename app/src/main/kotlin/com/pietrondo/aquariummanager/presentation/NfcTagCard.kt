package com.pietrondo.aquariummanager.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
 *
 * The card stays collapsed to a summary row so the aquarium list keeps the top of the
 * home screen; it opens itself when a tag is read or a binding is armed.
 */
@Composable
fun NfcTagCard(
    state: AquariumListState.Success,
    isNfcAvailable: Boolean,
    isNfcEnabled: Boolean,
    onIdentifyTag: () -> Unit,
    onCancelIdentification: () -> Unit,
    onAssignTag: () -> Unit,
    onCancelAssignment: () -> Unit,
    onUnbindTag: (String) -> Unit,
    onCreateAquariumWithTag: (String) -> Unit,
    onDismissStatus: () -> Unit,
) {
    val activeName = state.activeAquarium?.name.orEmpty()
    val tags = state.activeAquariumNfcTags
    var expanded by remember { mutableStateOf(false) }
    val needsDetails = !isNfcAvailable || !isNfcEnabled ||
        state.isNfcIdentifying || state.isNfcAssigningTag || state.nfcStatus != null
    LaunchedEffect(needsDetails) {
        if (needsDetails) expanded = true
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.nfc_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = if (state.nfcTagBindings.isEmpty()) {
                            stringResource(R.string.nfc_summary_none)
                        } else {
                            stringResource(R.string.nfc_bound_count, state.nfcTagBindings.size)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.isNfcIdentifying) {
                    TextButton(
                        onClick = onCancelIdentification,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Text(stringResource(R.string.nfc_cancel_assignment))
                    }
                } else {
                    TextButton(
                        onClick = onIdentifyTag,
                        enabled = isNfcAvailable && isNfcEnabled,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Text(stringResource(R.string.nfc_identify_button))
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_expand_more),
                        contentDescription = stringResource(
                            if (expanded) R.string.nfc_collapse else R.string.nfc_expand,
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(if (expanded) 180f else 0f),
                    )
                }
            }

            if (!expanded) return@Column

            Text(
                text = stringResource(R.string.nfc_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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
            }

            when {
                state.isNfcIdentifying -> {
                    Text(
                        text = stringResource(R.string.nfc_identifying_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                state.isNfcAssigningTag -> {
                    Text(
                        text = stringResource(R.string.nfc_assigning_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onCancelAssignment) {
                        Text(stringResource(R.string.nfc_cancel_assignment))
                    }
                }

                else -> {
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
                        if (status is NfcStatus.UnknownTag) {
                            if (activeName.isNotEmpty()) {
                                TextButton(onClick = onAssignTag) {
                                    Text(
                                        stringResource(
                                            R.string.nfc_assign_now_to,
                                            activeName,
                                        ),
                                    )
                                }
                            }
                            TextButton(onClick = { onCreateAquariumWithTag(status.tagId) }) {
                                Text(stringResource(R.string.nfc_create_aquarium_with_tag))
                            }
                        }
                    }
                }
            }
        }
    }
}
