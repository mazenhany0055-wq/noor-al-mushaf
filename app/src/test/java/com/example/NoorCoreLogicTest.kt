package com.example

import com.example.data.catalog.AdhkarCatalog
import com.example.data.catalog.QuranCatalog
import com.example.domain.model.CalculationMethod
import com.example.domain.model.PrayerName
import com.example.domain.prayer.PrayerTimeCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoorCoreLogicTest {

    @Test
    fun quranCatalog_hasAll114SurahsAnd30Juz() {
        assertEquals(114, QuranCatalog.surahs.size)
        assertEquals(30, QuranCatalog.juzList.size)
        assertEquals("الفاتحة", QuranCatalog.surahs.first().arabicName)
        assertEquals("الناس", QuranCatalog.surahs.last().arabicName)
        assertTrue(QuranCatalog.surahs.all { it.startPage in 1..604 })
    }

    @Test
    fun prayerCalculator_cairoScheduleInChronologicalOrder() {
        val schedule = PrayerTimeCalculator.calculateSchedule(
            cityName = "القاهرة",
            latitude = 30.0444,
            longitude = 31.2357,
            timeZoneId = "Africa/Cairo",
            method = CalculationMethod.EGYPT,
            enabledPrayers = mapOf(
                PrayerName.FAJR to true,
                PrayerName.DHUHR to true,
                PrayerName.ASR to true,
                PrayerName.MAGHRIB to true,
                PrayerName.ISHA to true
            )
        )
        assertEquals(6, schedule.items.size)
        for (i in 0 until schedule.items.lastIndex) {
            assertTrue(
                "Prayer ${schedule.items[i].prayer} should be before ${schedule.items[i + 1].prayer}",
                schedule.items[i].epochMillis < schedule.items[i + 1].epochMillis
            )
        }
        // Cairo Qibla is roughly Southeast (~136 degrees)
        assertTrue(schedule.qiblaDegrees in 120.0..150.0)
        assertTrue(schedule.hijriDate.contains("هـ"))
    }

    @Test
    fun adhkarCatalog_hasPeriodicPhrasesAndCategories() {
        assertTrue(AdhkarCatalog.periodicDhikrPhrases.isNotEmpty())
        assertTrue(AdhkarCatalog.allDhikr.isNotEmpty())
        assertTrue(AdhkarCatalog.defaultTasbihPresets.isNotEmpty())
    }

    @Test
    fun recitersCatalog_defaultsToYasserDosariWithAlQuranCloudAndMirrors() {
        val defaultReciter = QuranCatalog.reciters.first()
        assertEquals("yasser", defaultReciter.id)
        val urls = defaultReciter.surahAudioUrls(1)
        assertTrue(urls.any { it.contains("yasser_ad-dussary/001.mp3") })
        assertTrue(urls.any { it.contains("server11.mp3quran.net/yasser/001.mp3") })
        assertTrue(defaultReciter.everyAyahUrl(1, 1)?.contains("Yasser_Ad-Dussary_128kbps/001001.mp3") == true)

        val alafasy = QuranCatalog.reciters.first { it.id == "afs" }
        assertTrue(alafasy.surahAudioUrls(1).first().contains("cdn.islamic.network/quran/audio-surah/128/ar.alafasy/1.mp3"))
    }
}
