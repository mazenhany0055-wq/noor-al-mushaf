package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NoorAlMushafApp
import com.example.data.local.BookmarkEntity
import com.example.domain.model.MushafAyah
import com.example.domain.model.MushafPaperTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface MushafPageUiState {
    data object Loading : MushafPageUiState
    data class Success(val ayahs: List<MushafAyah>) : MushafPageUiState
    data class Error(val message: String) : MushafPageUiState
}

class QuranViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NoorAlMushafApp
    private val quranRepo = app.quranRepository
    private val prefsRepo = app.preferencesRepository

    private val _pagesCache = MutableStateFlow<Map<Int, MushafPageUiState>>(emptyMap())
    val pagesCache: StateFlow<Map<Int, MushafPageUiState>> = _pagesCache.asStateFlow()

    val bookmarks: StateFlow<List<BookmarkEntity>> = quranRepo.observeBookmarks().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val cachedPageNumbers: StateFlow<List<Int>> = quranRepo.observeCachedPages().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun loadPage(page: Int) {
        val validPage = page.coerceIn(1, 604)
        val existing = _pagesCache.value[validPage]
        if (existing is MushafPageUiState.Success || existing is MushafPageUiState.Loading) {
            prefetchNeighbors(validPage)
            return
        }
        fetchPageInternal(validPage)
    }

    fun retryPage(page: Int) {
        fetchPageInternal(page.coerceIn(1, 604))
    }

    private fun fetchPageInternal(validPage: Int) {
        _pagesCache.update { it + (validPage to MushafPageUiState.Loading) }
        viewModelScope.launch {
            val result = quranRepo.getMushafPage(validPage)
            result.onSuccess { ayahs ->
                _pagesCache.update { it + (validPage to MushafPageUiState.Success(ayahs)) }
                prefetchNeighbors(validPage)
            }.onFailure {
                _pagesCache.update {
                    it + (validPage to MushafPageUiState.Error(
                        "تعذّر تحميل هذه الصفحة حاليًا. تأكد من الاتصال بالإنترنت، وسيتم حفظها للقراءة بدون نت تلقائيًا."
                    ))
                }
            }
        }
    }

    private fun prefetchNeighbors(page: Int) {
        viewModelScope.launch {
            listOf(page + 1, page + 2, page - 1)
                .filter { it in 1..604 }
                .forEach { neighbor ->
                    quranRepo.prefetchPageSilently(neighbor)
                }
        }
    }

    fun saveLastReadPage(page: Int) {
        viewModelScope.launch {
            prefsRepo.updateLastReadPage(page)
        }
    }

    fun toggleBookmark(page: Int, surahNumber: Int, surahName: String) {
        viewModelScope.launch {
            quranRepo.toggleBookmark(page, surahNumber, surahName)
        }
    }

    fun deleteBookmark(id: Int) {
        viewModelScope.launch {
            quranRepo.deleteBookmark(id)
        }
    }

    fun updateFontSize(size: Float) {
        viewModelScope.launch {
            prefsRepo.updateMushafFontSize(size)
        }
    }

    fun updatePaperTheme(theme: MushafPaperTheme) {
        viewModelScope.launch {
            prefsRepo.updateMushafPaperTheme(theme)
        }
    }
}
