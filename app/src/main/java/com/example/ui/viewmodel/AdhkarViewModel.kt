package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NoorAlMushafApp
import com.example.data.catalog.AdhkarCatalog
import com.example.domain.model.AdhkarCategory
import com.example.domain.model.DhikrItem
import com.example.domain.model.OfflineTtsVoiceInfo
import com.example.service.DhikrSpeaker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdhkarUiState(
    val selectedCategory: AdhkarCategory = AdhkarCategory.MORNING,
    val remainingCounts: Map<String, Int> = emptyMap(),
    val speakingDhikrId: String? = null,
    val isAutoPlayingCategory: Boolean = false
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

    val availableSystemVoices: StateFlow<List<OfflineTtsVoiceInfo>> = speaker.availableSystemVoices

    init {
        speaker.warmUp()
    }

    fun selectCategory(category: AdhkarCategory) {
        stopSpeaking()
        _uiState.update { it.copy(selectedCategory = category) }
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

        _uiState.update { it.copy(speakingDhikrId = item.id, isAutoPlayingCategory = false) }
        speaker.playDhikrItem(
            item = item,
            voiceId = voiceId,
            quranReciterId = quranReciterId,
            chimeBefore = chimeBefore,
            useMediaUsage = true
        ) {
            _uiState.update { cur ->
                if (cur.speakingDhikrId == item.id && !cur.isAutoPlayingCategory) {
                    cur.copy(speakingDhikrId = null)
                } else {
                    cur
                }
            }
        }
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
        _uiState.update { it.copy(isAutoPlayingCategory = true) }

        autoPlayJob = viewModelScope.launch {
            for (item in items) {
                if (!_uiState.value.isAutoPlayingCategory) break
                _uiState.update { it.copy(speakingDhikrId = item.id) }
                kotlinx.coroutines.withTimeoutOrNull(45_000L) {
                    kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                        speaker.playDhikrItem(
                            item = item,
                            voiceId = voiceId,
                            quranReciterId = quranReciterId,
                            chimeBefore = chimeBefore,
                            useMediaUsage = true
                        ) {
                            if (cont.isActive) {
                                cont.resumeWith(Result.success(Unit))
                            }
                        }
                        cont.invokeOnCancellation {
                            speaker.stop()
                        }
                    }
                }
                delay(450L)
            }
            _uiState.update { it.copy(speakingDhikrId = null, isAutoPlayingCategory = false) }
        }
    }

    fun previewVoice(voiceId: String, chimeBefore: Boolean) {
        stopSpeaking()
        speaker.speakDhikr(
            phrase = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، سُبْحَانَ اللَّهِ الْعَظِيمِ",
            voiceId = voiceId,
            chimeBefore = chimeBefore,
            useMediaUsage = true
        )
    }

    fun stopSpeaking() {
        autoPlayJob?.cancel()
        autoPlayJob = null
        speaker.stop()
        _uiState.update { it.copy(speakingDhikrId = null, isAutoPlayingCategory = false) }
    }

    override fun onCleared() {
        autoPlayJob?.cancel()
        speaker.release()
        super.onCleared()
    }
}
