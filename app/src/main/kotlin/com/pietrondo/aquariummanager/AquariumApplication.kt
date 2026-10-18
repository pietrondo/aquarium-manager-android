package com.pietrondo.aquariummanager

import android.app.Application
import androidx.room.Room
import com.pietrondo.aquariummanager.data.AquariumDatabase
import com.pietrondo.aquariummanager.data.RoomAquariumRepository
import com.pietrondo.aquariummanager.domain.AquariumRepository

class AquariumApplication : Application() {
    private val database: AquariumDatabase by lazy {
        Room.databaseBuilder(applicationContext, AquariumDatabase::class.java, "aquariums.db").build()
    }
    val aquariumRepository: AquariumRepository by lazy { RoomAquariumRepository(database.aquariumDao()) }
}
