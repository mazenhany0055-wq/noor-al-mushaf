package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NoorAlMushafApp
import com.example.data.catalog.ScholarCatalog
import com.example.domain.model.LessonMediaType
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
    val showOfflineOnly: Boolean = false,
    val mediaFilter: LessonMediaType? = null,
    val isSyncing: Boolean = false,
    val statusBanner: String? = null,
    val activeLesson: ReligiousLesson? = null,
    val activePlayableUri: Uri? = null,
    val audioOnlyInPlayer: Boolean = false
)

class LessonsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NoorAlMushafApp
    private val lessonsRepo = app.lessonsRepository

    private val _uiState = MutableStateFlow(LessonsUiState())
    val uiState: StateFlow<LessonsUiState> = _uiState.asStateFlow()

    private val _customSearchLessons = MutableStateFlow<List<ReligiousLesson>>(emptyList())

    val offlineLessonFiles: StateFlow<Map<String, String>> = lessonsRepo.offlineLessonFiles
    val downloadProgress: StateFlow<Map<String, Float>> = lessonsRepo.downloadProgress
    val totalOfflineBytes: StateFlow<Long> = lessonsRepo.totalOfflineBytes

    val displayedLessons: StateFlow<List<ReligiousLesson>> = combine(
        _uiState,
        lessonsRepo.lessonsByScholar,
        _customSearchLessons,
        lessonsRepo.offlineLessonFiles
    ) { state, byScholar, customResults, offlineFiles ->
        val baseList = if (state.searchQuery.isNotBlank()) {
            val allKnown = byScholar.values.flatten() + customResults
            val q = state.searchQuery.trim()
            allKnown
                .distinctBy { it.mediaUrl }
                .filter {
                    it.title.contains(q, ignoreCase = true) ||
                        it.scholarName.contains(q, ignoreCase = true) ||
                        it.description.contains(q, ignoreCase = true)
                }
        } else {
            byScholar[state.selectedScholarId].orEmpty()
        }

        baseList.filter { lesson ->
            val passOffline = !state.showOfflineOnly || lessonsRepo.isDownloadedOffline(lesson.id)
            val passMedia = state.mediaFilter == null || lesson.mediaType == state.mediaFilter
            passOffline && passMedia
        }.sortedWith(
            compareByDescending<ReligiousLesson> { lessonsRepo.isDownloadedOffline(it.id) && state.showOfflineOnly }
                .thenByDescending { it.isNewArrival }
                .thenByDescending { it.mediaType == LessonMediaType.VIDEO }
        ).also {
            // Keep reference to offlineFiles so combine reacts cleanly when downloads complete
            offlineFiles.size
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        autoSyncInitialScholars()
    }

    private fun autoSyncInitialScholars() {
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
                    lessonsRepo.syncScholarLessons(scholar)
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
            _uiState.update { it.copy(statusBanner = "لا يوجد اتصال بالإنترنت — يتم البحث في الدروس المحفوظة") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, statusBanner = "جاري البحث التلقائي عن دروس: $q...") }
            try {
                val found = lessonsRepo.searchCustomScholarOrTopic(q)
                _customSearchLessons.value = found
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        statusBanner = if (found.isEmpty()) "لم يُعثر على دروس إضافية لـ \"$q\"" else null
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
            _uiState.update { it.copy(statusBanner = "أنت غير متصل بالإنترنت الآن — يمكنك تشغيل الدروس المحفوظة بدون نت") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            try {
                val list = lessonsRepo.syncScholarLessons(scholar)
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        statusBanner = "تم تحديث الدروس تلقائياً (${list.size} درس متاح)"
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun setShowOfflineOnly(offlineOnly: Boolean) {
        _uiState.update { it.copy(showOfflineOnly = offlineOnly) }
    }

    fun setMediaFilter(filter: LessonMediaType?) {
        _uiState.update { it.copy(mediaFilter = filter) }
    }

    fun openLesson(lesson: ReligiousLesson) {
        val isOfflineReady = lessonsRepo.isDownloadedOffline(lesson.id)
        if (!isOfflineReady && !lessonsRepo.isNetworkConnected()) {
            _uiState.update {
                it.copy(statusBanner = "هذا الدرس غير محمّل بعد — اتصل بالإنترنت للمشاهدة أو التحميل بدون نت")
            }
            return
        }
        val uri = lessonsRepo.getPlayableUri(lesson)
        _uiState.update {
            it.copy(
                activeLesson = lesson,
                activePlayableUri = uri,
                audioOnlyInPlayer = lesson.mediaType == LessonMediaType.AUDIO,
                statusBanner = null
            )
        }
    }

    fun togglePlayerAudioOnly() {
        val current = _uiState.value.activeLesson ?: return
        if (current.mediaType == LessonMediaType.AUDIO) return
        val nextAudioOnly = !_uiState.value.audioOnlyInPlayer
        val nextUri = if (nextAudioOnly && !lessonsRepo.isDownloadedOffline(current.id) && current.alternateAudioUrl != null) {
            Uri.parse(current.alternateAudioUrl)
        } else {
            lessonsRepo.getPlayableUri(current)
        }
        _uiState.update {
            it.copy(
                audioOnlyInPlayer = nextAudioOnly,
                activePlayableUri = nextUri
            )
        }
    }

    fun closePlayer() {
        _uiState.update {
            it.copy(
                activeLesson = null,
                activePlayableUri = null,
                audioOnlyInPlayer = false
            )
        }
    }

    fun downloadLesson(lesson: ReligiousLesson) {
        if (!lessonsRepo.isNetworkConnected()) {
            _uiState.update { it.copy(statusBanner = "اتصل بالإنترنت أولاً لتحميل الدرس ومشاهدته بدون نت") }
            return
        }
        viewModelScope.launch {
            val res = lessonsRepo.downloadLesson(lesson)
            if (res.isSuccess) {
                // If user is currently playing this lesson, swap seamlessly to offline file URI
                val active = _uiState.value.activeLesson
                if (active?.id == lesson.id) {
                    _uiState.update { it.copy(activePlayableUri = lessonsRepo.getPlayableUri(lesson)) }
                }
                _uiState.update { it.copy(statusBanner = "تم حفظ \"${lesson.title}\" للعمل بدون نت ✓") }
            } else {
                _uiState.update { it.copy(statusBanner = "تعذر التحميل الآن — حاول مرة أخرى") }
            }
        }
    }

    fun deleteDownloadedLesson(lessonId: String) {
        viewModelScope.launch {
            lessonsRepo.deleteDownloadedLesson(lessonId)
            _uiState.update { it.copy(statusBanner = "تم حذف الملف المحفوظ لتوفير المساحة") }
        }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }
}
