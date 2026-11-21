package com.example.receiver

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.NoorAlMushafApp
import com.example.R
import com.example.service.AdhanPlaybackService
import com.example.service.AlarmScheduler
import com.example.service.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AdhanAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        NotificationChannels.registerAll(context)

        val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: "الصلاة"
        val voiceId = intent.getStringExtra(EXTRA_ADHAN_VOICE) ?: "makkah"

        val serviceIntent = Intent(context, AdhanPlaybackService::class.java).apply {
            putExtra(AdhanPlaybackService.EXTRA_PRAYER_NAME, prayerName)
            putExtra(AdhanPlaybackService.EXTRA_VOICE_ID, voiceId)
        }

        val startedForegroundService = try {
            ContextCompat.startForegroundService(context, serviceIntent)
            true
        } catch (_: Exception) {
            false
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? NoorAlMushafApp
                val settings = app?.preferencesRepository?.getCurrentSettings()
                if (settings != null) {
                    AlarmScheduler.schedulePrayerAlarms(context, settings)
                }

                if (!startedForegroundService) {
                    postAudibleFallbackNotification(context, prayerName)
                    try {
                        val ringUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        RingtoneManager.getRingtone(context, ringUri)?.play()
                    } catch (_: Exception) {
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postAudibleFallbackNotification(context: Context, prayerName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val openAppIntent = PendingIntent.getActivity(
            context,
            401,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.ADHAN_FALLBACK_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("حان الآن موعد أذان $prayerName")
            .setContentText("حيّ على الصلاة، حيّ على الفلاح")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(4002, notification)
    }

    companion object {
        const val EXTRA_PRAYER_NAME = "extra_prayer_name"
        const val EXTRA_ADHAN_VOICE = "extra_adhan_voice"
    }
}
