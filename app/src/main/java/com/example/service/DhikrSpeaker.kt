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
    private var rotationCursor: Int = 0

    private val _availableSystemVoices = MutableStateFlow<List<OfflineTtsVoiceInfo>>(emptyList())
    val availableSystemVoices: StateFlow<List<OfflineTtsVoiceInfo>> = _availableSystemVoices.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    fun warmUp() {
        // Real human Sheikh MP3 audio requires no synthetic TTS initialization.
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
        val effectiveReciterId = resolveReciterForPlay(sheikh, quranReciterId)

        // 1. Try instant local/bundled real-sheikh recording (NEVER robotic TTS)
        val immediateUri = resolveLocalDhikrUri(item, effectiveReciterId)
        if (immediateUri != null) {
            if (chimeBefore) playChime()
            if (playOfflineUri(immediateUri, useMediaUsage, onFinished)) {
                return
            }
        }

        // 2. If online, fetch genuine Sheikh MP3 recording cleanly
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
                playEmergencyRealSheikhFallback(effectiveReciterId, chimeBefore, useMediaUsage, onFinished)
            }
            return
        }

        // 3. Strictly real human Sheikh fallback when offline (zero AI/TTS voice)
        playEmergencyRealSheikhFallback(effectiveReciterId, chimeBefore, useMediaUsage, onFinished)
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
        val sheikh = AdhkarSheikhVoice.fromId(voiceId)
        val localUri = resolvePeriodicLocalUri(sheikh, phraseIndex)
        if (localUri != null) {
            if (chimeBefore) playChime()
            if (playOfflineUri(localUri, useMediaUsage, onFinished)) {
                return
            }
        }

        if (offlineAudioRepository.isNetworkConnected()) {
            _isSpeaking.value = true
            fetchJob = scope.launch {
                val remote = fetchPeriodicRemoteUri(sheikh, phraseIndex, phraseText)
                if (remote != null) {
                    if (chimeBefore) playChime()
                    if (playOfflineUri(remote, useMediaUsage, onFinished)) {
                        return@launch
                    }
                }
                playEmergencyRealSheikhFallback(sheikh.quranReciterId, chimeBefore, useMediaUsage, onFinished)
            }
            return
        }

        playEmergencyRealSheikhFallback(sheikh.quranReciterId, chimeBefore, useMediaUsage, onFinished)
    }

    suspend fun playPeriodicDhikrAndAwait(
        phraseIndex: Int,
        phraseText: String,
        voiceId: String,
        chimeBefore: Boolean,
        timeoutMs: Long = 22_000L
    ) {
        val sheikh = AdhkarSheikhVoice.fromId(voiceId)
        val localUri = resolvePeriodicLocalUri(sheikh, phraseIndex)
            ?: if (offlineAudioRepository.isNetworkConnected()) {
                fetchPeriodicRemoteUri(sheikh, phraseIndex, phraseText)
            } else {
                null
            }
            ?: resolveAnyBundledRealSheikhUri(sheikh.quranReciterId)

        if (localUri != null) {
            if (chimeBefore) playChime()
            withTimeoutOrNull(timeoutMs) {
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
        } else if (chimeBefore) {
            playChime()
        }
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

        if (sheikh == AdhkarSheikhVoice.HAMAD_AL_DURAYHIM) {
            val hisnUri = offlineAudioRepository.getOfflineDhikrClipUri("m2")
                ?: offlineAudioRepository.getOfflinePeriodicDhikrUri(0)
            if (hisnUri != null) {
                if (chimeBefore) playChime()
                if (playOfflineUri(hisnUri, useMediaUsage = true, onFinished = onFinished)) return
            }
        }

        val targetReciterId = if (sheikh == AdhkarSheikhVoice.MULTI_SHEIKHS) {
            rotatingReciterIds[(rotationCursor++) % rotatingReciterIds.size]
        } else {
            sheikh.quranReciterId
        }
        val previewAyah = periodicAyahPool[(rotationCursor) % periodicAyahPool.size]

        val localPreview = offlineAudioRepository.getOfflineAyahUri(targetReciterId, previewAyah.first, previewAyah.second)
            ?: offlineAudioRepository.getOfflineAyahUri(targetReciterId, 2, 255)
            ?: if (sheikh == AdhkarSheikhVoice.MULTI_SHEIKHS) {
                offlineAudioRepository.getOfflineDhikrClipUri("m2")
                    ?: offlineAudioRepository.getOfflinePeriodicDhikrUri(0)
            } else null

        if (localPreview != null) {
            if (chimeBefore) playChime()
            if (playOfflineUri(localPreview, useMediaUsage = true, onFinished = onFinished)) return
        }

        if (offlineAudioRepository.isNetworkConnected()) {
            _isSpeaking.value = true
            fetchJob = scope.launch {
                val downloaded = if (sheikh == AdhkarSheikhVoice.HAMAD_AL_DURAYHIM) {
                    offlineAudioRepository.fetchAndCacheHisnDhikrClip(
                        "m2",
                        AdhkarCatalog.allDhikr.firstOrNull { it.id == "m2" }?.text ?: ""
                    )
                } else {
                    offlineAudioRepository.downloadAyahClip(targetReciterId, previewAyah.first, previewAyah.second)
                        ?: offlineAudioRepository.downloadAyahClip(targetReciterId, 2, 255)
                        ?: offlineAudioRepository.fetchAndCachePeriodicClip(0, AdhkarCatalog.periodicDhikrPhrases.first())
                }
                if (downloaded != null) {
                    if (chimeBefore) playChime()
                    if (playOfflineUri(downloaded, useMediaUsage = true, onFinished = onFinished)) return@launch
                }
                playEmergencyRealSheikhFallback(targetReciterId, chimeBefore, useMediaUsage = true, onFinished = onFinished)
            }
            return
        }

        playEmergencyRealSheikhFallback(targetReciterId, chimeBefore, useMediaUsage = true, onFinished = onFinished)
    }

    /**
     * Used strictly as emergency spoken Adhan fallback when AdhanVoice.SYSTEM_TONE is chosen
     * or an offline Adhan MP3 is missing; Adhkar playback never calls TTS.
     */
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

    private fun resolveReciterForPlay(sheikh: AdhkarSheikhVoice, fallbackReciterId: String): String {
        return when (sheikh) {
            AdhkarSheikhVoice.MULTI_SHEIKHS -> rotatingReciterIds[(rotationCursor++) % rotatingReciterIds.size]
            AdhkarSheikhVoice.HAMAD_AL_DURAYHIM -> fallbackReciterId
            else -> sheikh.quranReciterId
        }
    }

    private fun resolvePeriodicLocalUri(sheikh: AdhkarSheikhVoice, phraseIndex: Int): Uri? {
        if (sheikh == AdhkarSheikhVoice.HAMAD_AL_DURAYHIM) {
            return offlineAudioRepository.getOfflinePeriodicDhikrUri(phraseIndex)
        }
        if (sheikh == AdhkarSheikhVoice.MULTI_SHEIKHS) {
            // Mix real Quranic Duas/Tasbih across 6 real Sheikhs & Hamad Al-Durayhim Hisn recordings
            if (phraseIndex % 3 == 0) {
                offlineAudioRepository.getOfflinePeriodicDhikrUri(phraseIndex)?.let { return it }
            }
            val reciterId = rotatingReciterIds[(phraseIndex + (rotationCursor++)) % rotatingReciterIds.size]
            val ayah = periodicAyahPool[phraseIndex % periodicAyahPool.size]
            offlineAudioRepository.getOfflineAyahUri(reciterId, ayah.first, ayah.second)?.let { return it }
            offlineAudioRepository.getOfflinePeriodicDhikrUri(phraseIndex)?.let { return it }
            return null
        }
        // Specific Sheikh chosen: prefer THEIR own genuine Quranic Dhikr/Dua voice
        val ayah = periodicAyahPool[phraseIndex % periodicAyahPool.size]
        offlineAudioRepository.getOfflineAyahUri(sheikh.quranReciterId, ayah.first, ayah.second)?.let { return it }
        offlineAudioRepository.getOfflineAyahUri(sheikh.quranReciterId, 2, 255)?.let { return it }
        return null
    }

    private suspend fun fetchPeriodicRemoteUri(
        sheikh: AdhkarSheikhVoice,
        phraseIndex: Int,
        phraseText: String
    ): Uri? {
        if (sheikh == AdhkarSheikhVoice.HAMAD_AL_DURAYHIM) {
            return offlineAudioRepository.fetchAndCachePeriodicClip(phraseIndex, phraseText)
        }
        if (sheikh == AdhkarSheikhVoice.MULTI_SHEIKHS) {
            if (phraseIndex % 3 == 0) {
                offlineAudioRepository.fetchAndCachePeriodicClip(phraseIndex, phraseText)?.let { return it }
            }
            val reciterId = rotatingReciterIds[(phraseIndex + rotationCursor) % rotatingReciterIds.size]
            val ayah = periodicAyahPool[phraseIndex % periodicAyahPool.size]
            offlineAudioRepository.downloadAyahClip(reciterId, ayah.first, ayah.second)?.let { return it }
            return offlineAudioRepository.fetchAndCachePeriodicClip(phraseIndex, phraseText)
        }
        val ayah = periodicAyahPool[phraseIndex % periodicAyahPool.size]
        return offlineAudioRepository.downloadAyahClip(sheikh.quranReciterId, ayah.first, ayah.second)
            ?: offlineAudioRepository.downloadAyahClip(sheikh.quranReciterId, 2, 255)
            ?: offlineAudioRepository.fetchAndCachePeriodicClip(phraseIndex, phraseText)
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

    private fun resolveAnyBundledRealSheikhUri(preferredReciterId: String): Uri? {
        return offlineAudioRepository.getOfflineAyahUri(preferredReciterId, 2, 255)
            ?: offlineAudioRepository.getOfflineAyahUri("afs", 2, 255)
            ?: offlineAudioRepository.getOfflineAyahUri("yasser", 2, 255)
            ?: offlineAudioRepository.getOfflineAyahUri("husr", 2, 255)
            ?: offlineAudioRepository.getOfflineDhikrClipUri("m2")
            ?: offlineAudioRepository.getOfflinePeriodicDhikrUri(0)
    }

    private fun playEmergencyRealSheikhFallback(
        preferredReciterId: String,
        chimeBefore: Boolean,
        useMediaUsage: Boolean,
        onFinished: (() -> Unit)?
    ) {
        val realUri = resolveAnyBundledRealSheikhUri(preferredReciterId)
        if (realUri != null) {
            if (chimeBefore) playChime()
            if (playOfflineUri(realUri, useMediaUsage, onFinished)) {
                return
            }
        }
        _isSpeaking.value = false
        onFinished?.invoke()
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
        private val rotatingReciterIds = listOf("afs", "yasser", "husr", "qtm", "sgmd", "maher")

        // Short authentic Quranic Ayat of Tasbih, Praise & Dua available across all 6 real Sheikhs
        private val periodicAyahPool = listOf(
            1 to 2,    // الحمد لله رب العالمين
            2 to 201,  // ربنا آتنا في الدنيا حسنة
            2 to 255,  // آية الكرسي
            3 to 8,    // ربنا لا تزغ قلوبنا
            3 to 173,  // حسبنا الله ونعم الوكيل
            14 to 40,  // رب اجعلني مقيم الصلاة
            21 to 87,  // لا إله إلا أنت سبحانك إني كنت من الظالمين
            30 to 17,  // فسبحان الله حين تمسون وحين تصبحون
            37 to 180, // سبحان ربك رب العزة عما يصفون
            112 to 1   // قل هو الله أحد
        )

        fun buildSpokenAdhanText(prayerName: String): String {
            val fajrLine = if (prayerName.contains("الفجر")) "، الصلاة خير من النوم، الصلاة خير من النوم" else ""
            return "الله أكبر الله أكبر، الله أكبر الله أكبر، أشهد أن لا إله إلا الله، أشهد أن لا إله إلا الله، أشهد أن محمداً رسول الله، أشهد أن محمداً رسول الله، حيّ على الصلاة، حيّ على الفلاح$fajrLine، الله أكبر الله أكبر، لا إله إلا الله. حان الآن موعد صلاة $prayerName"
        }
    }
}
