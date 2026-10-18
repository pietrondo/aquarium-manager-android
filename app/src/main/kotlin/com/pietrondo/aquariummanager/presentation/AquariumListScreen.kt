package com.pietrondo.aquariummanager.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pietrondo.aquariummanager.R

@Composable
fun AquariumListScreen(viewModel: AquariumListViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(modifier = Modifier.fillMaxSize()) { insets ->
        Column(
            Modifier.padding(insets).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.aquariums_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
            )
            when (val current = state) {
                AquariumListState.Loading -> {
                    val loading = stringResource(R.string.aquariums_loading)
                    CircularProgressIndicator(Modifier.semantics { contentDescription = loading })
                    Text(loading)
                }
                AquariumListState.Error -> {
                    Text(stringResource(R.string.aquariums_error))
                    Button(onClick = viewModel::retry) { Text(stringResource(R.string.retry)) }
                }
                is AquariumListState.Success -> {
                    if (current.aquariums.isEmpty()) {
                        Text(stringResource(R.string.aquariums_empty))
                        Text(stringResource(R.string.aquariums_creation_unavailable))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(current.aquariums, key = { it.id.toString() }) { aquarium ->
                                Text(aquarium.name, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
