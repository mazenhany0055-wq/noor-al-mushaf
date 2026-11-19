package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.NoorAlMushafApp
import com.example.service.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val app = context.applicationContext as NoorAlMushafApp
                    val settings = app.preferencesRepository.getCurrentSettings()
                    AlarmScheduler.schedulePrayerAlarms(context, settings)
                    if (settings.isPeriodicDhikrEnabled) {
                        val nextTrigger = AlarmScheduler.scheduleNextPeriodicDhikr(
                            context,
                            settings.periodicDhikrIntervalMinutes
                        )
                        app.preferencesRepository.updatePeriodicDhikr(nextTriggerMillis = nextTrigger)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
