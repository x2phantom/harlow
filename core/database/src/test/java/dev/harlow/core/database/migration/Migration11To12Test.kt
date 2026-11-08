package dev.harlow.core.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import dev.harlow.core.database.HarlowDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 11 → 12 drops the unused search history and lyrics tables and keeps everything else. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration11To12Test {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), HarlowDatabase::class.java)

    @Test
    fun `drops search history and lyrics, keeps the session`() {
        helper.createDatabase(DB_NAME, 11).use { db ->
            db.execSQL(
                "INSERT INTO servers (id, name, url, userId, accessToken, isActive, lastConnected) " +
                    "VALUES ('s1', 'Jellyfin', 'https://jelly.example', 'u1', 'v1:x:y', 1, 0)",
            )
            db.execSQL("INSERT INTO search_queries (serverId, queryText, searchedAt) VALUES ('s1', 'metallica', 0)")
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 12, true, Migrations.MIGRATION_11_12)

        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            val tables = buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            assertFalse("search_queries" in tables)
            assertFalse("lyrics" in tables)
        }
        db.query("SELECT accessToken FROM servers WHERE id = 's1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("v1:x:y", cursor.getString(0))
        }
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
