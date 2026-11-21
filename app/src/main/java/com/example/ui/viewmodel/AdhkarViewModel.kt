package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NoorAlMushafApp
import com.example.data.catalog.AdhkarCatalog
import com.example.domain.model.AdhkarCategory
import com.example.domain.model.AdhkarSheikhVoice
import com.example.domain.model.DhikrItem
import com.example.service.DhikrSpeaker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

data class AdhkarUiState(
    val selectedCategory: AdhkarCategory = AdhkarCategory.MORNING,
    val remainingCounts: Map<String, Int> = emptyMap(),
    val speakingDhikrId: String? = null,
    val isAutoPlayingCategory: Boolean = false,
    val activeSessionLabel: String? = null,
    val voiceStatusMessage: String? = null
) {
    val categoryItems: List<DhikrItem>
        get() = AdhkarCatalog.allDhikr.filter { it.category == selectedCategory }

    fun remainingFor(item: DhikrItem): Int = remainingCounts[item.id] ?: item.targetCount
}

class AdhkarViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NoorAlMushafApp
    private val speaker = DhikrSpeaker(application, app.offlineAudioRepository)
    private var autoPlayJob: Job? = null

    private val _uiState = MutableStateFlow(AdhkarUiState())
    val uiState: StateFlow<AdhkarUiState> = _uiState.asStateFlow()

    fun selectCategory(category: AdhkarCategory) {
        stopSpeaking()
        _uiState.update { it.copy(selectedCategory = category, voiceStatusMessage = null) }
    }

    fun decrementDhikr(item: DhikrItem) {
        _uiState.update { current ->
            val rem = current.remainingFor(item)
            if (rem <= 0) return@update current
            current.copy(remainingCounts = current.remainingCounts + (item.id to (rem - 1)))
        }
    }

    fun resetCategory() {
        _uiState.update { current ->
            val idsInCategory = current.categoryItems.map { it.id }.toSet()
            current.copy(remainingCounts = current.remainingCounts - idsInCategory)
        }
    }

    fun speakSingleDhikr(
        item: DhikrItem,
        voiceId: String,
        quranReciterId: String,
        chimeBefore: Boolean
    ) {
        val wasSame = _uiState.value.speakingDhikrId == item.id && !_uiState.value.isAutoPlayingCategory
        stopSpeaking()
        if (wasSame) return

        _uiState.update {
            it.copy(
                speakingDhikrId = item.id,
                isAutoPlayingCategory = false,
                voiceStatusMessage = null
            )
        }
        speaker.playDhikrItem(
            item = item,
            voiceId = voiceId,
            quranReciterId = quranReciterId,
            chimeBefore = chimeBefore,
            useMediaUsage = true,
            onUnavailable = { reason ->
                _uiState.update {
                    it.copy(
                        speakingDhikrId = null,
                        isAutoPlayingCategory = false,
                        voiceStatusMessage = reason
                    )
                }
            },
            onFinished = {
                _uiState.update { cur ->
                    if (cur.speakingDhikrId == item.id && !cur.isAutoPlayingCategory) {
                        cur.copy(speakingDhikrId = null)
                    } else {
                        cur
                    }
                }
            }
        )
    }

    fun toggleAutoPlayCategory(
        voiceId: String,
        quranReciterId: String,
        chimeBefore: Boolean
    ) {
        if (_uiState.value.isAutoPlayingCategory) {
            stopSpeaking()
            return
        }

        val items = _uiState.value.categoryItems
        if (items.isEmpty()) return

        stopSpeaking()
        _uiState.update { it.copy(isAutoPlayingCategory = true, voiceStatusMessage = null) }

        autoPlayJob = viewModelScope.launch {
            var playedAny = false
            var lastUnavailableReason: String? = null
            for (item in items) {
                if (!_uiState.value.isAutoPlayingCategory) break
                _uiState.update { it.copy(speakingDhikrId = item.id) }
                val playedThis = withTimeoutOrNull(45_000L) {
                    suspendCancellableCoroutine<Boolean> { cont ->
                        speaker.playDhikrItem(
                            item = item,
                            voiceId = voiceId,
                            quranReciterId = quranReciterId,
                            chimeBefore = chimeBefore,
                            useMediaUsage = true,
                            onUnavailable = { reason ->
                                lastUnavailableReason = reason
                                if (cont.isActive) cont.resumeWith(Result.success(false))
                            },
                            onFinished = {
                                if (cont.isActive) cont.resumeWith(Result.success(true))
                            }
                        )
                        cont.invokeOnCancellation {
                            speaker.stop()
                        }
                    }
                } ?: false

                if (playedThis) {
                    playedAny = true
                    delay(450L)
                }
            }
            _uiState.update {
                it.copy(
                    speakingDhikrId = null,
                    isAutoPlayingCategory = false,
                    voiceStatusMessage = if (!playedAny) {
                        lastUnavailableReason ?: "غير متوفر: لا توجد تسجيلات صوتية حقيقية متاحة لهذا القسم بصوت الشيخ المختار"
                    } else {
                        it.voiceStatusMessage
                    }
                )
            }
        }
    }

    fun previewVoice(voiceId: String, chimeBefore: Boolean) {
        stopSpeaking()
        _uiState.update { it.copy(voiceStatusMessage = null) }
        speaker.previewSheikhVoice(
            sheikh = AdhkarSheikhVoice.fromId(voiceId),
            chimeBefore = chimeBefore,
            onUnavailable = { reason ->
                _uiState.update { it.copy(voiceStatusMessage = reason) }
            }
        )
    }

    fun playSheikhSession(sheikh: AdhkarSheikhVoice, isMorning: Boolean): Boolean {
        val label = "${sheikh.arabicName} • ${if (isMorning) "أذكار الصباح كاملة" else "أذكار المساء كاملة"}"
        if (_uiState.value.activeSessionLabel == label) {
            stopSpeaking()
            return true
        }
        stopSpeaking()
        _uiState.update { it.copy(voiceStatusMessage = null) }
        val started = speaker.playSheikhSession(
            sheikh = sheikh,
            isMorning = isMorning,
            onUnavailable = { reason ->
                _uiState.update { it.copy(activeSessionLabel = null, voiceStatusMessage = reason) }
            },
            onFinished = {
                _uiState.update { it.copy(activeSessionLabel = null) }
            }
        )
        if (started) {
            _uiState.update { it.copy(activeSessionLabel = label) }
        }
        return started
    }

    fun clearVoiceStatusMessage() {
        _uiState.update { it.copy(voiceStatusMessage = null) }
    }

    fun stopSpeaking() {
        autoPlayJob?.cancel()
        autoPlayJob = null
        speaker.stop()
        _uiState.update {
            it.copy(
                speakingDhikrId = null,
                isAutoPlayingCategory = false,
                activeSessionLabel = null
            )
        }
    }

    override fun onCleared() {
        autoPlayJob?.cancel()
        speaker.release()
        super.onCleared()
    }
}
