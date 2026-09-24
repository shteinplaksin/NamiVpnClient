package io.github.hhwkart.nami.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit migrations for schema history that predates the current auto-migration
 * declarations. These migrations preserve every existing row and only add
 * nullable or defaulted columns introduced by later entity versions.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE `proxy_groups` ADD COLUMN `isSelector` INTEGER NOT NULL DEFAULT 0",
        )
        database.execSQL(
            "ALTER TABLE `proxy_groups` ADD COLUMN `frontProxy` INTEGER NOT NULL DEFAULT -1",
        )
        database.execSQL(
            "ALTER TABLE `proxy_groups` ADD COLUMN `landingProxy` INTEGER NOT NULL DEFAULT -1",
        )
        database.execSQL(
            "ALTER TABLE `proxy_entities` ADD COLUMN `shadowTLSBean` BLOB DEFAULT NULL",
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE `proxy_entities` ADD COLUMN `mieruBean` BLOB DEFAULT NULL",
        )
    }
}
