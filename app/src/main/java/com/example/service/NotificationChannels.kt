package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.R

object NotificationChannels {
    const val ADHAN_CHANNEL_ID = "adhan_channel"
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
            setSound(null, null) // Sound is handled by AdhanPlaybackService MediaPlayer
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

        manager.createNotificationChannels(listOf(adhanChannel, dhikrChannel, audioChannel))
    }
}
