package com.example.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.data.repository.OfflineAudioRepository
import com.example.domain.model.AdhanVoice

class AdhanPlaybackService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var dhikrSpeaker: DhikrSpeaker? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val safetyStopRunnable = Runnable { stopAdhanAndSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAdhanAndSelf()
            return START_NOT_STICKY
        }

        NotificationChannels.registerAll(this)

        val prayerName = intent?.getStringExtra(EXTRA_PRAYER_NAME) ?: "الصلاة"
        val voiceId = intent?.getStringExtra(EXTRA_VOICE_ID) ?: AdhanVoice.MAKKAH.id
        val voice = AdhanVoice.fromId(voiceId)
        val adhkarVoiceId = intent?.getStringExtra(EXTRA_TTS_VOICE_ID) ?: "calm_deep"

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AdhanPlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NotificationChannels.ADHAN_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("حان الآن موعد أذان $prayerName")
            .setContentText("حيّ على الصلاة، حيّ على الفلاح • ${voice.arabicName}")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف الأذان", stopIntent)
            .setOngoing(true)
            .build()

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)

        acquireWakeLock()
        requestAlarmAudioFocus()
        scheduleSafetyTimeout(MAX_SERVICE_DURATION_MS)
        playAdhanVoiceOfflineFirst(voice, prayerName, adhkarVoiceId)
        return START_NOT_STICKY
    }

    private fun playAdhanVoiceOfflineFirst(
        voice: AdhanVoice,
        prayerName: String,
        ttsVoiceId: String
    ) {
        if (voice == AdhanVoice.SYSTEM_TONE) {
            playSpokenAdhanOrRingtone(prayerName, ttsVoiceId)
            return
        }

        val offlineRepo = OfflineAudioRepository(this)
        val offlineUri = offlineRepo.getOfflineAdhanUri(voice)
        if (offlineUri != null) {
            releaseAudioResources()
            try {
                val player = MediaPlayer().apply {
                    setAudioAttributes(alarmAudioAttributes())
                    setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                    if (offlineUri.scheme == "asset") {
                        val assetPath = offlineUri.path?.removePrefix("/") ?: ""
                        assets.openFd(assetPath).use { afd ->
                            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        }
                    } else {
                        setDataSource(this@AdhanPlaybackService, offlineUri)
                    }
                    setOnPreparedListener { it.start() }
                    setOnCompletionListener { stopAdhanAndSelf() }
                    setOnErrorListener { _, _, _ ->
                        playSpokenAdhanOrRingtone(prayerName, ttsVoiceId)
                        true
                    }
                    prepareAsync()
                }
                mediaPlayer = player
                return
            } catch (_: Exception) {
            }
        }

        if (offlineRepo.isNetworkConnected()) {
            playAdhanAudio(voice.streamUrl, voice.fallbackUrl, prayerName, ttsVoiceId)
        } else {
            playSpokenAdhanOrRingtone(prayerName, ttsVoiceId)
        }
    }

    private fun playAdhanAudio(
        primaryUrl: String?,
        fallbackUrl: String?,
        prayerName: String,
        ttsVoiceId: String
    ) {
        releaseAudioResources()
        if (primaryUrl != null) {
            try {
                val player = MediaPlayer().apply {
                    setAudioAttributes(alarmAudioAttributes())
                    setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                    setDataSource(primaryUrl)
                    setOnPreparedListener { it.start() }
                    setOnCompletionListener { stopAdhanAndSelf() }
                    setOnErrorListener { _, _, _ ->
                        if (fallbackUrl != null) {
                            playAdhanAudio(fallbackUrl, null, prayerName, ttsVoiceId)
                        } else {
                            playSpokenAdhanOrRingtone(prayerName, ttsVoiceId)
                        }
                        true
                    }
                    prepareAsync()
                }
                mediaPlayer = player
            } catch (_: Exception) {
                if (fallbackUrl != null) {
                    playAdhanAudio(fallbackUrl, null, prayerName, ttsVoiceId)
                } else {
                    playSpokenAdhanOrRingtone(prayerName, ttsVoiceId)
                }
            }
        } else {
            playSpokenAdhanOrRingtone(prayerName, ttsVoiceId)
        }
    }

    private fun playSpokenAdhanOrRingtone(prayerName: String, ttsVoiceId: String) {
        releaseAudioResources()
        try {
            val speaker = dhikrSpeaker ?: DhikrSpeaker(applicationContext).also { dhikrSpeaker = it }
            val adhanPhrase = DhikrSpeaker.buildSpokenAdhanText(prayerName)
            speaker.speakDhikr(
                phrase = adhanPhrase,
                voiceId = ttsVoiceId,
                chimeBefore = true,
                audioUsage = AudioAttributes.USAGE_ALARM
            ) {
                mainHandler.post { stopAdhanAndSelf() }
            }
        } catch (_: Exception) {
            playFallbackRingtone()
        }
    }

    private fun playFallbackRingtone() {
        releaseAudioResources()
        scheduleSafetyTimeout(RINGTONE_FALLBACK_MAX_MS)
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(this@AdhanPlaybackService, uri)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { stopAdhanAndSelf() }
                setOnErrorListener { _, _, _ ->
                    stopAdhanAndSelf()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (_: Exception) {
            stopAdhanAndSelf()
        }
    }

    private fun alarmAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NoorAlMushaf:AdhanServiceLock")
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(MAX_SERVICE_DURATION_MS)
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }
    }

    private fun requestAlarmAudioFocus() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(alarmAudioAttributes())
                    .setAcceptsDelayedFocusGain(false)
                    .build()
                audioFocusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
        } catch (_: Exception) {
        }
    }

    private fun abandonAudioFocus() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (_: Exception) {
        }
    }

    private fun scheduleSafetyTimeout(timeoutMs: Long) {
        mainHandler.removeCallbacks(safetyStopRunnable)
        mainHandler.postDelayed(safetyStopRunnable, timeoutMs)
    }

    private fun releaseAudioResources() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {
        } finally {
            mediaPlayer = null
        }
        try {
            dhikrSpeaker?.stop()
        } catch (_: Exception) {
        }
    }

    private fun stopAdhanAndSelf() {
        mainHandler.removeCallbacks(safetyStopRunnable)
        releaseAudioResources()
        abandonAudioFocus()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(safetyStopRunnable)
        releaseAudioResources()
        try {
            dhikrSpeaker?.release()
        } catch (_: Exception) {
        } finally {
            dhikrSpeaker = null
        }
        abandonAudioFocus()
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.action.STOP_ADHAN"
        const val EXTRA_PRAYER_NAME = "extra_prayer_name"
        const val EXTRA_VOICE_ID = "extra_voice_id"
        const val EXTRA_TTS_VOICE_ID = "extra_tts_voice_id"
        private const val NOTIFICATION_ID = 4001
        private const val MAX_SERVICE_DURATION_MS = 5 * 60 * 1000L
        private const val RINGTONE_FALLBACK_MAX_MS = 60 * 1000L
    }
}
