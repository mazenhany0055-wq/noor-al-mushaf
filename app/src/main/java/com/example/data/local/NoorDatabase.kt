package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        QuranPageAyahEntity::class,
        BookmarkEntity::class,
        TasbihItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NoorDatabase : RoomDatabase() {
    abstract fun quranPageDao(): QuranPageDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun tasbihDao(): TasbihDao

    companion object {
        @Volatile
        private var INSTANCE: NoorDatabase? = null

        fun getInstance(context: Context): NoorDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NoorDatabase::class.java,
                    "noor_al_mushaf.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
