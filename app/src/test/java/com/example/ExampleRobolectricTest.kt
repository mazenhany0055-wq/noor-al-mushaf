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
