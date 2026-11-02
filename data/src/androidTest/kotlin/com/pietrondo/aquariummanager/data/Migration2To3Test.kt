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
class Migration2To3Test {
    private val dbName = "migration-2-3-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AquariumDatabase::class.java,
    )

    @Test
    fun migration2To3KeepsExistingRowsAndAddsMaintenanceTypes() {
        helper.createDatabase(dbName, 2).use { legacy ->
            legacy.execSQL(
                "INSERT INTO aquariums (id, name, netLitres, grossLitres, notes, createdAt) " +
                    "VALUES ('00000000-0000-4000-8000-000000000001', 'Legacy tank', NULL, NULL, NULL, '2026-10-01T10:00:00Z')",
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            dbName,
            3,
            true,
            AquariumDatabase.MIGRATION_2_3,
        )

        migrated.query("SELECT name FROM aquariums").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Legacy tank", cursor.getString(0))
        }

        migrated.execSQL(
            "INSERT INTO maintenance_types (code, label) VALUES ('custom:Catappa', 'Catappa')",
        )
        migrated.query("SELECT label FROM maintenance_types").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Catappa", cursor.getString(0))
        }
    }
}
