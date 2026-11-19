package com.example.data.repository

import android.content.Context
import com.example.data.catalog.BundledQuranAssetSource
import com.example.data.catalog.OfflineQuranFallback
import com.example.data.local.BookmarkDao
import com.example.data.local.BookmarkEntity
import com.example.data.local.QuranPageAyahEntity
import com.example.data.local.QuranPageDao
import com.example.data.remote.AlQuranApiService
import com.example.domain.model.MushafAyah
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class QuranRepository(
    context: Context,
    private val quranPageDao: QuranPageDao,
    private val bookmarkDao: BookmarkDao,
    private val apiService: AlQuranApiService
) {
    private val appContext = context.applicationContext

    fun isFullMushafBundled(): Boolean = BundledQuranAssetSource.isAvailable(appContext)

    fun observeBookmarks(): Flow<List<BookmarkEntity>> = bookmarkDao.observeBookmarks()

    fun observeIsPageBookmarked(page: Int): Flow<Boolean> = bookmarkDao.observeIsPageBookmarked(page)

    fun observeCachedPages(): Flow<List<Int>> = quranPageDao.observeCachedPages()

    suspend fun getMushafPage(page: Int): Result<List<MushafAyah>> = withContext(Dispatchers.IO) {
        val validPage = page.coerceIn(1, 604)

        val bundled = BundledQuranAssetSource.getPage(appContext, validPage)
        if (!bundled.isNullOrEmpty()) {
            return@withContext Result.success(bundled)
        }

        val cached = quranPageDao.getPageAyahs(validPage)
        if (cached.isNotEmpty()) {
            return@withContext Result.success(cached.map { it.toDomain() })
        }

        return@withContext try {
            val response = apiService.getPageUthmani(validPage)
            val entities = response.data.ayahs.map { dto ->
                QuranPageAyahEntity(
                    globalNumber = dto.number,
                    page = dto.page,
                    numberInSurah = dto.numberInSurah,
                    text = dto.text,
                    surahNumber = dto.surah.number,
                    surahName = dto.surah.name,
                    juz = dto.juz,
                    hizbQuarter = dto.hizbQuarter
                )
            }
            if (entities.isNotEmpty()) {
                quranPageDao.insertAyahs(entities)
            }
            Result.success(entities.map { it.toDomain() })
        } catch (e: Exception) {
            val fallback = OfflineQuranFallback.getFallbackPage(validPage)
            if (fallback != null) {
                Result.success(fallback)
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun prefetchPageSilently(page: Int) = withContext(Dispatchers.IO) {
        val validPage = page.coerceIn(1, 604)
        if (BundledQuranAssetSource.isAvailable(appContext)) return@withContext
        if (quranPageDao.getPageCount(validPage) > 0) return@withContext
        try {
            val response = apiService.getPageUthmani(validPage)
            val entities = response.data.ayahs.map { dto ->
                QuranPageAyahEntity(
                    globalNumber = dto.number,
                    page = dto.page,
                    numberInSurah = dto.numberInSurah,
                    text = dto.text,
                    surahNumber = dto.surah.number,
                    surahName = dto.surah.name,
                    juz = dto.juz,
                    hizbQuarter = dto.hizbQuarter
                )
            }
            if (entities.isNotEmpty()) {
                quranPageDao.insertAyahs(entities)
            }
        } catch (_: Exception) {
            // Silent prefetch ignores transient network errors
        }
    }

    suspend fun toggleBookmark(page: Int, surahNumber: Int, surahName: String): Boolean {
        return withContext(Dispatchers.IO) {
            val isBookmarkedNow = isPageBookmarkedSync(page)
            if (isBookmarkedNow) {
                bookmarkDao.deleteByPage(page)
                false
            } else {
                bookmarkDao.insertBookmark(
                    BookmarkEntity(
                        page = page,
                        surahNumber = surahNumber,
                        surahName = surahName,
                        label = "صفحة $page • $surahName"
                    )
                )
                true
            }
        }
    }

    private suspend fun isPageBookmarkedSync(page: Int): Boolean {
        return bookmarkDao.observeIsPageBookmarked(page).firstOrNull() ?: false
    }

    suspend fun deleteBookmark(id: Int) = withContext(Dispatchers.IO) {
        bookmarkDao.deleteById(id)
    }

    private fun QuranPageAyahEntity.toDomain() = MushafAyah(
        globalNumber = globalNumber,
        numberInSurah = numberInSurah,
        text = text,
        surahNumber = surahNumber,
        surahName = surahName,
        page = page,
        juz = juz,
        hizbQuarter = hizbQuarter
    )
}
