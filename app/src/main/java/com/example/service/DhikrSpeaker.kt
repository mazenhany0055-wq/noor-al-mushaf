package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.data.catalog.AdhkarCatalog
import com.example.data.repository.OfflineAudioRepository
import com.example.domain.model.AdhkarSheikhVoice
import com.example.domain.model.AdhkarVoicePreset
import com.example.domain.model.DhikrItem
import com.example.domain.model.OfflineTtsVoiceInfo
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

class DhikrSpeaker(
    context: Context,
    private val offlineAudioRepository: OfflineAudioRepository = OfflineAudioRepository(context)
) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var fetchJob: Job? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady: Boolean = false
    private var mediaPlayer: MediaPlayer? = null
    private var currentContinuation: CancellableContinuation<Unit>? = null
    private var currentOnFinished: (() -> Unit)? = null

    private val _availableSystemVoices = MutableStateFlow<List<OfflineTtsVoiceInfo>>(emptyList())
    val availableSystemVoices: StateFlow<List<OfflineTtsVoiceInfo>> = _availableSystemVoices.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    fun warmUp() {
        ensureTtsInitialized {}
    }

    fun playDhikrItem(
        item: DhikrItem,
        voiceId: String,
        quranReciterId: String,
        chimeBefore: Boolean,
        useMediaUsage: Boolean = true,
        onFinished: (() -> Unit)? = null
    ) {
        stop()

        val sheikh = AdhkarSheikhVoice.fromId(voiceId)
        val effectiveReciterId = if (voiceId.startsWith("sheikh_")) sheikh.quranReciterId else quranReciterId

        // 1. Try instant local/bundled real-sheikh recording
        val immediateUri = resolveLocalDhikrUri(item, effectiveReciterId)
        if (immediateUri != null) {
            if (chimeBefore) playChime()
            if (playOfflineUri(immediateUri, useMediaUsage, onFinished)) {
                return
            }
        }

        // 2. If online, fetch real Sheikh MP3 recording cleanly (never robotic TTS!)
        if (offlineAudioRepository.isNetworkConnected()) {
            _isSpeaking.value = true
            fetchJob = scope.launch {
                val remoteCachedUri = fetchRemoteDhikrUri(item, effectiveReciterId)
                if (remoteCachedUri != null) {
                    if (chimeBefore) playChime()
                    if (playOfflineUri(remoteCachedUri, useMediaUsage, onFinished)) {
                        return@launch
                    }
                }
                speakDhikr(
                    phrase = item.text,
                    voiceId = voiceId,
                    chimeBefore = chimeBefore,
                    useMediaUsage = useMediaUsage,
                    onFinished = onFinished
                )
            }
            return
        }

        // 3. Offline emergency fallback only if clip was not bundled/cached
        speakDhikr(
            phrase = item.text,
            voiceId = voiceId,
            chimeBefore = chimeBefore,
            useMediaUsage = useMediaUsage,
            onFinished = onFinished
        )
    }

    fun playPeriodicDhikr(
        phraseIndex: Int,
        phraseText: String,
        voiceId: String,
        chimeBefore: Boolean,
        useMediaUsage: Boolean = true,
        onFinished: (() -> Unit)? = null
    ) {
        stop()
        val offlineUri = offlineAudioRepository.getOfflinePeriodicDhikrUri(phraseIndex)
        if (offlineUri != null) {
            if (chimeBefore) playChime()
            if (playOfflineUri(offlineUri, useMediaUsage, onFinished)) {
                return
            }
        }

        if (offlineAudioRepository.isNetworkConnected()) {
            _isSpeaking.value = true
            fetchJob = scope.launch {
                val cached = offlineAudioRepository.fetchAndCachePeriodicClip(phraseIndex, phraseText)
                if (cached != null) {
                    if (chimeBefore) playChime()
                    if (playOfflineUri(cached, useMediaUsage, onFinished)) {
                        return@launch
                    }
                }
                speakDhikr(
                    phrase = phraseText,
                    voiceId = voiceId,
                    chimeBefore = chimeBefore,
                    useMediaUsage = useMediaUsage,
                    onFinished = onFinished
                )
            }
            return
        }

        speakDhikr(
            phrase = phraseText,
            voiceId = voiceId,
            chimeBefore = chimeBefore,
            useMediaUsage = useMediaUsage,
            onFinished = onFinished
        )
    }

    suspend fun playPeriodicDhikrAndAwait(
        phraseIndex: Int,
        phraseText: String,
        voiceId: String,
        chimeBefore: Boolean,
        timeoutMs: Long = 22_000L
    ) {
        val localUri = offlineAudioRepository.getOfflinePeriodicDhikrUri(phraseIndex)
            ?: if (offlineAudioRepository.isNetworkConnected()) {
                offlineAudioRepository.fetchAndCachePeriodicClip(phraseIndex, phraseText)
            } else {
                null
            }

        if (localUri != null) {
            if (chimeBefore) playChime()
            val playedRealSheikh = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    val started = playOfflineUri(localUri, useMediaUsage = false) {
                        if (cont.isActive) cont.resume(true)
                    }
                    if (!started && cont.isActive) {
                        cont.resume(false)
                    }
                    cont.invokeOnCancellation { stop() }
                }
            }
            if (playedRealSheikh == true) return
        }

        speakAndAwait(
            phrase = phraseText,
            voiceId = voiceId,
            chimeBefore = chimeBefore,
            timeoutMs = 9_000L
        )
    }

    fun playSheikhSession(
        sheikh: AdhkarSheikhVoice,
        isMorning: Boolean,
        onFinished: (() -> Unit)? = null
    ): Boolean {
        stop()
        val offlineUri = offlineAudioRepository.getOfflineAdhkarSessionUri(sheikh, isMorning)
        if (offlineUri != null) {
            return playOfflineUri(offlineUri, useMediaUsage = true, onFinished = onFinished)
        }
        val streamUrl = if (isMorning) sheikh.morningSessionUrl else sheikh.eveningSessionUrl
        if (streamUrl != null && offlineAudioRepository.isNetworkConnected()) {
            return playOfflineUri(Uri.parse(streamUrl), useMediaUsage = true, onFinished = onFinished)
        }
        return false
    }

    fun previewSheikhVoice(
        sheikh: AdhkarSheikhVoice,
        chimeBefore: Boolean = false,
        onFinished: (() -> Unit)? = null
    ) {
        stop()
        // Try playing Sheikh's genuine recording (Ayat Al-Kursi / Fatiha / Hisn Dhikr / Session)
        val reciterAyah = offlineAudioRepository.getOfflineAyahUri(sheikh.quranReciterId, 2, 255)
            ?: offlineAudioRepository.getOfflineDhikrClipUri("m2")
            ?: offlineAudioRepository.getOfflinePeriodicDhikrUri(0)
            ?: offlineAudioRepository.getOfflineSurahUri(sheikh.quranReciterId, 1)
            ?: offlineAudioRepository.getOfflineAdhkarSessionUri(sheikh, true)

        if (sheikh == AdhkarSheikhVoice.HAMAD_AL_DURAYHIM) {
            val hisnUri = offlineAudioRepository.getOfflineDhikrClipUri("m2")
                ?: offlineAudioRepository.getOfflinePeriodicDhikrUri(0)
            if (hisnUri != null) {
                if (chimeBefore) playChime()
                if (playOfflineUri(hisnUri, useMediaUsage = true, onFinished = onFinished)) return
            }
        }

        if (reciterAyah != null) {
            if (chimeBefore) playChime()
            if (playOfflineUri(reciterAyah, useMediaUsage = true, onFinished = onFinished)) return
        }

        if (offlineAudioRepository.isNetworkConnected()) {
            _isSpeaking.value = true
            fetchJob = scope.launch {
                val downloaded = if (sheikh == AdhkarSheikhVoice.HAMAD_AL_DURAYHIM) {
                    offlineAudioRepository.fetchAndCacheHisnDhikrClip("m2", AdhkarCatalog.allDhikr.firstOrNull { it.id == "m2" }?.text ?: "")
                } else {
                    offlineAudioRepository.downloadAyahClip(sheikh.quranReciterId, 2, 255)
                        ?: offlineAudioRepository.fetchAndCachePeriodicClip(0, AdhkarCatalog.periodicDhikrPhrases.first())
                }
                if (downloaded != null) {
                    if (chimeBefore) playChime()
                    if (playOfflineUri(downloaded, useMediaUsage = true, onFinished = onFinished)) return@launch
                }
                _isSpeaking.value = false
                onFinished?.invoke()
            }
            return
        }

        speakDhikr(
            phrase = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، سُبْحَانَ اللَّهِ الْعَظِيمِ",
            voiceId = sheikh.id,
            chimeBefore = chimeBefore,
            useMediaUsage = true,
            onFinished = onFinished
        )
    }

    fun speakDhikr(
        phrase: String,
        voiceId: String = AdhkarVoicePreset.CALM_DEEP.id,
        chimeBefore: Boolean = false,
        useMediaUsage: Boolean = false,
        audioUsage: Int? = null,
        onFinished: (() -> Unit)? = null
    ) {
        stop()
        if (chimeBefore) {
            playChime()
        }

        val cleaned = cleanPhraseForSpeech(phrase)
        val resolvedUsage = audioUsage
            ?: if (useMediaUsage) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_NOTIFICATION
        currentOnFinished = onFinished
        _isSpeaking.value = true

        ensureTtsInitialized { ready ->
            val engine = tts
            if (!ready || engine == null) {
                _isSpeaking.value = false
                val cb = currentOnFinished
                currentOnFinished = null
                cb?.invoke()
                return@ensureTtsInitialized
            }

            try {
                applyVoiceSelection(engine, voiceId)
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(resolvedUsage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                val utteranceId = "dhikr_${System.currentTimeMillis()}"
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(id: String?) {
                        if (id == utteranceId) {
                            notifyDone()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) {
                        if (id == utteranceId) {
                            notifyDone()
                        }
                    }
                })
                val res = engine.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                if (res != TextToSpeech.SUCCESS) {
                    notifyDone()
                }
            } catch (_: Exception) {
                notifyDone()
            }
        }
    }

    suspend fun speakAndAwait(
        phrase: String,
        voiceId: String,
        chimeBefore: Boolean,
        timeoutMs: Long = 9_000L,
        audioUsage: Int = AudioAttributes.USAGE_NOTIFICATION
    ) {
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Unit> { cont ->
                currentContinuation = cont
                speakDhikr(
                    phrase = phrase,
                    voiceId = voiceId,
                    chimeBefore = chimeBefore,
                    useMediaUsage = false,
                    audioUsage = audioUsage
                ) {
                    if (cont.isActive) {
                        cont.resume(Unit)
                    }
                }
                cont.invokeOnCancellation {
                    stop()
                }
            }
        }
    }

    fun stop() {
        fetchJob?.cancel()
        fetchJob = null
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        releaseMediaPlayer()
        _isSpeaking.value = false
        currentOnFinished = null
        currentContinuation = null
    }

    fun release() {
        stop()
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        } finally {
            tts = null
            isTtsReady = false
        }
    }

    private fun resolveLocalDhikrUri(item: DhikrItem, reciterId: String): Uri? {
        val surah = item.surahNumber
        if (surah != null) {
            if (item.id == "m_muawwidhat") {
                offlineAudioRepository.getOfflineSurahUri(reciterId, 112)?.let { return it }
                offlineAudioRepository.getOfflineSurahUri("afs", 112)?.let { return it }
                offlineAudioRepository.getOfflineSurahUri("yasser", 112)?.let { return it }
            } else if (item.ayahNumbers.isNotEmpty()) {
                val ayah = item.ayahNumbers.first()
                offlineAudioRepository.getOfflineAyahUri(reciterId, surah, ayah)?.let { return it }
                offlineAudioRepository.getOfflineAyahUri("afs", surah, ayah)?.let { return it }
                offlineAudioRepository.getOfflineAyahUri("yasser", surah, ayah)?.let { return it }
            }
        }
        return offlineAudioRepository.getOfflineDhikrClipUri(item.id)
    }

    private suspend fun fetchRemoteDhikrUri(item: DhikrItem, reciterId: String): Uri? {
        val surah = item.surahNumber
        if (surah != null && item.ayahNumbers.isNotEmpty()) {
            offlineAudioRepository.downloadAyahClip(reciterId, surah, item.ayahNumbers.first())?.let {
                return it
            }
        }
        return offlineAudioRepository.fetchAndCacheHisnDhikrClip(item.id, item.text)
    }

    private fun notifyDone() {
        _isSpeaking.value = false
        val cb = currentOnFinished
        currentOnFinished = null
        val cont = currentContinuation
        currentContinuation = null
        cb?.invoke()
        if (cont != null && cont.isActive) {
            cont.resume(Unit)
        }
    }

    private fun ensureTtsInitialized(onReady: (Boolean) -> Unit) {
        val existing = tts
        if (existing != null && isTtsReady) {
            onReady(true)
            return
        }
        try {
            existing?.shutdown()
        } catch (_: Exception) {
        }
        isTtsReady = false
        try {
            tts = TextToSpeech(appContext) { status ->
                val success = status == TextToSpeech.SUCCESS
                isTtsReady = success
                if (success) {
                    tts?.let { engine ->
                        val arabicLocale = Locale("ar")
                        val langRes = engine.setLanguage(arabicLocale)
                        if (langRes == TextToSpeech.LANG_MISSING_DATA || langRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                            engine.language = Locale.getDefault()
                        }
                        refreshDiscoveredVoices(engine)
                    }
                }
                onReady(success)
            }
        } catch (_: Exception) {
            isTtsReady = false
            onReady(false)
        }
    }

    private fun refreshDiscoveredVoices(engine: TextToSpeech) {
        try {
            val allVoices = engine.voices ?: return
            val arabicVoices = allVoices
                .filter { it.locale?.language?.equals("ar", ignoreCase = true) == true }
                .sortedWith(
                    compareBy<Voice> { it.isNetworkConnectionRequired }
                        .thenByDescending { it.quality }
                        .thenBy { it.name }
                )

            _availableSystemVoices.value = arabicVoices.mapIndexed { idx, v ->
                val offlineLabel = if (!v.isNetworkConnectionRequired) "بدون نت" else "عبر الشبكة"
                val countryTag = v.locale?.country?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                val friendlyName = "صوت عربي ${PrayerTimeCalculatorDigits.toArabic(idx + 1)}$countryTag • $offlineLabel"
                OfflineTtsVoiceInfo(
                    id = "sys:${v.name}",
                    rawName = v.name,
                    displayName = friendlyName,
                    isOfflineReady = !v.isNetworkConnectionRequired
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun applyVoiceSelection(engine: TextToSpeech, voiceId: String) {
        val arabicLocale = Locale("ar")
        if (engine.language?.language != "ar") {
            val result = engine.setLanguage(arabicLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.language = Locale.getDefault()
            }
        }

        if (voiceId.startsWith("sys:")) {
            val targetName = voiceId.removePrefix("sys:")
            val match = engine.voices?.firstOrNull { it.name == targetName }
            if (match != null) {
                engine.voice = match
            }
            engine.setPitch(0.96f)
            engine.setSpeechRate(0.90f)
            return
        }

        val preset = AdhkarVoicePreset.fromId(voiceId) ?: AdhkarVoicePreset.CALM_DEEP
        val offlineArabicVoices = engine.voices
            ?.filter { it.locale?.language?.equals("ar", ignoreCase = true) == true && !it.isNetworkConnectionRequired }
            ?.sortedByDescending { it.quality }
            .orEmpty()
        if (offlineArabicVoices.isNotEmpty()) {
            engine.voice = offlineArabicVoices.first()
        }
        engine.setPitch(preset.pitch)
        engine.setSpeechRate(preset.speechRate)
    }

    private fun playOfflineUri(
        uri: Uri,
        useMediaUsage: Boolean,
        onFinished: (() -> Unit)?
    ): Boolean {
        return try {
            releaseMediaPlayer()
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(if (useMediaUsage) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                if (uri.scheme == "asset") {
                    val assetPath = uri.path?.removePrefix("/") ?: ""
                    appContext.assets.openFd(assetPath).use { afd ->
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    }
                } else {
                    setDataSource(appContext, uri)
                }
                setOnPreparedListener {
                    _isSpeaking.value = true
                    it.start()
                }
                setOnCompletionListener {
                    releaseMediaPlayer()
                    _isSpeaking.value = false
                    onFinished?.invoke()
                }
                setOnErrorListener { _, _, _ ->
                    releaseMediaPlayer()
                    _isSpeaking.value = false
                    onFinished?.invoke()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
            true
        } catch (_: Exception) {
            releaseMediaPlayer()
            false
        }
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {
        } finally {
            mediaPlayer = null
        }
    }

    private fun playChime() {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(appContext, ringtoneUri)?.play()
        } catch (_: Exception) {
        }
    }

    private fun cleanPhraseForSpeech(raw: String): String {
        return raw
            .replace("*", "، ")
            .replace("۞", "")
            .replace("ۚ", "، ")
            .replace("ۖ", " ")
            .replace("ۗ", " ")
            .replace("ۛ", " ")
            .replace("ﷺ", "صلى الله عليه وسلم")
            .trim()
    }

    private object PrayerTimeCalculatorDigits {
        private val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        fun toArabic(number: Int): String = number.toString().map { ch ->
            if (ch.isDigit()) arabicDigits[ch - '0'] else ch
        }.joinToString("")
    }

    companion object {
        fun buildSpokenAdhanText(prayerName: String): String {
            val fajrLine = if (prayerName.contains("الفجر")) "، الصلاة خير من النوم، الصلاة خير من النوم" else ""
            return "الله أكبر الله أكبر، الله أكبر الله أكبر، أشهد أن لا إله إلا الله، أشهد أن لا إله إلا الله، أشهد أن محمداً رسول الله، أشهد أن محمداً رسول الله، حيّ على الصلاة، حيّ على الفلاح$fajrLine، الله أكبر الله أكبر، لا إله إلا الله. حان الآن موعد صلاة $prayerName"
        }
    }
}
