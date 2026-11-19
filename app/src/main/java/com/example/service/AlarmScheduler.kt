package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.preferences.UserSettings
import com.example.domain.model.PrayerName
import com.example.domain.prayer.PrayerTimeCalculator
import com.example.receiver.AdhanAlarmReceiver
import com.example.receiver.DhikrReminderReceiver

object AlarmScheduler {

    private const val PRAYER_REQUEST_BASE = 2000
    private const val DHIKR_REQUEST_CODE = 3050

    fun schedulePrayerAlarms(context: Context, settings: UserSettings) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        val enabledMap = settings.enabledPrayersMap()

        val todaySchedule = PrayerTimeCalculator.calculateSchedule(
            cityName = settings.cityName,
            latitude = settings.latitude,
            longitude = settings.longitude,
            timeZoneId = settings.timeZoneId,
            method = settings.calculationMethod,
            enabledPrayers = enabledMap,
            nowMillis = now
        )

        val tomorrowSchedule = PrayerTimeCalculator.calculateSchedule(
            cityName = settings.cityName,
            latitude = settings.latitude,
            longitude = settings.longitude,
            timeZoneId = settings.timeZoneId,
            method = settings.calculationMethod,
            enabledPrayers = enabledMap,
            nowMillis = now + 86_400_000L
        )

        val obligatoryPrayers = listOf(
            PrayerName.FAJR,
            PrayerName.DHUHR,
            PrayerName.ASR,
            PrayerName.MAGHRIB,
            PrayerName.ISHA
        )

        obligatoryPrayers.forEachIndexed { index, prayer ->
            val requestCode = PRAYER_REQUEST_BASE + index
            val intent = Intent(context, AdhanAlarmReceiver::class.java).apply {
                putExtra(AdhanAlarmReceiver.EXTRA_PRAYER_NAME, prayer.arabicName)
                putExtra(AdhanAlarmReceiver.EXTRA_ADHAN_VOICE, settings.adhanVoice.id)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val isEnabled = enabledMap[prayer] == true
            if (!isEnabled) {
                alarmManager.cancel(pendingIntent)
                return@forEachIndexed
            }

            val todayItem = todaySchedule.items.firstOrNull { it.prayer == prayer }
            val triggerAt = if (todayItem != null && todayItem.epochMillis > now + 5_000L) {
                todayItem.epochMillis
            } else {
                tomorrowSchedule.items.firstOrNull { it.prayer == prayer }?.epochMillis
                    ?: (now + 86_400_000L)
            }

            scheduleSingleAlarm(alarmManager, triggerAt, pendingIntent)
        }
    }

    fun scheduleNextPeriodicDhikr(context: Context, intervalMinutes: Int): Long {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val safeMinutes = intervalMinutes.coerceAtLeast(1)
        val triggerAt = System.currentTimeMillis() + safeMinutes * 60_000L

        val intent = Intent(context, DhikrReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DHIKR_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleSingleAlarm(alarmManager, triggerAt, pendingIntent)
        return triggerAt
    }

    fun cancelPeriodicDhikr(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DhikrReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DHIKR_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun scheduleSingleAlarm(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        if (canExact) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }
}
