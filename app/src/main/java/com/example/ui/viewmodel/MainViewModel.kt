package com.example.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.location.Geocoder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NoorAlMushafApp
import com.example.data.catalog.AdhkarCatalog
import com.example.data.catalog.QuranCatalog
import com.example.data.preferences.UserSettings
import com.example.domain.model.AdhanVoice
import com.example.domain.model.AdhkarSheikhVoice
import com.example.domain.model.CalculationMethod
import com.example.domain.model.CityPreset
import com.example.domain.model.DailyPrayerSchedule
import com.example.domain.model.OfflineTtsVoiceInfo
import com.example.domain.model.PeriodicDhikrMode
import com.example.domain.model.PrayerName
import com.example.domain.prayer.PrayerTimeCalculator
import com.example.receiver.DhikrReminderReceiver
import com.example.service.AdhanPlaybackService
import com.example.service.AlarmScheduler
import com.example.service.AudioPlaybackState
import com.example.service.DhikrSpeaker
import com.example.service.QuranAudioManager
import com.google.android.gms.location.LocationServices
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.TimeZone
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class CountdownUiState(
    val prayerCountdownText: String = "٠٠:٠٠:٠٠",
    val periodicCountdownText: String = ""
)

data class HomeUiState(
    val settings: UserSettings = UserSettings(),
    val schedule: DailyPrayerSchedule? = null,
    val activeBannerMessage: String? = null,
    val isDetectingLocation: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NoorAlMushafApp
    private val prefsRepo = app.preferencesRepository
    private val offlineAudioRepo = app.offlineAudioRepository
    val audioManager = QuranAudioManager(application, app.apiService, offlineAudioRepo)
    private val dhikrSpeaker = DhikrSpeaker(application, offlineAudioRepo)

    private val _scheduleRefreshEpoch = MutableStateFlow(System.currentTimeMillis())
    private val _isDetectingLocation = MutableStateFlow(false)
    private val _activeBannerMessage = MutableStateFlow<String?>(null)

    private val _countdownState = MutableStateFlow(CountdownUiState())
    val countdownState: StateFlow<CountdownUiState> = _countdownState.asStateFlow()

    val settings: StateFlow<UserSettings> = prefsRepo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserSettings()
    )

    val audioState: StateFlow<AudioPlaybackState> = audioManager.state
    val offlineSurahsByReciter: StateFlow<Map<String, Set<Int>>> = offlineAudioRepo.offlineSurahsByReciter
    val downloadProgress: StateFlow<Map<String, Float>> = offlineAudioRepo.downloadProgress
    val offlineAdhanIds: StateFlow<Set<String>> = offlineAudioRepo.offlineAdhanIds
    val offlineAdhkarSessionKeys: StateFlow<Set<String>> = offlineAudioRepo.offlineAdhkarSessionKeys
    val totalOfflineAudioBytes: StateFlow<Long> = offlineAudioRepo.totalStorageBytes
    val availableSystemVoices: StateFlow<List<OfflineTtsVoiceInfo>> = dhikrSpeaker.availableSystemVoices

    val isFullMushafBundled: Boolean by lazy { app.quranRepository.isFullMushafBundled() }

    private val initialSchedule: DailyPrayerSchedule by lazy {
        val def = UserSettings()
        PrayerTimeCalculator.calculateSchedule(
            cityName = def.cityName,
            latitude = def.latitude,
            longitude = def.longitude,
            timeZoneId = def.timeZoneId,
            method = def.calculationMethod,
            enabledPrayers = def.enabledPrayersMap(),
            nowMillis = System.currentTimeMillis()
        )
    }

    val homeUiState: StateFlow<HomeUiState> = combine(
        settings,
        _scheduleRefreshEpoch,
        _isDetectingLocation,
        _activeBannerMessage
    ) { currentSettings, refreshNow, detecting, banner ->
        val schedule = withContext(Dispatchers.Default) {
            PrayerTimeCalculator.calculateSchedule(
                cityName = currentSettings.cityName,
                latitude = currentSettings.latitude,
                longitude = currentSettings.longitude,
                timeZoneId = currentSettings.timeZoneId,
                method = currentSettings.calculationMethod,
                enabledPrayers = currentSettings.enabledPrayersMap(),
                nowMillis = refreshNow
            )
        }

        HomeUiState(
            settings = currentSettings,
            schedule = schedule,
            activeBannerMessage = banner,
            isDetectingLocation = detecting
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(schedule = initialSchedule)
    )

    init {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                val now = System.currentTimeMillis()
                val currentUi = homeUiState.value
                val nextPrayerEpoch = currentUi.schedule?.nextPrayer?.epochMillis
                if (nextPrayerEpoch != null && now >= nextPrayerEpoch) {
                    _scheduleRefreshEpoch.value = now
                }
                val nextDiff = (nextPrayerEpoch ?: now) - now
                val prayerCountdown = PrayerTimeCalculator.formatCountdown(nextDiff)
                val currentSettings = currentUi.settings
                val periodicCountdown = if (currentSettings.isPeriodicDhikrEnabled &&
                    currentSettings.nextPeriodicDhikrEpochMillis > now
                ) {
                    PrayerTimeCalculator.formatCountdown(currentSettings.nextPeriodicDhikrEpochMillis - now)
                } else {
                    ""
                }
                val updated = CountdownUiState(
                    prayerCountdownText = prayerCountdown,
                    periodicCountdownText = periodicCountdown
                )
                if (_countdownState.value != updated) {
                    _countdownState.value = updated
                }
                delay(1000L)
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            val initial = prefsRepo.getCurrentSettings()
            AlarmScheduler.schedulePrayerAlarms(getApplication(), initial)
        }
    }

    fun selectCity(city: CityPreset) {
        viewModelScope.launch {
            prefsRepo.updateCity(
                cityName = city.arabicName,
                latitude = city.latitude,
                longitude = city.longitude,
                timeZoneId = city.timeZoneId,
                isGps = false
            )
            AlarmScheduler.schedulePrayerAlarms(getApplication(), prefsRepo.getCurrentSettings())
        }
    }

    @SuppressLint("MissingPermission")
    fun detectGpsLocation() {
        _isDetectingLocation.value = true
        val fusedClient = LocationServices.getFusedLocationProviderClient(getApplication<Application>())
        fusedClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    viewModelScope.launch {
                        val cityLabel = resolveCityName(location.latitude, location.longitude)
                        prefsRepo.updateCity(
                            cityName = cityLabel,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            timeZoneId = TimeZone.getDefault().id,
                            isGps = true
                        )
                        AlarmScheduler.schedulePrayerAlarms(getApplication(), prefsRepo.getCurrentSettings())
                        _isDetectingLocation.value = false
                        showBanner("تم تحديث الموقع إلى: $cityLabel")
                    }
                } else {
                    _isDetectingLocation.value = false
                    showBanner("تعذر تحديد الموقع تلقائيًا، يمكنك اختيار مدينتك من القائمة بدون نت")
                }
            }
            .addOnFailureListener {
                _isDetectingLocation.value = false
                showBanner("تعذر الوصول للموقع، اختر مدينتك من القائمة بدون نت")
            }
    }

    private suspend fun resolveCityName(lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(getApplication(), Locale("ar"))
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            val addr = addresses?.firstOrNull()
            val resolved = addr?.locality ?: addr?.adminArea ?: addr?.countryName
            if (!resolved.isNullOrBlank()) return@withContext resolved
        } catch (_: Exception) {
        }
        // Offline Haversine fallback to closest built-in city
        val nearest = QuranCatalog.cities.minByOrNull { city ->
            haversineKm(lat, lng, city.latitude, city.longitude)
        }
        if (nearest != null) "${nearest.arabicName} (موقعي)" else "موقعي الحالي"
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    fun updateCalculationMethod(method: CalculationMethod) {
        viewModelScope.launch {
            prefsRepo.updateCalculationMethod(method)
            AlarmScheduler.schedulePrayerAlarms(getApplication(), prefsRepo.getCurrentSettings())
        }
    }

    fun updateAdhanVoice(voice: AdhanVoice) {
        viewModelScope.launch {
            prefsRepo.updateAdhanVoice(voice)
            AlarmScheduler.schedulePrayerAlarms(getApplication(), prefsRepo.getCurrentSettings())
        }
    }

    fun togglePrayerAdhan(prayer: PrayerName, enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.togglePrayerAdhan(prayer, enabled)
            AlarmScheduler.schedulePrayerAlarms(getApplication(), prefsRepo.getCurrentSettings())
        }
    }

    fun rescheduleAlarms() {
        viewModelScope.launch(Dispatchers.Default) {
            val current = prefsRepo.getCurrentSettings()
            AlarmScheduler.schedulePrayerAlarms(getApplication(), current)
        }
    }

    fun testAdhanNow() {
        val appContext = getApplication<Application>()
        val current = settings.value
        val prayerLabel = homeUiState.value.schedule?.nextPrayer?.prayer?.arabicName ?: "الصلاة"
        val serviceIntent = Intent(appContext, AdhanPlaybackService::class.java).apply {
            putExtra(AdhanPlaybackService.EXTRA_PRAYER_NAME, prayerLabel)
            putExtra(AdhanPlaybackService.EXTRA_VOICE_ID, current.adhanVoice.id)
            putExtra(AdhanPlaybackService.EXTRA_TTS_VOICE_ID, current.adhkarVoiceId)
        }
        val started = try {
            ContextCompat.startForegroundService(appContext, serviceIntent)
            true
        } catch (_: Exception) {
            false
        }
        if (!started) {
            audioManager.playAdhanPreview(current.adhanVoice)
        }
        showBanner("جاري تشغيل ${current.adhanVoice.arabicName} بصوت الأذان — يمكنك الإيقاف بالضغط على إيقاف")
    }

    fun stopAdhanNow() {
        audioManager.stopAdhanPreview()
        val stopIntent = Intent(getApplication(), AdhanPlaybackService::class.java).apply {
            action = AdhanPlaybackService.ACTION_STOP
        }
        try {
            getApplication<Application>().startService(stopIntent)
        } catch (_: Exception) {
        }
    }

    fun downloadAdhanOffline(voice: AdhanVoice) {
        viewModelScope.launch {
            val res = offlineAudioRepo.downloadAdhanVoice(voice)
            if (res.isSuccess) {
                showBanner("تم حفظ ${voice.arabicName} للعمل بدون إنترنت ✓")
            } else {
                showBanner("تأكد من الاتصال بالإنترنت لتحميل صوت الأذان")
            }
        }
    }

    fun downloadSurahOffline(reciterId: String, surahNumber: Int) {
        val reciter = QuranCatalog.reciters.find { it.id == reciterId } ?: return
        viewModelScope.launch {
            val res = offlineAudioRepo.downloadSurah(reciter, surahNumber)
            if (res.isFailure) {
                showBanner("تعذّر التحميل. تأكد من الاتصال بالإنترنت وحاول مرة أخرى.")
            }
        }
    }

    fun downloadBatchSurahsOffline(reciterId: String, surahNumbers: List<Int>) {
        val reciter = QuranCatalog.reciters.find { it.id == reciterId } ?: return
        viewModelScope.launch {
            if (!offlineAudioRepo.isNetworkConnected()) {
                showBanner("اتصل بالإنترنت أولاً لتحميل السور، وبعدها ستعمل 100% بدون نت")
                return@launch
            }
            var successCount = 0
            for (surah in surahNumbers) {
                if (!offlineAudioRepo.isSurahAvailableOffline(reciter.id, surah)) {
                    val res = offlineAudioRepo.downloadSurah(reciter, surah)
                    if (res.isSuccess) successCount++
                }
            }
            if (successCount > 0) {
                showBanner("تم تحميل ${PrayerTimeCalculator.toArabicDigits(successCount)} سورة بصوت ${reciter.arabicName} بدون نت ✓")
            }
        }
    }

    fun deleteDownloadedSurah(reciterId: String, surahNumber: Int) {
        viewModelScope.launch {
            offlineAudioRepo.deleteDownloadedSurah(reciterId, surahNumber)
        }
    }

    fun setPeriodicDhikrEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = prefsRepo.getCurrentSettings()
            if (enabled) {
                val nextTrigger = AlarmScheduler.scheduleNextPeriodicDhikr(
                    getApplication(),
                    current.periodicDhikrIntervalMinutes
                )
                prefsRepo.updatePeriodicDhikr(enabled = true, nextTriggerMillis = nextTrigger)
                showBanner("تم تفعيل المذكّر التلقائي كل ${PrayerTimeCalculator.toArabicDigits(current.periodicDhikrIntervalMinutes)} دقائق")
            } else {
                AlarmScheduler.cancelPeriodicDhikr(getApplication())
                prefsRepo.updatePeriodicDhikr(enabled = false, nextTriggerMillis = 0L)
                showBanner("تم إيقاف المذكّر التلقائي")
            }
        }
    }

    fun setPeriodicDhikrInterval(minutes: Int) {
        viewModelScope.launch {
            val current = prefsRepo.getCurrentSettings()
            val nextTrigger = if (current.isPeriodicDhikrEnabled) {
                AlarmScheduler.scheduleNextPeriodicDhikr(getApplication(), minutes)
            } else {
                0L
            }
            prefsRepo.updatePeriodicDhikr(
                intervalMinutes = minutes,
                nextTriggerMillis = nextTrigger
            )
        }
    }

    fun setPeriodicDhikrMode(mode: PeriodicDhikrMode) {
        viewModelScope.launch {
            prefsRepo.updatePeriodicDhikr(mode = mode)
        }
    }

    fun updateAdhkarVoice(voiceId: String) {
        viewModelScope.launch {
            val sheikh = AdhkarSheikhVoice.fromId(voiceId)
            prefsRepo.updateAdhkarVoicePreferences(
                voiceId = sheikh.id,
                quranReciterId = sheikh.quranReciterId
            )
            showBanner("تم اختيار ${sheikh.arabicName} للأذكار")
        }
    }

    fun updateAdhkarQuranReciter(reciterId: String) {
        viewModelScope.launch {
            prefsRepo.updateAdhkarVoicePreferences(quranReciterId = reciterId)
        }
    }

    fun updateAdhkarChimeBeforeVoice(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateAdhkarVoicePreferences(chimeBeforeVoice = enabled)
        }
    }

    fun downloadAdhkarSession(sheikh: AdhkarSheikhVoice, isMorning: Boolean) {
        viewModelScope.launch {
            val res = offlineAudioRepo.downloadSheikhAdhkarSession(sheikh, isMorning)
            val label = if (isMorning) "أذكار الصباح" else "أذكار المساء"
            if (res.isSuccess) {
                showBanner("تم حفظ $label بصوت ${sheikh.arabicName} للعمل بدون نت ✓")
            } else {
                showBanner("تعذر التحميل — تأكد من الاتصال بالإنترنت لحفظ $label")
            }
        }
    }

    fun triggerDhikrNow() {
        viewModelScope.launch {
            val current = prefsRepo.getCurrentSettings()
            val phrases = AdhkarCatalog.periodicDhikrPhrases
            val idx = current.periodicDhikrPhraseIndex % phrases.size
            val phrase = phrases[idx]
            val nextIdx = (idx + 1) % phrases.size

            prefsRepo.updatePeriodicDhikr(phraseIndex = nextIdx)
            if (current.periodicDhikrMode.includesNotification) {
                DhikrReminderReceiver.postDhikrNotification(
                    getApplication(),
                    phrase,
                    current.periodicDhikrMode
                )
            }
            if (current.periodicDhikrMode.includesVoice) {
                dhikrSpeaker.playPeriodicDhikr(
                    phraseIndex = idx,
                    phraseText = phrase,
                    voiceId = current.adhkarVoiceId,
                    chimeBefore = current.adhkarChimeBeforeVoice,
                    useMediaUsage = true
                )
            }
            showBanner(phrase)
        }
    }

    fun updateSelectedReciter(reciterId: String) {
        viewModelScope.launch {
            prefsRepo.updateSelectedReciter(reciterId)
        }
    }

    fun updateDarkModeSetting(mode: String) {
        viewModelScope.launch {
            prefsRepo.updateDarkMode(mode)
        }
    }

    fun dismissBanner() {
        _activeBannerMessage.value = null
    }

    private fun showBanner(message: String) {
        _activeBannerMessage.value = message
    }

    override fun onCleared() {
        audioManager.release()
        dhikrSpeaker.release()
        super.onCleared()
    }
}
