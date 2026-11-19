package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quran_page_ayahs",
    indices = [Index(value = ["page"])]
)
data class QuranPageAyahEntity(
    @PrimaryKey val globalNumber: Int,
    val page: Int,
    val numberInSurah: Int,
    val text: String,
    val surahNumber: Int,
    val surahName: String,
    val juz: Int,
    val hizbQuarter: Int
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val page: Int,
    val surahNumber: Int,
    val surahName: String,
    val label: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "tasbih_items")
data class TasbihItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val phrase: String,
    val targetCount: Int,
    val currentCount: Int = 0,
    val roundsCompleted: Int = 0,
    val totalLifetimeCount: Long = 0L,
    val isCustom: Boolean = false,
    val orderIndex: Int = 0
)
