package com.pietrondo.aquariummanager.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration3To4Test {
    private val dbName = "migration-3-4-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AquariumDatabase::class.java,
    )

    @Test
    fun migration3To4KeepsExistingRowsAndAddsNfcTags() {
        helper.createDatabase(dbName, 3).use { legacy ->
            legacy.execSQL(
                "INSERT INTO aquariums (id, name, netLitres, grossLitres, notes, createdAt) " +
                    "VALUES ('00000000-0000-4000-8000-000000000001', 'Legacy tank', NULL, NULL, NULL, '2026-10-01T10:00:00Z')",
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            dbName,
            4,
            true,
            AquariumDatabase.MIGRATION_3_4,
        )

        migrated.query("SELECT name FROM aquariums").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Legacy tank", cursor.getString(0))
        }

        migrated.execSQL(
            "INSERT INTO aquarium_nfc_tags (tagId, aquariumId, label, createdAt) " +
                "VALUES ('04a1b2c3d4e5f6', '00000000-0000-4000-8000-000000000001', NULL, '2026-11-02T10:00:00Z')",
        )
        migrated.query("SELECT tagId FROM aquarium_nfc_tags").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("04a1b2c3d4e5f6", cursor.getString(0))
        }
    }
}
