package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class DhikrSpeaker(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null

    fun speakDhikr(phrase: String, onFinished: (() -> Unit)? = null) {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(appContext, ringtoneUri)?.play()
        } catch (_: Exception) {
        }

        tts?.shutdown()
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts ?: return@TextToSpeech
                val arabicLocale = Locale("ar")
                val result = engine.setLanguage(arabicLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.language = Locale.getDefault()
                }
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                engine.setSpeechRate(0.92f)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        onFinished?.invoke()
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        onFinished?.invoke()
                    }
                })
                engine.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "dhikr_${System.currentTimeMillis()}")
            } else {
                onFinished?.invoke()
            }
        }
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
