package com.example.kanjipractice.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [CardEntity::class, ReviewLogEntity::class],
    version = 2,
    exportSchema = true,
    // Adding two nullable columns. Room derives the SQL from the schema diff and
    // checks it at build time, which is safer than hand-writing the ALTER TABLEs
    // for the one migration that must not lose somebody's review history.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class KanjiDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun reviewLogDao(): ReviewLogDao

    companion object {
        const val NAME = "kanji.db"
    }
}
