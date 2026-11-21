package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NoorAlMushafApp
import com.example.data.catalog.ScholarCatalog
import com.example.domain.model.LessonScholar
import com.example.domain.model.ReligiousLesson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LessonsUiState(
    val selectedScholarId: String = "latest",
    val searchQuery: String = "",
    val showFavoritesOnly: Boolean = false,
    val isSyncing: Boolean = false,
    val statusBanner: String? = null,
    val activeLesson: ReligiousLesson? = null,
    val hasConfiguredApiKey: Boolean = false
)

class LessonsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NoorAlMushafApp
    private val lessonsRepo = app.lessonsRepository

    private val _uiState = MutableStateFlow(
        LessonsUiState(hasConfiguredApiKey = lessonsRepo.hasConfiguredYouTubeApiKey())
    )
    val uiState: StateFlow<LessonsUiState> = _uiState.asStateFlow()

    private val _customSearchLessons = MutableStateFlow<List<ReligiousLesson>>(emptyList())
    private val sessionFailedVideoIds = mutableSetOf<String>()

    val favoriteVideoIds: StateFlow<Set<String>> = lessonsRepo.favoriteVideoIds

    val displayedLessons: StateFlow<List<ReligiousLesson>> = combine(
        _uiState,
        lessonsRepo.lessonsByScholar,
        _customSearchLessons,
        lessonsRepo.favoriteVideoIds
    ) { state, byScholar, customResults, favorites ->
        val baseList = if (state.searchQuery.isNotBlank()) {
            val allKnown = byScholar.values.flatten() + customResults
            val q = state.searchQuery.trim()
            allKnown
                .distinctBy { it.youtubeVideoId }
                .filter {
                    it.title.contains(q, ignoreCase = true) ||
                        it.scholarName.contains(q, ignoreCase = true) ||
                        it.channelTitle.contains(q, ignoreCase = true) ||
                        it.description.contains(q, ignoreCase = true)
                }
        } else {
            byScholar[state.selectedScholarId].orEmpty()
        }

        baseList.filter { lesson ->
            !state.showFavoritesOnly || favorites.contains(lesson.youtubeVideoId)
        }.sortedWith(
            compareByDescending<ReligiousLesson> { it.isNewArrival }
                .thenByDescending { it.publishedDateLabel }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        autoSyncLatest()
    }

    private fun autoSyncLatest() {
        viewModelScope.launch {
            if (!lessonsRepo.isNetworkConnected()) return@launch
            _uiState.update { it.copy(isSyncing = true) }
            try {
                ScholarCatalog.scholars.firstOrNull { it.id == "latest" }?.let {
                    lessonsRepo.syncScholarLessons(it)
                }
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun selectScholar(scholar: LessonScholar) {
        _uiState.update {
            it.copy(
                selectedScholarId = scholar.id,
                searchQuery = "",
                statusBanner = null
            )
        }
        viewModelScope.launch {
            if (lessonsRepo.isNetworkConnected()) {
                _uiState.update { it.copy(isSyncing = true) }
                try {
                    val synced = lessonsRepo.syncScholarLessons(scholar)
                    if (synced.isEmpty()) {
                        _uiState.update {
                            it.copy(statusBanner = "لا توجد فيديوهات موثقة متاحة حالياً لـ ${scholar.arabicName}")
                        }
                    }
                } finally {
                    _uiState.update { it.copy(isSyncing = false) }
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun submitOnlineSearch() {
        val q = _uiState.value.searchQuery.trim()
        if (q.isBlank()) return
        if (!lessonsRepo.isNetworkConnected()) {
            _uiState.update { it.copy(statusBanner = "اتصل بالإنترنت للبحث في القنوات الرسمية على يوتيوب") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, statusBanner = "جاري البحث والتحقق من فيديوهات: $q...") }
            try {
                val found = lessonsRepo.searchYouTubeOfficialLessons(q)
                _customSearchLessons.value = found
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        statusBanner = if (found.isEmpty()) {
                            "لم يُعثر على فيديوهات موثقة بالصوت والصورة لـ \"$q\""
                        } else {
                            null
                        }
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun refreshCurrentScholar() {
        val scholar = ScholarCatalog.scholars.find { it.id == _uiState.value.selectedScholarId }
            ?: ScholarCatalog.scholars.first()
        if (!lessonsRepo.isNetworkConnected()) {
            _uiState.update { it.copy(statusBanner = "أنت غير متصل بالإنترنت — اتصل لعرض وتحديث الفيديوهات الرسمية") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            try {
                val list = lessonsRepo.syncScholarLessons(scholar)
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        statusBanner = if (list.isNotEmpty()) {
                            "تم تحديث القنوات الرسمية (${list.size} فيديو موثق بالصوت والصورة)"
                        } else {
                            "لا توجد فيديوهات متاحة حالياً لهذا الشيخ"
                        }
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun setShowFavoritesOnly(favoritesOnly: Boolean) {
        _uiState.update { it.copy(showFavoritesOnly = favoritesOnly) }
    }

    fun toggleFavorite(youtubeVideoId: String) {
        lessonsRepo.toggleFavorite(youtubeVideoId)
    }

    fun openLesson(lesson: ReligiousLesson) {
        if (!lessonsRepo.isNetworkConnected()) {
            _uiState.update {
                it.copy(statusBanner = "يرجى الاتصال بالإنترنت لتشغيل فيديو يوتيوب الرسمي بجودة عالية")
            }
            return
        }
        _uiState.update {
            it.copy(
                activeLesson = lesson,
                statusBanner = null
            )
        }
    }

    fun handleBrokenOrSilentLesson(brokenLesson: ReligiousLesson, reason: String) {
        sessionFailedVideoIds.add(brokenLesson.youtubeVideoId)
        viewModelScope.launch {
            lessonsRepo.removeAndBlacklistLesson(brokenLesson)
            _customSearchLessons.update { list ->
                list.filterNot { it.youtubeVideoId == brokenLesson.youtubeVideoId }
            }

            // Silently pick next verified working video from the active list or scholar bucket
            val candidates = (displayedLessons.value +
                lessonsRepo.lessonsByScholar.value[brokenLesson.scholarId].orEmpty())
                .filterNot {
                    it.youtubeVideoId == brokenLesson.youtubeVideoId ||
                        sessionFailedVideoIds.contains(it.youtubeVideoId)
                }

            val alternative = candidates.firstOrNull()
            if (alternative != null) {
                _uiState.update {
                    it.copy(
                        activeLesson = alternative,
                        statusBanner = null
                    )
                }
            } else {
                // Attempt fresh silent sync from official channel before closing
                val scholar = ScholarCatalog.scholars.find { s -> s.id == _uiState.value.selectedScholarId }
                    ?: ScholarCatalog.scholars.find { s -> s.id == brokenLesson.scholarId }
                val fresh = if (scholar != null && lessonsRepo.isNetworkConnected()) {
                    lessonsRepo.syncScholarLessons(scholar)
                        .firstOrNull { !sessionFailedVideoIds.contains(it.youtubeVideoId) }
                } else null

                _uiState.update {
                    it.copy(
                        activeLesson = fresh,
                        statusBanner = null
                    )
                }
            }
        }
    }

    fun closePlayer() {
        _uiState.update { it.copy(activeLesson = null) }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }
}
