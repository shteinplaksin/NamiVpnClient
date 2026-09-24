package io.github.hhwkart.nami.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SagerDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SagerDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrateVersion1ToCurrentPreservesRowsBytesAndDefaults() {
        migrateAndAssert(version = 1)
    }

    @Test
    fun migrateVersion2ToCurrentPreservesRowsBytesAndDefaults() {
        migrateAndAssert(version = 2)
    }

    @Test
    fun migrateVersion3ToCurrentPreservesRowsBytesAndDefaults() {
        migrateAndAssert(version = 3)
    }

    @Test
    fun migrateVersion4ToCurrentPreservesRowsBytesAndDefaults() {
        migrateAndAssert(version = 4)
    }

    @Test
    fun migrateVersion5ToCurrentPreservesRowsBytesAndDefaults() {
        migrateAndAssert(version = 5)
    }

    private fun migrateAndAssert(version: Int) {
        val databaseName = "sager-migration-v$version.db"
        val originalBlob = byteArrayOf(1, 2, 3, 4)

        helper.createDatabase(databaseName, version).use { database ->
            seedHistoricalData(database, version, originalBlob)
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val migrated = Room.databaseBuilder(context, SagerDatabase::class.java, databaseName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

        try {
            val group = migrated.groupDao().getById(11L)
            assertEquals("Legacy group v$version", group?.name)
            assertEquals(false, group?.isSelector)
            assertEquals(-1L, group?.frontProxy)
            assertEquals(-1L, group?.landingProxy)

            val rule = migrated.rulesDao().getById(21L)
            assertEquals("Legacy rule v$version", rule?.name)
            assertEquals("", rule?.config)

            migrated.openHelper.writableDatabase.query(
                "SELECT socksBean FROM proxy_entities WHERE id = 31",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertArrayEquals(originalBlob, cursor.getBlob(0))
            }

            val columns = tableColumns(migrated.openHelper.writableDatabase, "proxy_entities")
            assertTrue("shadowTLSBean" in columns)
            assertTrue("mieruBean" in columns)
            assertTrue("anyTLSBean" in columns)
        } finally {
            migrated.close()
        }
    }

    private fun seedHistoricalData(
        database: SupportSQLiteDatabase,
        version: Int,
        profileBlob: ByteArray,
    ) {
        val profileBlobSql = profileBlob.joinToString(separator = "") { byte ->
            "%02X".format(byte)
        }
        database.execSQL(
            """
            INSERT INTO proxy_groups
                (id, userOrder, ungrouped, name, type, subscription, `order`)
            VALUES (11, 2, 1, 'Legacy group v$version', 0, NULL, 0)
            """.trimIndent(),
        )

        val profileColumns = buildString {
            append("id, groupId, type, userOrder, tx, rx, status, ping, uuid, socksBean")
            if (version >= 2) append(", shadowTLSBean")
            if (version >= 3) append(", mieruBean")
            if (version >= 5) append(", anyTLSBean")
        }
        val profileValues = buildString {
            append("31, 11, 0, 4, 5, 6, 0, 0, 'legacy-$version', X'$profileBlobSql'")
            if (version >= 2) append(", NULL")
            if (version >= 3) append(", NULL")
            if (version >= 5) append(", NULL")
        }
        database.execSQL(
            "INSERT INTO proxy_entities ($profileColumns) VALUES ($profileValues)",
        )

        val ruleColumns = if (version >= 6) {
            "id, name, config, userOrder, enabled, domains, ip, sourcePort, port, network, source, protocol, outbound, packages"
        } else {
            "id, name, userOrder, enabled, domains, ip, sourcePort, port, network, source, protocol, outbound, packages"
        }
        val ruleValues = if (version >= 6) {
            "21, 'Legacy rule v$version', '', 3, 1, 'example.com', '', '', '', '', '', '', 0, ''"
        } else {
            "21, 'Legacy rule v$version', 3, 1, 'example.com', '', '', '', '', '', '', 0, ''"
        }
        database.execSQL("INSERT INTO rules ($ruleColumns) VALUES ($ruleValues)")
    }

    private fun tableColumns(database: SupportSQLiteDatabase, table: String): Set<String> =
        database.query("PRAGMA table_info(`$table`)").use { cursor ->
            buildSet {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }
}
