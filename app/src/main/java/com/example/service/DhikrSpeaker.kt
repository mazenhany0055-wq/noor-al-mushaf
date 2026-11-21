package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import com.example.data.catalog.AdhkarCatalog
import com.example.data.repository.OfflineAudioRepository
import com.example.domain.model.AdhkarSheikhVoice
import com.example.domain.model.DhikrItem
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
import kotlin.coroutines.resume

/**
 * Plays 100% genuine human Sheikh audio recordings for Adhkar & Quranic Duas.
 * Zero TextToSpeech (TTS) and zero AI-synthesized audio anywhere.
 * When a real recording is unavailable for the selected Sheikh/dhikr, reports "غير متوفر" honestly.
 */
class DhikrSpeaker(
    context: Context,
    private val offlineAudioRepository: OfflineAudioRepository = OfflineAudioRepository(context)
) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var fetchJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var rotationCursor: Int = 0

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    fun playDhikrItem(
        item: DhikrItem,
        voiceId: String,
        quranReciterId: String,
        chimeBefore: Boolean,
        useMediaUsage: Boolean = true,
        onUnavailable: ((String) -> Unit)? = null,
        onFinished: (() -> Unit)? = null
    ) {
        stop()

        val sheikh = AdhkarSheikhVoice.fromId(voiceId)

        // 1. Quranic Ayat item inside Adhkar (e.g., Ayat al-Kursi, Al-Muawwidhat, Quranic Duas)
        if (item.surahNumber != null) {
            val targetReciterId = when (sheikh) {
                AdhkarSheikhVoice.MULTI_SHEIKHS ->
                    rotatingReciterIds[(rotationCursor++) % rotatingReciterIds.size]
                AdhkarSheikhVoice.HAMAD_AL_DURAYHIM -> quranReciterId
                else -> sheikh.quranReciterId
            }

            val localUri = resolveLocalQuranDhikrUri(item, targetReciterId)
            if (localUri != null) {
                if (chimeBefore) playChime()
                if (playOfflineUri(localUri, useMediaUsage, onUnavailable, onFinished)) {
                    return
                }
            }

            if (offlineAudioRepository.isNetworkConnected()) {
                _isSpeaking.value = true
                fetchJob = scope.launch {
                    val remoteUri = fetchRemoteQuranDhikrUri(item, targetReciterId)
                    if (remoteUri != null) {
                        if (chimeBefore) playChime()
                        if (playOfflineUri(remoteUri, useMediaUsage, onUnavailable, onFinished)) {
                            return@launch
                        }
                    }
                    reportUnavailable(
                        "غير متوفر: تعذّر تشغيل التسجيل الحقيقي بصوت ${sheikh.arabicName} حالياً",
                        onUnavailable,
                        onFinished
                    )
                }
                return
            }

            reportUnavailable(
                "غير متوفر: تسجيل ${sheikh.arabicName} غير محفوظ للاستماع بدون نت",
                onUnavailable,
                onFinished
            )
            return
        }

        // 2. Non-Quranic Hisn Al-Muslim prose Dhikr item
        val supportsHisnIndividual =
            sheikh == AdhkarSheikhVoice.HAMAD_AL_DURAYHIM || sheikh == AdhkarSheikhVoice.MULTI_SHEIKHS

        if (!supportsHisnIndividual) {
            reportUnavailable(
                "غير متوفر: لا يوجد تسجيل منفرد بصوت ${sheikh.arabicName} لهذا الذكر — اختر «الشيخ حمد الدريهم» أو «تنويع تلقائي»",
                onUnavailable,
                onFinished
            )
            return
        }

        val localHisn = offlineAudioRepository.getOfflineDhikrClipUri(item.id)
        if (localHisn != null) {
            if (chimeBefore) playChime()
            if (playOfflineUri(localHisn, useMediaUsage, onUnavailable, onFinished)) {
                return
            }
        }

        if (offlineAudioRepository.isNetworkConnected()) {
            _isSpeaking.value = true
            fetchJob = scope.launch {
                val remoteHisn = offlineAudioRepository.fetchAndCacheHisnDhikrClip(item.id, item.text)
                if (remoteHisn != null) {
                    if (chimeBefore) playChime()
                    if (playOfflineUri(remoteHisn, useMediaUsage, onUnavailable, onFinished)) {
                        return@launch
                    }
                }
                reportUnavailable(
                    "غير متوفر: لا يوجد تسجيل صوتي حقيقي لهذا الذكر حالياً",
                    onUnavailable,
                    onFinished
                )
            }
            return
        }

        reportUnavailable(
            "غير متوفر: التسجيل الحقيقي لهذا الذكر غير محمّل للاستماع بدون نت",
            onUnavailable,
            onFinished
        )
    }

    fun playPeriodicDhikr(
        phraseIndex: Int,
        phraseText: String,
        voiceId: String,
        chimeBefore: Boolean,
        useMediaUsage: Boolean = true,
        onUnavailable: ((String) -> Unit)? = null,
        onFinished: (() -> Unit)? = null
    ) {
        stop()
        val sheikh = AdhkarSheikhVoice.fromId(voiceId)
        val localUri = resolvePeriodicLocalUri(sheikh, phraseIndex)
        if (localUri != null) {
            if (chimeBefore) playChime()
            if (playOfflineUri(localUri, useMediaUsage, onUnavailable, onFinished)) {
                return
            }
        }

        if (offlineAudioRepository.isNetworkConnected()) {
            _isSpeaking.value = true
            fetchJob = scope.launch {
                val remote = fetchPeriodicRemoteUri(sheikh, phraseIndex, phraseText)
                if (remote != null) {
                    if (chimeBefore) playChime()
                    if (playOfflineUri(remote, useMediaUsage, onUnavailable, onFinished)) {
                        return@launch
                    }
                }
                reportUnavailable(
                    "غير متوفر: لا يوجد تسجيل حقيقي بصوت ${sheikh.arabicName} حالياً",
                    onUnavailable,
                    onFinished
                )
            }
            return
        }

        reportUnavailable(
            "غير متوفر: لا يوجد تسجيل حقيقي محفوظ بصوت ${sheikh.arabicName} بدون نت",
            onUnavailable,
            onFinished
        )
    }

    suspend fun playPeriodicDhikrAndAwait(
        phraseIndex: Int,
        phraseText: String,
        voiceId: String,
        chimeBefore: Boolean,
        timeoutMs: Long = 22_000L
    ) {
        val sheikh = AdhkarSheikhVoice.fromId(voiceId)
        val uri = resolvePeriodicLocalUri(sheikh, phraseIndex)
            ?: if (offlineAudioRepository.isNetworkConnected()) {
                fetchPeriodicRemoteUri(sheikh, phraseIndex, phraseText)
            } else {
                null
            }

        if (uri != null) {
            if (chimeBefore) playChime()
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    val started = playOfflineUri(
                        uri = uri,
                        useMediaUsage = false,
                        onUnavailable = {
                            if (cont.isActive) cont.resume(false)
                        },
                        onFinished = {
                            if (cont.isActive) cont.resume(true)
                        }
                    )
                    if (!started && cont.isActive) {
                        cont.resume(false)
                    }
                    cont.invokeOnCancellation { stop() }
                }
            }
        }
    }

    fun playSheikhSession(
        sheikh: AdhkarSheikhVoice,
        isMorning: Boolean,
        onUnavailable: ((String) -> Unit)? = null,
        onFinished: (() -> Unit)? = null
    ): Boolean {
        stop()
        val offlineUri = offlineAudioRepository.getOfflineAdhkarSessionUri(sheikh, isMorning)
        if (offlineUri != null) {
            return playOfflineUri(offlineUri, useMediaUsage = true, onUnavailable = onUnavailable, onFinished = onFinished)
        }
        val streamUrl = if (isMorning) sheikh.morningSessionUrl else sheikh.eveningSessionUrl
        if (streamUrl == null) {
            onUnavailable?.invoke("غير متوفر: لا توجد جلسة كاملة مسجلة بصوت ${sheikh.arabicName}")
            return false
        }
        if (offlineAudioRepository.isNetworkConnected()) {
            return playOfflineUri(Uri.parse(streamUrl), useMediaUsage = true, onUnavailable = onUnavailable, onFinished = onFinished)
        }
        onUnavailable?.invoke("غير متوفر بدون نت: اتصل بالإنترنت أولاً أو حمّل الجلسة بصوت ${sheikh.arabicName}")
        return false
    }

    fun previewSheikhVoice(
        sheikh: AdhkarSheikhVoice,
        chimeBefore: Boolean = false,
        onUnavailable: ((String) -> Unit)? = null,
        onFinished: (() -> Unit)? = null
    ) {
        stop()

        if (sheikh == AdhkarSheikhVoice.HAMAD_AL_DURAYHIM) {
            val hisnUri = offlineAudioRepository.getOfflineDhikrClipUri("m2")
                ?: offlineAudioRepository.getOfflinePeriodicDhikrUri(0)
            if (hisnUri != null) {
                if (chimeBefore) playChime()
                if (playOfflineUri(hisnUri, useMediaUsage = true, onUnavailable, onFinished)) return
            }
            if (offlineAudioRepository.isNetworkConnected()) {
                _isSpeaking.value = true
                fetchJob = scope.launch {
                    val sampleText = AdhkarCatalog.allDhikr.firstOrNull { it.id == "m2" }?.text ?: ""
                    val downloaded = offlineAudioRepository.fetchAndCacheHisnDhikrClip("m2", sampleText)
                        ?: offlineAudioRepository.fetchAndCachePeriodicClip(0, AdhkarCatalog.periodicDhikrPhrases.first())
                    if (downloaded != null) {
                        if (chimeBefore) playChime()
                        if (playOfflineUri(downloaded, useMediaUsage = true, onUnavailable, onFinished)) return@launch
                    }
                    reportUnavailable(
                        "غير متوفر: تعذّر تحميل عينة صوت ${sheikh.arabicName}",
                        onUnavailable,
                        onFinished
                    )
                }
                return
            }
            reportUnavailable(
                "غير متوفر بدون نت: عينة صوت ${sheikh.arabicName} غير محمّلة حالياً",
                onUnavailable,
                onFinished
            )
            return
        }

        val targetReciterId = if (sheikh == AdhkarSheikhVoice.MULTI_SHEIKHS) {
            rotatingReciterIds[(rotationCursor++) % rotatingReciterIds.size]
        } else {
            sheikh.quranReciterId
        }
        val previewAyah = periodicAyahPool[(rotationCursor) % periodicAyahPool.size]

        val localPreview = offlineAudioRepository.getOfflineAyahUri(targetReciterId, previewAyah.first, previewAyah.second)
            ?: offlineAudioRepository.getOfflineAyahUri(targetReciterId, 2, 255)

        if (localPreview != null) {
            if (chimeBefore) playChime()
            if (playOfflineUri(localPreview, useMediaUsage = true, onUnavailable, onFinished)) return
        }

        if (offlineAudioRepository.isNetworkConnected()) {
            _isSpeaking.value = true
            fetchJob = scope.launch {
                val downloaded = offlineAudioRepository.downloadAyahClip(targetReciterId, previewAyah.first, previewAyah.second)
                    ?: offlineAudioRepository.downloadAyahClip(targetReciterId, 2, 255)
                if (downloaded != null) {
                    if (chimeBefore) playChime()
                    if (playOfflineUri(downloaded, useMediaUsage = true, onUnavailable, onFinished)) return@launch
                }
                reportUnavailable(
                    "غير متوفر: تعذّر تحميل عينة صوت ${sheikh.arabicName}",
                    onUnavailable,
                    onFinished
                )
            }
            return
        }

        reportUnavailable(
            "غير متوفر بدون نت: عينة صوت ${sheikh.arabicName} غير محمّلة",
            onUnavailable,
            onFinished
        )
    }

    fun stop() {
        fetchJob?.cancel()
        fetchJob = null
        releaseMediaPlayer()
        _isSpeaking.value = false
    }

    fun release() {
        stop()
    }

    private fun resolvePeriodicLocalUri(sheikh: AdhkarSheikhVoice, phraseIndex: Int): Uri? {
        if (sheikh == AdhkarSheikhVoice.HAMAD_AL_DURAYHIM) {
            return offlineAudioRepository.getOfflinePeriodicDhikrUri(phraseIndex)
        }
        if (sheikh == AdhkarSheikhVoice.MULTI_SHEIKHS) {
            if (phraseIndex % 3 == 0) {
                offlineAudioRepository.getOfflinePeriodicDhikrUri(phraseIndex)?.let { return it }
            }
            val reciterId = rotatingReciterIds[(phraseIndex + (rotationCursor++)) % rotatingReciterIds.size]
            val ayah = periodicAyahPool[phraseIndex % periodicAyahPool.size]
            offlineAudioRepository.getOfflineAyahUri(reciterId, ayah.first, ayah.second)?.let { return it }
            offlineAudioRepository.getOfflinePeriodicDhikrUri(phraseIndex)?.let { return it }
            return null
        }
        val ayah = periodicAyahPool[phraseIndex % periodicAyahPool.size]
        return offlineAudioRepository.getOfflineAyahUri(sheikh.quranReciterId, ayah.first, ayah.second)
            ?: offlineAudioRepository.getOfflineAyahUri(sheikh.quranReciterId, 2, 255)
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
    }

    private fun resolveLocalQuranDhikrUri(item: DhikrItem, reciterId: String): Uri? {
        val surah = item.surahNumber ?: return null
        if (item.id == "m_muawwidhat") {
            return offlineAudioRepository.getOfflineSurahUri(reciterId, 112)
                ?: offlineAudioRepository.getOfflineAyahUri(reciterId, 112, 1)
        }
        if (item.ayahNumbers.isNotEmpty()) {
            val ayah = item.ayahNumbers.first()
            return offlineAudioRepository.getOfflineAyahUri(reciterId, surah, ayah)
        }
        return offlineAudioRepository.getOfflineSurahUri(reciterId, surah)
    }

    private suspend fun fetchRemoteQuranDhikrUri(item: DhikrItem, reciterId: String): Uri? {
        val surah = item.surahNumber ?: return null
        if (item.id == "m_muawwidhat") {
            return offlineAudioRepository.downloadAyahClip(reciterId, 112, 1)
        }
        if (item.ayahNumbers.isNotEmpty()) {
            return offlineAudioRepository.downloadAyahClip(reciterId, surah, item.ayahNumbers.first())
        }
        return offlineAudioRepository.downloadAyahClip(reciterId, surah, 1)
    }

    private fun reportUnavailable(
        message: String,
        onUnavailable: ((String) -> Unit)?,
        onFinished: (() -> Unit)?
    ) {
        _isSpeaking.value = false
        onUnavailable?.invoke(message)
        onFinished?.invoke()
    }

    private fun playOfflineUri(
        uri: Uri,
        useMediaUsage: Boolean,
        onUnavailable: ((String) -> Unit)?,
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
                    onUnavailable?.invoke("غير متوفر: تعذّر تشغيل الملف الصوتي الحقيقي")
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
    }
}
