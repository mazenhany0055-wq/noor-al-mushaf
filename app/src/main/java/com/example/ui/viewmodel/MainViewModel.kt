package com.example.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.location.Geocoder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NoorAlMushafApp
import com.example.data.catalog.AdhkarCatalog
import com.example.data.preferences.UserSettings
import com.example.domain.model.AdhanVoice
import com.example.domain.model.CalculationMethod
import com.example.domain.model.CityPreset
import com.example.domain.model.DailyPrayerSchedule
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
    val audioManager = QuranAudioManager(application, app.apiService)
    private val dhikrSpeaker = DhikrSpeaker(application)

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
                    showBanner("تعذر تحديد الموقع تلقائيًا، يمكنك اختيار مدينتك من القائمة")
                }
            }
            .addOnFailureListener {
                _isDetectingLocation.value = false
                showBanner("تعذر الوصول للموقع، اختر مدينتك يدويًا")
            }
    }

    private suspend fun resolveCityName(lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(getApplication(), Locale("ar"))
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            val addr = addresses?.firstOrNull()
            addr?.locality ?: addr?.adminArea ?: addr?.countryName ?: "موقعي الحالي"
        } catch (_: Exception) {
            "موقعي الحالي"
        }
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

    fun testAdhanNow() {
        val current = settings.value
        audioManager.playAdhanPreview(current.adhanVoice)
        showBanner("جاري تشغيل ${current.adhanVoice.arabicName} — يمكنك الإيقاف بالضغط على إيقاف")
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

    fun triggerDhikrNow() {
        viewModelScope.launch {
            val current = prefsRepo.getCurrentSettings()
            val phrases = AdhkarCatalog.periodicDhikrPhrases
            val idx = current.periodicDhikrPhraseIndex % phrases.size
            val phrase = phrases[idx]
            val nextIdx = (idx + 1) % phrases.size

            prefsRepo.updatePeriodicDhikr(phraseIndex = nextIdx)
            DhikrReminderReceiver.postDhikrNotification(
                getApplication(),
                phrase,
                current.periodicDhikrMode
            )
            if (current.periodicDhikrMode == PeriodicDhikrMode.VOICE_AND_NOTIFICATION) {
                dhikrSpeaker.speakDhikr(phrase)
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
