package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("نور المصحف", appName)
  }

  @Test
  fun `instantiate all ViewModels and trigger audio without crashing`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(app)

    val mainVm = factory.create(com.example.ui.viewmodel.MainViewModel::class.java)
    val quranVm = factory.create(com.example.ui.viewmodel.QuranViewModel::class.java)
    val tasbihVm = factory.create(com.example.ui.viewmodel.TasbihViewModel::class.java)
    val adhkarVm = factory.create(com.example.ui.viewmodel.AdhkarViewModel::class.java)

    org.junit.Assert.assertNotNull(mainVm)
    org.junit.Assert.assertNotNull(quranVm)
    org.junit.Assert.assertNotNull(tasbihVm)
    org.junit.Assert.assertNotNull(adhkarVm)

    // Verify triggering Quran playback and Adhan preview does not throw IllegalArgumentException
    mainVm.audioManager.playSurah(1, "yasser")
    mainVm.testAdhanNow()
    mainVm.stopAdhanNow()
    mainVm.audioManager.stopPlayback()
    adhkarVm.stopSpeaking()
  }

  @Test
  fun `verify adhkar periodic modes and voice presets`() {
    org.junit.Assert.assertTrue(com.example.domain.model.PeriodicDhikrMode.VOICE_AND_NOTIFICATION.includesVoice)
    org.junit.Assert.assertTrue(com.example.domain.model.PeriodicDhikrMode.VOICE_AND_NOTIFICATION.includesNotification)
    org.junit.Assert.assertTrue(com.example.domain.model.PeriodicDhikrMode.VOICE_ONLY.includesVoice)
    org.junit.Assert.assertFalse(com.example.domain.model.PeriodicDhikrMode.VOICE_ONLY.includesNotification)
    org.junit.Assert.assertFalse(com.example.domain.model.PeriodicDhikrMode.SILENT_NOTIFICATION.includesVoice)
    org.junit.Assert.assertTrue(com.example.domain.model.PeriodicDhikrMode.SILENT_NOTIFICATION.includesNotification)

    org.junit.Assert.assertNotNull(com.example.domain.model.AdhkarVoicePreset.fromId("calm_deep"))
    org.junit.Assert.assertNotNull(com.example.domain.model.AdhkarVoicePreset.fromId("slow_teaching"))
  }

  @Test
  fun `verify spoken Adhan text and alarm scheduler permission helpers`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val spokenFajr = com.example.service.DhikrSpeaker.buildSpokenAdhanText("الفجر")
    val spokenDhuhr = com.example.service.DhikrSpeaker.buildSpokenAdhanText("الظهر")
    org.junit.Assert.assertTrue(spokenFajr.contains("الصلاة خير من النوم"))
    org.junit.Assert.assertFalse(spokenDhuhr.contains("الصلاة خير من النوم"))
    org.junit.Assert.assertTrue(spokenDhuhr.contains("الظهر"))

    // Ensure permission check helpers execute safely on Robolectric SDK 36
    com.example.service.AlarmScheduler.canScheduleExactAlarms(context)
    com.example.service.AlarmScheduler.isIgnoringBatteryOptimizations(context)

    val settings = com.example.data.preferences.UserSettings()
    com.example.service.AlarmScheduler.schedulePrayerAlarms(context, settings)

    // Verify AdhanAlarmReceiver invokes AdhanPlaybackService cleanly
    val receiver = com.example.receiver.AdhanAlarmReceiver()
    val alarmIntent = android.content.Intent(context, com.example.receiver.AdhanAlarmReceiver::class.java).apply {
      putExtra(com.example.receiver.AdhanAlarmReceiver.EXTRA_PRAYER_NAME, "العصر")
      putExtra(com.example.receiver.AdhanAlarmReceiver.EXTRA_ADHAN_VOICE, "makkah")
    }
    receiver.onReceive(context, alarmIntent)

    val serviceController = org.robolectric.Robolectric.buildService(
      com.example.service.AdhanPlaybackService::class.java,
      android.content.Intent(context, com.example.service.AdhanPlaybackService::class.java).apply {
        putExtra(com.example.service.AdhanPlaybackService.EXTRA_PRAYER_NAME, "العصر")
        putExtra(com.example.service.AdhanPlaybackService.EXTRA_VOICE_ID, "tone")
      }
    )
    serviceController.create().startCommand(0, 0)
    serviceController.destroy()
  }

  @Test
  fun `launch MainActivity renders and responds without crashing`() {
    val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java)
      .create()
      .start()
      .resume()
    val activity = controller.get()
    org.junit.Assert.assertNotNull(activity)
    controller.pause().stop().destroy()
  }
}
