package com.pietrondo.aquariummanager.presentation

import com.pietrondo.aquariummanager.domain.AquariumRepository
import com.pietrondo.aquariummanager.domain.AquariumSummary
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import androidx.lifecycle.ViewModelStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AquariumListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); dispatcher.scheduler.runCurrent(); Dispatchers.resetMain() }
    private fun vm(source: () -> Flow<List<AquariumSummary>>): AquariumListViewModel {
        val vm = AquariumListViewModel(object : AquariumRepository { override fun observeAquariums() = source() })
        store.put("list", vm)
        return vm
    }
    @Test fun startsLoadingThenShowsRealList() = runTest {
        val row = AquariumSummary(UUID(0, 1), "Reef")
        val vm = vm { flowOf(listOf(row)) }
        assertEquals(AquariumListState.Loading, vm.state.value)
        runCurrent()
        assertEquals(AquariumListState.Success(listOf(row)), vm.state.value)
    }
    @Test fun emptyIsSuccessfulNotFakeData() = runTest {
        val vm = vm { flowOf(emptyList()) }; runCurrent()
        assertEquals(AquariumListState.Success(emptyList()), vm.state.value)
    }
    @Test fun failureDoesNotExposeDatabaseMessageAndRetryRecovers() = runTest {
        var calls = 0
        val vm = vm { flow { if (calls++ == 0) error("SECRET SQL database path"); emit(emptyList()) } }
        runCurrent(); assertEquals(AquariumListState.Error, vm.state.value)
        assertFalse(vm.state.value.toString().contains("SECRET"))
        vm.retry(); runCurrent()
        assertEquals(AquariumListState.Success(emptyList()), vm.state.value)
    }
    @Test fun cancellationIsNotPresentedAsAnError() = runTest {
        val vm = vm { flow { throw CancellationException("cancelled") } }; runCurrent()
        assertEquals(AquariumListState.Loading, vm.state.value)
    }
    @Test fun repeatedRetryNeverOverlapsCollectorsAndClearCancels() = runTest {
        var active = 0; var maximum = 0
        val vm = vm { flow { active++; maximum = maxOf(maximum, active); try { awaitCancellation() } finally { active-- } } }
        runCurrent()
        repeat(3) { vm.retry() }; runCurrent()
        assertEquals(1, maximum); assertEquals(1, active)
        store.clear(); runCurrent(); assertEquals(0, active)
    }
}
