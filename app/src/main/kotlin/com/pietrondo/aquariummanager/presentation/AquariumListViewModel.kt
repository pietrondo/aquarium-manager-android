package com.pietrondo.aquariummanager.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pietrondo.aquariummanager.domain.AquariumRepository
import com.pietrondo.aquariummanager.domain.AquariumSummary
import java.util.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AquariumListState {
    data object Loading : AquariumListState
    data class Success(val aquariums: List<AquariumSummary>) : AquariumListState
    data object Error : AquariumListState
}

class AquariumListViewModel(private val repository: AquariumRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<AquariumListState>(AquariumListState.Loading)
    val state = mutableState.asStateFlow()
    private val retries = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            // collectLatest cancels and joins the previous observation before retrying.
            retries.collectLatest {
                mutableState.value = AquariumListState.Loading
                try {
                    repository.observeAquariums().collect { rows ->
                        mutableState.value = AquariumListState.Success(Collections.unmodifiableList(rows.toList()))
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.value = AquariumListState.Error
                }
            }
        }
    }

    fun retry() { retries.update { it + 1 } }
}
