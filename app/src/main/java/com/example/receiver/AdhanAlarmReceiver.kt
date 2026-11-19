package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.NoorAlMushafApp
import com.example.service.AdhanPlaybackService
import com.example.service.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AdhanAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: "الصلاة"
        val voiceId = intent.getStringExtra(EXTRA_ADHAN_VOICE) ?: "makkah"

        val serviceIntent = Intent(context, AdhanPlaybackService::class.java).apply {
            putExtra(AdhanPlaybackService.EXTRA_PRAYER_NAME, prayerName)
            putExtra(AdhanPlaybackService.EXTRA_VOICE_ID, voiceId)
        }
        ContextCompat.startForegroundService(context, serviceIntent)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as NoorAlMushafApp
                val settings = app.preferencesRepository.getCurrentSettings()
                AlarmScheduler.schedulePrayerAlarms(context, settings)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_PRAYER_NAME = "extra_prayer_name"
        const val EXTRA_ADHAN_VOICE = "extra_adhan_voice"
    }
}
