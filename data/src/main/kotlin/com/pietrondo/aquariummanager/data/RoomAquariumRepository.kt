package com.pietrondo.aquariummanager.data

import com.pietrondo.aquariummanager.domain.AquariumRepository
import com.pietrondo.aquariummanager.domain.AquariumSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAquariumRepository(private val dao: AquariumDao) : AquariumRepository {
    override fun observeAquariums(): Flow<List<AquariumSummary>> =
        dao.observeAquariums().map { rows -> rows.map { it.toSummary() } }
}
