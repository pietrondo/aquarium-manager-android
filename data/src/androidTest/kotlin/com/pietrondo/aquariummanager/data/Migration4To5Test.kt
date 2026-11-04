package com.pietrondo.aquariummanager.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration4To5Test {
    private val dbName = "migration-4-5-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AquariumDatabase::class.java,
    )

    @Test
    fun migration4To5KeepsExistingRowsAndAddsTheBreedingFields() {
        helper.createDatabase(dbName, 4).use { legacy ->
            legacy.execSQL(
                "INSERT INTO aquariums (id, name, netLitres, grossLitres, notes, createdAt) " +
                    "VALUES ('00000000-0000-4000-8000-000000000001', 'Legacy tank', NULL, NULL, NULL, '2026-10-01T10:00:00Z')",
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            dbName,
            5,
            true,
            AquariumDatabase.MIGRATION_4_5,
        )

        // The pre-existing tank survives and is simply not breeding.
        migrated.query(
            "SELECT name, breedingSince, breedingNotes FROM aquariums",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Legacy tank", cursor.getString(0))
            assertNull(cursor.getString(1))
            assertNull(cursor.getString(2))
        }

        // The new columns accept a breeding period and its notes.
        migrated.execSQL(
            "INSERT INTO aquariums (id, name, netLitres, grossLitres, notes, createdAt, breedingSince, breedingNotes) " +
                "VALUES ('00000000-0000-4000-8000-000000000002', 'Breeding tank', NULL, NULL, NULL, " +
                "'2026-11-04T10:00:00Z', '2026-11-04T00:00:00Z', 'Separate the male')",
        )
        migrated.query(
            "SELECT breedingSince, breedingNotes FROM aquariums WHERE name = 'Breeding tank'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("2026-11-04T00:00:00Z", cursor.getString(0))
            assertEquals("Separate the male", cursor.getString(1))
        }
    }
}
