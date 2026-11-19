package com.example.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.model.AdhanVoice

class AdhanPlaybackService : Service() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAdhanAndSelf()
            return START_NOT_STICKY
        }

        val prayerName = intent?.getStringExtra(EXTRA_PRAYER_NAME) ?: "الصلاة"
        val voiceId = intent?.getStringExtra(EXTRA_VOICE_ID) ?: AdhanVoice.MAKKAH.id
        val voice = AdhanVoice.fromId(voiceId)

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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف الأذان", stopIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        playAdhanAudio(voice.streamUrl, voice.fallbackUrl)
        return START_NOT_STICKY
    }

    private fun playAdhanAudio(primaryUrl: String?, fallbackUrl: String?) {
        releasePlayer()
        if (primaryUrl != null) {
            try {
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setDataSource(primaryUrl)
                    setOnPreparedListener { it.start() }
                    setOnCompletionListener { stopAdhanAndSelf() }
                    setOnErrorListener { _, _, _ ->
                        if (fallbackUrl != null) {
                            playAdhanAudio(fallbackUrl, null)
                        } else {
                            playFallbackRingtone()
                        }
                        true
                    }
                    prepareAsync()
                }
                mediaPlayer = player
            } catch (_: Exception) {
                if (fallbackUrl != null) {
                    playAdhanAudio(fallbackUrl, null)
                } else {
                    playFallbackRingtone()
                }
            }
        } else {
            playFallbackRingtone()
        }
    }

    private fun playFallbackRingtone() {
        releasePlayer()
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

    private fun releasePlayer() {
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

    private fun stopAdhanAndSelf() {
        releasePlayer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.action.STOP_ADHAN"
        const val EXTRA_PRAYER_NAME = "extra_prayer_name"
        const val EXTRA_VOICE_ID = "extra_voice_id"
        private const val NOTIFICATION_ID = 4001
    }
}
