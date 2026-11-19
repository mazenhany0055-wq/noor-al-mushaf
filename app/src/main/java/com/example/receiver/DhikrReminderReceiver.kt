package com.example.receiver

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.NoorAlMushafApp
import com.example.R
import com.example.data.catalog.AdhkarCatalog
import com.example.domain.model.PeriodicDhikrMode
import com.example.service.AlarmScheduler
import com.example.service.DhikrSpeaker
import com.example.service.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DhikrReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = try {
            powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NoorAlMushaf:DhikrWake")?.apply {
                acquire(24_000L)
            }
        } catch (_: Exception) {
            null
        }

        CoroutineScope(Dispatchers.IO).launch {
            var speaker: DhikrSpeaker? = null
            try {
                val app = context.applicationContext as NoorAlMushafApp
                val settings = app.preferencesRepository.getCurrentSettings()
                if (!settings.isPeriodicDhikrEnabled) return@launch

                val phrases = AdhkarCatalog.periodicDhikrPhrases
                val currentIndex = settings.periodicDhikrPhraseIndex % phrases.size
                val phrase = phrases[currentIndex]
                val nextIndex = (currentIndex + 1) % phrases.size

                if (settings.periodicDhikrMode.includesNotification) {
                    postDhikrNotification(context, phrase, settings.periodicDhikrMode)
                }

                val nextTrigger = AlarmScheduler.scheduleNextPeriodicDhikr(
                    context,
                    settings.periodicDhikrIntervalMinutes
                )
                app.preferencesRepository.updatePeriodicDhikr(
                    nextTriggerMillis = nextTrigger,
                    phraseIndex = nextIndex
                )

                if (settings.periodicDhikrMode.includesVoice) {
                    speaker = DhikrSpeaker(context, app.offlineAudioRepository)
                    speaker.playPeriodicDhikrAndAwait(
                        phraseIndex = currentIndex,
                        phraseText = phrase,
                        voiceId = settings.adhkarVoiceId,
                        chimeBefore = settings.adhkarChimeBeforeVoice,
                        timeoutMs = 22_000L
                    )
                } else if (settings.periodicDhikrMode == PeriodicDhikrMode.CHIME_AND_NOTIFICATION) {
                    try {
                        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        RingtoneManager.getRingtone(context, uri)?.play()
                    } catch (_: Exception) {
                    }
                }
            } finally {
                try {
                    speaker?.release()
                } catch (_: Exception) {
                }
                try {
                    if (wakeLock?.isHeld == true) wakeLock.release()
                } catch (_: Exception) {
                }
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 5002

        fun postDhikrNotification(
            context: Context,
            phrase: String,
            mode: PeriodicDhikrMode
        ) {
            if (!mode.includesNotification) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }

            val openIntent = PendingIntent.getActivity(
                context,
                50,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, NotificationChannels.DHIKR_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("اذكر الله يذكرك")
                .setContentText(phrase)
                .setStyle(NotificationCompat.BigTextStyle().bigText(phrase))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(openIntent)

            if (mode == PeriodicDhikrMode.SILENT_NOTIFICATION) {
                builder.setSilent(true)
            }

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        }
    }
}
