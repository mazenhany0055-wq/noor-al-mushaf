package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    assertEquals("نور القرأن", appName)
  }

  @Test
  fun `instantiate all ViewModels and trigger audio without crashing`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(app)

    val mainVm = factory.create(com.example.ui.viewmodel.MainViewModel::class.java)
    val quranVm = factory.create(com.example.ui.viewmodel.QuranViewModel::class.java)
    val tasbihVm = factory.create(com.example.ui.viewmodel.TasbihViewModel::class.java)
    val adhkarVm = factory.create(com.example.ui.viewmodel.AdhkarViewModel::class.java)
    val lessonsVm = factory.create(com.example.ui.viewmodel.LessonsViewModel::class.java)

    assertNotNull(mainVm)
    assertNotNull(quranVm)
    assertNotNull(tasbihVm)
    assertNotNull(adhkarVm)
    assertNotNull(lessonsVm)

    // Verify triggering Quran playback and Adhan preview does not throw IllegalArgumentException
    mainVm.audioManager.playSurah(1, "yasser")
    mainVm.testAdhanNow()
    mainVm.stopAdhanNow()
    mainVm.audioManager.stopPlayback()
    adhkarVm.stopSpeaking()
  }

  @Test
  fun `verify adhkar strict real sheikh audio and unavailable callback without TTS`() {
    assertTrue(com.example.domain.model.PeriodicDhikrMode.VOICE_AND_NOTIFICATION.includesVoice)
    assertTrue(com.example.domain.model.PeriodicDhikrMode.VOICE_AND_NOTIFICATION.includesNotification)
    assertTrue(com.example.domain.model.PeriodicDhikrMode.VOICE_ONLY.includesVoice)
    assertFalse(com.example.domain.model.PeriodicDhikrMode.VOICE_ONLY.includesNotification)
    assertFalse(com.example.domain.model.PeriodicDhikrMode.SILENT_NOTIFICATION.includesVoice)
    assertTrue(com.example.domain.model.PeriodicDhikrMode.SILENT_NOTIFICATION.includesNotification)

    val defaultSettings = com.example.data.preferences.UserSettings()
    assertEquals("sheikh_multi", defaultSettings.adhkarVoiceId)
    assertEquals("afs", defaultSettings.adhkarQuranReciterId)

    assertEquals(
      com.example.domain.model.AdhkarSheikhVoice.ALAFASY,
      com.example.domain.model.AdhkarSheikhVoice.fromId("sheikh_alafasy")
    )
    assertEquals(
      com.example.domain.model.AdhkarSheikhVoice.NASSER_ALQATAMI,
      com.example.domain.model.AdhkarSheikhVoice.fromId("sheikh_qatami")
    )
    assertEquals(
      com.example.domain.model.AdhkarSheikhVoice.HUSSARY,
      com.example.domain.model.AdhkarSheikhVoice.fromId("sheikh_hussary")
    )
    assertEquals(
      com.example.domain.model.AdhkarSheikhVoice.MULTI_SHEIKHS,
      com.example.domain.model.AdhkarSheikhVoice.fromId("unknown_id")
    )

    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val offlineRepo = com.example.data.repository.OfflineAudioRepository(app)
    assertNull(offlineRepo.getOfflineDhikrClipUri("m2"))
    assertNull(offlineRepo.getOfflinePeriodicDhikrUri(0))

    // Selecting a Quran-only Sheikh on a non-Quranic Hisn item MUST report "غير متوفر" instead of faking TTS
    val speaker = com.example.service.DhikrSpeaker(app, offlineRepo)
    var unavailableMsg: String? = null
    val nonQuranDhikr = com.example.domain.model.DhikrItem(
      id = "test_hisn",
      category = com.example.domain.model.AdhkarCategory.MORNING,
      text = "سبحان الله وبحمده",
      targetCount = 100,
      virtueOrSource = "مسلم"
    )
    speaker.playDhikrItem(
      item = nonQuranDhikr,
      voiceId = com.example.domain.model.AdhkarSheikhVoice.HUSSARY.id,
      quranReciterId = "husr",
      chimeBefore = false,
      onUnavailable = { unavailableMsg = it }
    )
    assertNotNull(unavailableMsg)
    assertTrue(unavailableMsg!!.contains("غير متوفر"))

    val factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(app)
    val adhkarVm = factory.create(com.example.ui.viewmodel.AdhkarViewModel::class.java)
    adhkarVm.previewVoice("sheikh_hussary", false)
    assertTrue(adhkarVm.uiState.value.voiceStatusMessage?.contains("غير متوفر") == true)
    adhkarVm.clearVoiceStatusMessage()
    assertNull(adhkarVm.uiState.value.voiceStatusMessage)
    adhkarVm.stopSpeaking()
  }

  @Test
  fun `verify official YouTube lessons catalog filters and broken video auto removal`() {
    val scholarIds = com.example.data.catalog.ScholarCatalog.scholars.map { it.id }.toSet()
    assertTrue(
      scholarIds.containsAll(
        listOf(
          "latest", "shaarawy", "samir_mustafa", "yasser", "ahmed_samir",
          "mohamed_azab", "hussary", "yaqoub", "hassan", "abdelkafy",
          "alkhamis", "nabulsi", "ahmed_amer"
        )
      )
    )

    // Verify AI / silent / static-image filter rejects non-speaking or synthetic videos
    assertTrue(
      com.example.data.repository.LessonsRepository.isLikelyAiOrStaticOrNonSpeaking(
        title = "درس بصوت ذكاء اصطناعي بدون صوت",
        description = "",
        requireVisualHint = false
      )
    )
    assertTrue(
      com.example.data.repository.LessonsRepository.isLikelyAiOrStaticOrNonSpeaking(
        title = "قصة حياة الشيخ مع صورة ثابتة",
        description = "",
        requireVisualHint = false
      )
    )
    assertTrue(
      com.example.data.repository.LessonsRepository.isLikelyAiOrStaticOrNonSpeaking(
        title = "سورة البقرة كاملة",
        description = "تلاوة",
        requireVisualHint = true
      )
    )
    assertFalse(
      com.example.data.repository.LessonsRepository.isLikelyAiOrStaticOrNonSpeaking(
        title = "تفسير سورة البقرة - درس مرئي للشيخ الشعراوي",
        description = "حلقة تلفزيونية كاملة",
        requireVisualHint = true
      )
    )

    // Verify ISO 8601 duration parser and Secrets BuildConfig key presence
    assertEquals(3665L, com.example.data.repository.LessonsRepository.parseIsoDurationSeconds("PT1H1M5S"))
    assertEquals(420L, com.example.data.repository.LessonsRepository.parseIsoDurationSeconds("PT7M"))
    assertNotNull(BuildConfig.YOUTUBE_API_KEY)

    // Verify broken video detection & toggleFavorite in LessonsViewModel
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(app)
    val lessonsVm = factory.create(com.example.ui.viewmodel.LessonsViewModel::class.java)
    lessonsVm.toggleFavorite("dQw4w9WgXcQ")
    assertTrue(lessonsVm.favoriteVideoIds.value.contains("dQw4w9WgXcQ"))
    lessonsVm.toggleFavorite("dQw4w9WgXcQ")
    assertFalse(lessonsVm.favoriteVideoIds.value.contains("dQw4w9WgXcQ"))

    val dummyBroken = com.example.domain.model.ReligiousLesson(
      id = "yt_abc12345678",
      youtubeVideoId = "abc12345678",
      scholarId = "shaarawy",
      scholarName = "الشيخ محمد متولي الشعراوي",
      channelId = "UCijSEIUA72pyzSNOf_lx5ng",
      channelTitle = "القناة الرسمية",
      title = "درس تجريبي",
      description = "",
      thumbnailUrl = "https://i.ytimg.com/vi/abc12345678/hqdefault.jpg",
      durationSeconds = 600L,
      publishedDateLabel = "2026-01-01",
      isVerifiedOfficial = true
    )
    lessonsVm.handleBrokenOrSilentLesson(dummyBroken, "صامت")
    lessonsVm.closePlayer()
  }

  @Test
  fun `verify alarm scheduler permission helpers and Adhan service without TTS`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.service.AlarmScheduler.canScheduleExactAlarms(context)
    com.example.service.AlarmScheduler.isIgnoringBatteryOptimizations(context)

    val settings = com.example.data.preferences.UserSettings()
    com.example.service.AlarmScheduler.schedulePrayerAlarms(context, settings)

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
    assertNotNull(activity)
    controller.pause().stop().destroy()
  }
}
