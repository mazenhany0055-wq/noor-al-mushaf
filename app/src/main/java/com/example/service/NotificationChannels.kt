package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.example.R

object NotificationChannels {
    const val ADHAN_CHANNEL_ID = "adhan_channel"
    const val ADHAN_FALLBACK_CHANNEL_ID = "adhan_fallback_alarm_channel"
    const val DHIKR_CHANNEL_ID = "dhikr_channel"
    const val QURAN_AUDIO_CHANNEL_ID = "quran_audio_channel"

    fun registerAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val adhanChannel = NotificationChannel(
            ADHAN_CHANNEL_ID,
            context.getString(R.string.channel_adhan_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_adhan_desc)
            enableVibration(true)
            setSound(null, null) // Active service plays Adhan audio via USAGE_ALARM
        }

        val alarmSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val adhanFallbackChannel = NotificationChannel(
            ADHAN_FALLBACK_CHANNEL_ID,
            context.getString(R.string.channel_adhan_fallback_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_adhan_fallback_desc)
            enableVibration(true)
            setSound(
                alarmSoundUri,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }

        val dhikrChannel = NotificationChannel(
            DHIKR_CHANNEL_ID,
            context.getString(R.string.channel_dhikr_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_dhikr_desc)
            enableVibration(true)
        }

        val audioChannel = NotificationChannel(
            QURAN_AUDIO_CHANNEL_ID,
            context.getString(R.string.channel_quran_audio_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_quran_audio_desc)
        }

        manager.createNotificationChannels(
            listOf(adhanChannel, adhanFallbackChannel, dhikrChannel, audioChannel)
        )
    }
}
