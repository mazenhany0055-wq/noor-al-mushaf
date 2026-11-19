package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QuranPageDao {
    @Query("SELECT * FROM quran_page_ayahs WHERE page = :page ORDER BY globalNumber ASC")
    suspend fun getPageAyahs(page: Int): List<QuranPageAyahEntity>

    @Query("SELECT COUNT(*) FROM quran_page_ayahs WHERE page = :page")
    suspend fun getPageCount(page: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAyahs(ayahs: List<QuranPageAyahEntity>)

    @Query("SELECT DISTINCT page FROM quran_page_ayahs")
    fun observeCachedPages(): Flow<List<Int>>
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAtMillis DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE page = :page)")
    fun observeIsPageBookmarked(page: Int): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE page = :page")
    suspend fun deleteByPage(page: Int)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Int)
}

@Dao
interface TasbihDao {
    @Query("SELECT * FROM tasbih_items ORDER BY orderIndex ASC, id ASC")
    fun observeAllTasbih(): Flow<List<TasbihItemEntity>>

    @Query("SELECT COUNT(*) FROM tasbih_items")
    suspend fun countItems(): Int

    @Query("SELECT COALESCE(SUM(totalLifetimeCount), 0) FROM tasbih_items")
    fun observeTotalLifetimeTasbih(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TasbihItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TasbihItemEntity): Long

    @Update
    suspend fun update(item: TasbihItemEntity)

    @Query("DELETE FROM tasbih_items WHERE id = :id AND isCustom = 1")
    suspend fun deleteCustomById(id: Int)
}
