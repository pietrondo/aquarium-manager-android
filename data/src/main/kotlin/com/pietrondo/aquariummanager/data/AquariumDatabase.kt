package com.pietrondo.aquariummanager.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import com.pietrondo.aquariummanager.domain.AquariumSummary
import com.pietrondo.aquariummanager.domain.validateAquariumVolumes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "aquariums")
data class AquariumEntity(
    @PrimaryKey val id: UUID,
    val name: String,
    val netLitres: BigDecimal?,
    val grossLitres: BigDecimal?,
    val notes: String?,
    val createdAt: Instant,
) {
    init {
        require(name.isNotBlank()) { "Aquarium name cannot be blank" }
        validateAquariumVolumes(netLitres, grossLitres)
    }
}

fun AquariumEntity.toSummary() = AquariumSummary(id, name)

class AquariumConverters {
    @TypeConverter fun decimalToText(value: BigDecimal?): String? = value?.stripTrailingZeros()?.toPlainString()
    @TypeConverter fun textToDecimal(value: String?): BigDecimal? = value?.toBigDecimal()
    @TypeConverter fun uuidToText(value: UUID): String = value.toString()
    @TypeConverter fun textToUuid(value: String): UUID {
        val uuid = UUID.fromString(value)
        require(uuid.toString() == value) { "UUID must use canonical lowercase format" }
        return uuid
    }
    @TypeConverter fun instantToText(value: Instant): String = value.toString()
    @TypeConverter fun textToInstant(value: String): Instant = Instant.parse(value)
}

@Dao
interface AquariumDao {
    @Query("SELECT * FROM aquariums ORDER BY name ASC, id ASC")
    fun observeAquariums(): Flow<List<AquariumEntity>>
    @Insert suspend fun insert(aquarium: AquariumEntity)
    @Update suspend fun update(aquarium: AquariumEntity)
}

@Database(entities = [AquariumEntity::class], version = 1, exportSchema = true)
@TypeConverters(AquariumConverters::class)
abstract class AquariumDatabase : RoomDatabase() {
    abstract fun aquariumDao(): AquariumDao
}
