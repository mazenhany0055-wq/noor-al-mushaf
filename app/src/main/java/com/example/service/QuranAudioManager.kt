package com.example.service

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.data.catalog.QuranCatalog
import com.example.data.remote.AlQuranApiService
import com.example.data.repository.OfflineAudioRepository
import com.example.domain.model.AdhanVoice
import com.example.domain.model.ReciterInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AudioPlaybackState(
    val hasActiveItem: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentSurahNumber: Int = 1,
    val currentSurahName: String = "الفاتحة",
    val currentReciterId: String = "yasser",
    val currentReciterName: String = "ياسر الدوسري",
    val currentAyahNumber: Int? = null,
    val totalAyahsInSurah: Int = 7,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatSurah: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val sleepTimerRemainingSec: Int? = null,
    val errorMessage: String? = null
)

@OptIn(UnstableApi::class)
class QuranAudioManager(
    context: Context,
    private val apiService: AlQuranApiService = AlQuranApiService.create(),
    private val offlineAudioRepository: OfflineAudioRepository = OfflineAudioRepository(context)
) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(8_000)
        .setReadTimeoutMs(10_000)

    private val dataSourceFactory = DefaultDataSource.Factory(appContext, httpFactory)

    private var _quranPlayer: ExoPlayer? = null
    private var _adhanPlayer: ExoPlayer? = null
    private var fallbackMediaPlayer: android.media.MediaPlayer? = null

    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var resolveJob: Job? = null

    private var fallbackQueue: ArrayDeque<List<MediaItem>> = ArrayDeque()
    private var adhanFallbackUrl: String? = null

    private val _state = MutableStateFlow(AudioPlaybackState())
    val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()

    private val _isAdhanPreviewPlaying = MutableStateFlow(false)
    val isAdhanPreviewPlaying: StateFlow<Boolean> = _isAdhanPreviewPlaying.asStateFlow()

    private fun getOrCreateQuranPlayer(): ExoPlayer {
        _quranPlayer?.let { return it }
        val player = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        attachQuranPlayerListener(player)
        _quranPlayer = player
        return player
    }

    private fun getOrCreateAdhanPlayer(): ExoPlayer {
        _adhanPlayer?.let { return it }
        val player = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_ALARM)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false
            )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        attachAdhanPlayerListener(player)
        _adhanPlayer = player
        return player
    }

    private fun attachQuranPlayerListener(quranPlayer: ExoPlayer) {
        quranPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update {
                    it.copy(
                        isPlaying = isPlaying,
                        errorMessage = if (isPlaying) null else it.errorMessage
                    )
                }
                if (isPlaying) startProgressPolling() else stopProgressPolling()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val buffering = playbackState == Player.STATE_BUFFERING
                _state.update {
                    it.copy(
                        isBuffering = buffering,
                        durationMs = quranPlayer.duration.coerceAtLeast(0L)
                    )
                }
                if (playbackState == Player.STATE_ENDED) {
                    if (_state.value.repeatSurah) {
                        quranPlayer.seekTo(0, 0L)
                        quranPlayer.play()
                    } else {
                        playNextSurah()
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val ayahTag = mediaItem?.mediaMetadata?.trackNumber
                _state.update {
                    it.copy(
                        currentAyahNumber = ayahTag,
                        durationMs = quranPlayer.duration.coerceAtLeast(0L)
                    )
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (fallbackQueue.isNotEmpty()) {
                    val nextSourceItems = fallbackQueue.removeFirst()
                    quranPlayer.setMediaItems(nextSourceItems, true)
                    quranPlayer.prepare()
                    quranPlayer.setPlaybackSpeed(_state.value.playbackSpeed)
                    quranPlayer.play()
                } else {
                    _state.update {
                        it.copy(
                            isBuffering = false,
                            isPlaying = false,
                            errorMessage = "تعذّر تشغيل التلاوة. تأكد من اتصال الإنترنت وحاول مرة أخرى."
                        )
                    }
                }
            }
        })
    }

    private fun attachAdhanPlayerListener(adhanPlayer: ExoPlayer) {
        adhanPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isAdhanPreviewPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    _isAdhanPreviewPlaying.value = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val nextUrl = adhanFallbackUrl
                adhanFallbackUrl = null
                if (nextUrl != null) {
                    adhanPlayer.setMediaItem(MediaItem.fromUri(nextUrl))
                    adhanPlayer.prepare()
                    adhanPlayer.play()
                } else {
                    playLocalAdhanRingtone()
                }
            }
        })
    }

    fun playSurah(surahNumber: Int, reciterId: String) {
        val surah = QuranCatalog.surahs.find { it.number == surahNumber } ?: QuranCatalog.surahs.first()
        val reciter = QuranCatalog.reciters.find { it.id == reciterId } ?: QuranCatalog.reciters.first()

        stopAdhanPreview()
        resolveJob?.cancel()
        fallbackQueue.clear()

        _state.update {
            it.copy(
                hasActiveItem = true,
                isBuffering = true,
                isPlaying = false,
                currentSurahNumber = surah.number,
                currentSurahName = surah.arabicName,
                currentReciterId = reciter.id,
                currentReciterName = reciter.arabicName,
                currentAyahNumber = null,
                totalAyahsInSurah = surah.ayahCount,
                positionMs = 0L,
                durationMs = 0L,
                errorMessage = null
            )
        }

        // 1. Check offline downloaded/bundled audio FIRST for true 100% offline playback!
        val offlineUri = offlineAudioRepository.getOfflineSurahUri(reciter.id, surah.number)
        if (offlineUri != null) {
            val offlineItem = buildSurahMediaItemFromUri(offlineUri, surah.arabicName, reciter.arabicName)
            val player = try {
                getOrCreateQuranPlayer()
            } catch (_: Exception) {
                playViaMediaPlayerFallbackUri(offlineUri)
                return
            }
            stopFallbackMediaPlayer()
            player.setMediaItems(listOf(offlineItem), true)
            player.prepare()
            player.setPlaybackSpeed(_state.value.playbackSpeed)
            player.play()
            return
        }

        // 2. If offline and not downloaded, fail gracefully with clear guidance (or fall back to another offline reciter if available)
        if (!offlineAudioRepository.isNetworkConnected()) {
            _state.update {
                it.copy(
                    isBuffering = false,
                    isPlaying = false,
                    errorMessage = "لا يوجد اتصال بالإنترنت، وهذه السورة غير محمّلة بعد. يمكنك الاستماع للسور المحمّلة بدون نت أو تحميلها عند توفر الاتصال."
                )
            }
            return
        }

        // 3. Online streaming with deterministic fallbacks
        val directSurahUrls = reciter.surahAudioUrls(surah.number)
        val primaryItems = directSurahUrls.firstOrNull()?.let { url ->
            listOf(buildSurahMediaItem(url, surah.arabicName, reciter.arabicName))
        }
        directSurahUrls.drop(1).forEach { mirrorUrl ->
            fallbackQueue.addLast(listOf(buildSurahMediaItem(mirrorUrl, surah.arabicName, reciter.arabicName)))
        }
        val deterministicEveryAyah = (1..surah.ayahCount).mapNotNull { ayahIdx ->
            reciter.everyAyahUrl(surah.number, ayahIdx)?.let { url ->
                val metadata = MediaMetadata.Builder()
                    .setTitle("سورة ${surah.arabicName} • آية $ayahIdx")
                    .setArtist(reciter.arabicName)
                    .setTrackNumber(ayahIdx)
                    .build()
                MediaItem.Builder()
                    .setUri(Uri.parse(url))
                    .setMediaMetadata(metadata)
                    .build()
            }
        }
        if (deterministicEveryAyah.isNotEmpty()) {
            fallbackQueue.addLast(deterministicEveryAyah)
        }
        if (reciter.alQuranCloudEdition != "ar.alafasy") {
            fallbackQueue.addLast(
                listOf(
                    buildSurahMediaItem(
                        "https://cdn.islamic.network/quran/audio-surah/128/ar.alafasy/${surah.number}.mp3",
                        surah.arabicName,
                        reciter.arabicName
                    )
                )
            )
        }

        val player = try {
            getOrCreateQuranPlayer()
        } catch (_: Exception) {
            directSurahUrls.firstOrNull()?.let { playViaMediaPlayerFallback(it) }
            return
        }

        stopFallbackMediaPlayer()
        if (primaryItems != null) {
            player.setMediaItems(primaryItems, true)
            player.prepare()
            player.setPlaybackSpeed(_state.value.playbackSpeed)
            player.play()
        }

        resolveJob = scope.launch {
            val ayahPlaylist = fetchAlQuranCloudAyahPlaylist(surah.number, surah.arabicName, reciter)
            if (ayahPlaylist.isNotEmpty()) {
                if (!player.isPlaying && player.playbackState == Player.STATE_IDLE) {
                    _state.update { it.copy(errorMessage = null, isBuffering = true) }
                    player.setMediaItems(ayahPlaylist, true)
                    player.prepare()
                    player.setPlaybackSpeed(_state.value.playbackSpeed)
                    player.play()
                } else {
                    fallbackQueue.addLast(ayahPlaylist)
                }
            }
        }
    }

    private suspend fun fetchAlQuranCloudAyahPlaylist(
        surahNumber: Int,
        surahName: String,
        reciter: ReciterInfo
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val edition = reciter.alQuranCloudEdition ?: "quran-uthmani"
            val response = apiService.getSurahAudio(surahNumber, edition)
            response.data.ayahs.mapNotNull { ayah ->
                val url = if (!ayah.audio.isNullOrBlank()) {
                    ayah.audio
                } else {
                    reciter.everyAyahUrl(surahNumber, ayah.numberInSurah)
                }
                url?.let {
                    val metadata = MediaMetadata.Builder()
                        .setTitle("سورة $surahName • آية ${ayah.numberInSurah}")
                        .setArtist(reciter.arabicName)
                        .setTrackNumber(ayah.numberInSurah)
                        .build()
                    MediaItem.Builder()
                        .setUri(Uri.parse(it))
                        .setMediaMetadata(metadata)
                        .build()
                }
            }
        } catch (_: Exception) {
            // Fallback to deterministic EveryAyah URLs using known ayahCount if API is unreachable
            val ayahCount = QuranCatalog.surahs.find { it.number == surahNumber }?.ayahCount ?: 7
            (1..ayahCount).mapNotNull { ayahIdx ->
                reciter.everyAyahUrl(surahNumber, ayahIdx)?.let { url ->
                    val metadata = MediaMetadata.Builder()
                        .setTitle("سورة $surahName • آية $ayahIdx")
                        .setArtist(reciter.arabicName)
                        .setTrackNumber(ayahIdx)
                        .build()
                    MediaItem.Builder()
                        .setUri(Uri.parse(url))
                        .setMediaMetadata(metadata)
                        .build()
                }
            }
        }
    }

    private fun buildSurahMediaItem(url: String, surahName: String, reciterName: String): MediaItem {
        return buildSurahMediaItemFromUri(Uri.parse(url), surahName, reciterName)
    }

    private fun buildSurahMediaItemFromUri(uri: Uri, surahName: String, reciterName: String): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle("سورة $surahName")
            .setArtist(reciterName)
            .build()
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun togglePlayPause() {
        val mp = fallbackMediaPlayer
        if (mp != null) {
            try {
                if (mp.isPlaying) {
                    mp.pause()
                    _state.update { it.copy(isPlaying = false) }
                } else {
                    mp.start()
                    _state.update { it.copy(isPlaying = true) }
                }
                return
            } catch (_: Exception) {
                stopFallbackMediaPlayer()
            }
        }

        val player = _quranPlayer
        if (player != null && player.isPlaying) {
            player.pause()
        } else if (player != null && _state.value.hasActiveItem && player.playbackState != Player.STATE_IDLE) {
            player.play()
        } else {
            playSurah(_state.value.currentSurahNumber, _state.value.currentReciterId)
        }
    }

    fun stopPlayback() {
        resolveJob?.cancel()
        fallbackQueue.clear()
        stopFallbackMediaPlayer()
        _quranPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()
        }
        stopProgressPolling()
        _state.update {
            it.copy(
                hasActiveItem = false,
                isPlaying = false,
                isBuffering = false,
                positionMs = 0L,
                durationMs = 0L,
                currentAyahNumber = null,
                errorMessage = null
            )
        }
    }

    fun playNextSurah() {
        val next = if (_state.value.currentSurahNumber >= 114) 1 else _state.value.currentSurahNumber + 1
        playSurah(next, _state.value.currentReciterId)
    }

    fun playPreviousSurah() {
        val prev = if (_state.value.currentSurahNumber <= 1) 114 else _state.value.currentSurahNumber - 1
        playSurah(prev, _state.value.currentReciterId)
    }

    fun seekTo(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        fallbackMediaPlayer?.let { mp ->
            try {
                mp.seekTo(safePos.toInt())
            } catch (_: Exception) {
            }
        }
        _quranPlayer?.seekTo(safePos)
        _state.update { it.copy(positionMs = safePos) }
    }

    fun setPlaybackSpeed(speed: Float) {
        _quranPlayer?.setPlaybackSpeed(speed)
        _state.update { it.copy(playbackSpeed = speed) }
    }

    fun toggleRepeatSurah() {
        _state.update { it.copy(repeatSurah = !it.repeatSurah) }
    }

    fun setSleepTimerMinutes(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _state.update { it.copy(sleepTimerRemainingSec = null) }
            return
        }
        sleepTimerJob = scope.launch {
            var remaining = minutes * 60
            while (remaining > 0) {
                _state.update { it.copy(sleepTimerRemainingSec = remaining) }
                delay(1000L)
                remaining--
            }
            _quranPlayer?.pause()
            fallbackMediaPlayer?.let { if (it.isPlaying) it.pause() }
            _state.update { it.copy(sleepTimerRemainingSec = null, isPlaying = false) }
        }
    }

    fun playAdhanPreview(voice: AdhanVoice) {
        if (_quranPlayer?.isPlaying == true) {
            _quranPlayer?.pause()
        }
        // Check offline bundled/downloaded Adhan FIRST!
        val offlineUri = offlineAudioRepository.getOfflineAdhanUri(voice)
        if (offlineUri != null) {
            adhanFallbackUrl = null
            try {
                val player = getOrCreateAdhanPlayer()
                _isAdhanPreviewPlaying.value = true
                player.setMediaItem(MediaItem.fromUri(offlineUri))
                player.prepare()
                player.play()
                return
            } catch (_: Exception) {
            }
        }

        val url = voice.streamUrl
        adhanFallbackUrl = voice.fallbackUrl
        if (url != null && offlineAudioRepository.isNetworkConnected()) {
            try {
                val player = getOrCreateAdhanPlayer()
                _isAdhanPreviewPlaying.value = true
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.play()
            } catch (_: Exception) {
                playLocalAdhanRingtone()
            }
        } else {
            playLocalAdhanRingtone()
        }
    }

    private fun playLocalAdhanRingtone() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (uri != null) {
                val player = getOrCreateAdhanPlayer()
                player.setMediaItem(MediaItem.fromUri(uri))
                player.prepare()
                player.play()
                _isAdhanPreviewPlaying.value = true
            }
        } catch (_: Exception) {
            _isAdhanPreviewPlaying.value = false
        }
    }

    fun stopAdhanPreview() {
        adhanFallbackUrl = null
        _adhanPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()
        }
        _isAdhanPreviewPlaying.value = false
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun playViaMediaPlayerFallback(url: String) {
        playViaMediaPlayerFallbackUri(Uri.parse(url))
    }

    private fun playViaMediaPlayerFallbackUri(uri: Uri) {
        stopFallbackMediaPlayer()
        try {
            val mp = android.media.MediaPlayer().apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(appContext, uri)
                setOnPreparedListener {
                    it.start()
                    _state.update { st ->
                        st.copy(
                            isBuffering = false,
                            isPlaying = true,
                            durationMs = it.duration.coerceAtLeast(0).toLong(),
                            errorMessage = null
                        )
                    }
                    startProgressPolling()
                }
                setOnCompletionListener {
                    if (_state.value.repeatSurah) {
                        it.seekTo(0)
                        it.start()
                    } else {
                        playNextSurah()
                    }
                }
                setOnErrorListener { _, _, _ ->
                    _state.update { st ->
                        st.copy(
                            isBuffering = false,
                            isPlaying = false,
                            errorMessage = "تعذّر تشغيل التلاوة. تأكد من اتصال الإنترنت وحاول مرة أخرى."
                        )
                    }
                    true
                }
                prepareAsync()
            }
            fallbackMediaPlayer = mp
        } catch (_: Exception) {
            _state.update {
                it.copy(
                    isBuffering = false,
                    isPlaying = false,
                    errorMessage = "تعذّر تشغيل التلاوة على هذا الجهاز."
                )
            }
        }
    }

    private fun stopFallbackMediaPlayer() {
        try {
            fallbackMediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {
        } finally {
            fallbackMediaPlayer = null
        }
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                val player = _quranPlayer
                val mp = fallbackMediaPlayer
                when {
                    player != null -> {
                        _state.update {
                            it.copy(
                                positionMs = player.currentPosition.coerceAtLeast(0L),
                                durationMs = player.duration.coerceAtLeast(0L)
                            )
                        }
                    }
                    mp != null -> {
                        try {
                            _state.update {
                                it.copy(
                                    positionMs = mp.currentPosition.coerceAtLeast(0).toLong(),
                                    durationMs = mp.duration.coerceAtLeast(0).toLong()
                                )
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
                delay(500L)
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
    }

    fun release() {
        resolveJob?.cancel()
        progressJob?.cancel()
        sleepTimerJob?.cancel()
        stopFallbackMediaPlayer()
        _quranPlayer?.release()
        _quranPlayer = null
        _adhanPlayer?.release()
        _adhanPlayer = null
    }
}
