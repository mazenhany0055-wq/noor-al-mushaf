package com.example

import android.app.Application
import com.example.data.local.NoorDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.remote.AlQuranApiService
import com.example.data.repository.QuranRepository
import com.example.data.repository.TasbihRepository
import com.example.service.NotificationChannels

class NoorAlMushafApp : Application() {

    val database: NoorDatabase by lazy { NoorDatabase.getInstance(this) }
    val preferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepository(this) }
    val apiService: AlQuranApiService by lazy { AlQuranApiService.create() }
    val offlineAudioRepository: com.example.data.repository.OfflineAudioRepository by lazy {
        com.example.data.repository.OfflineAudioRepository(this)
    }
    val quranRepository: QuranRepository by lazy {
        QuranRepository(
            context = this,
            quranPageDao = database.quranPageDao(),
            bookmarkDao = database.bookmarkDao(),
            apiService = apiService
        )
    }
    val tasbihRepository: TasbihRepository by lazy { TasbihRepository(database.tasbihDao()) }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.registerAll(this)
    }
}
