package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.catalog.QuranCatalog
import com.example.domain.model.AdhanVoice
import com.example.domain.model.CalculationMethod
import com.example.domain.model.MushafPaperTheme
import com.example.domain.model.PeriodicDhikrMode
import com.example.domain.model.PrayerName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.TimeZone

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "noor_prefs")

data class UserSettings(
    val cityName: String = "القاهرة",
    val latitude: Double = 30.0444,
    val longitude: Double = 31.2357,
    val timeZoneId: String = "Africa/Cairo",
    val isGpsLocation: Boolean = false,
    val calculationMethod: CalculationMethod = CalculationMethod.EGYPT,
    val adhanVoice: AdhanVoice = AdhanVoice.MAKKAH,
    val fajrAdhan: Boolean = true,
    val dhuhrAdhan: Boolean = true,
    val asrAdhan: Boolean = true,
    val maghribAdhan: Boolean = true,
    val ishaAdhan: Boolean = true,
    val isPeriodicDhikrEnabled: Boolean = false,
    val periodicDhikrIntervalMinutes: Int = 5,
    val periodicDhikrMode: PeriodicDhikrMode = PeriodicDhikrMode.VOICE_AND_NOTIFICATION,
    val nextPeriodicDhikrEpochMillis: Long = 0L,
    val periodicDhikrPhraseIndex: Int = 0,
    val lastReadPage: Int = 1,
    val mushafFontSize: Float = 24f,
    val mushafPaperTheme: MushafPaperTheme = MushafPaperTheme.CREAM,
    val selectedReciterId: String = "yasser",
    val darkModeSetting: String = "system",
    val tasbihSoundEnabled: Boolean = true,
    val tasbihVibrationEnabled: Boolean = true,
    val selectedTasbihId: Int = 1
) {
    fun enabledPrayersMap(): Map<PrayerName, Boolean> = mapOf(
        PrayerName.FAJR to fajrAdhan,
        PrayerName.SUNRISE to false,
        PrayerName.DHUHR to dhuhrAdhan,
        PrayerName.ASR to asrAdhan,
        PrayerName.MAGHRIB to maghribAdhan,
        PrayerName.ISHA to ishaAdhan
    )
}

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val CITY_NAME = stringPreferencesKey("city_name")
        val LATITUDE = doublePreferencesKey("latitude")
        val LONGITUDE = doublePreferencesKey("longitude")
        val TIMEZONE_ID = stringPreferencesKey("timezone_id")
        val IS_GPS_LOCATION = booleanPreferencesKey("is_gps_location")
        val CALCULATION_METHOD = stringPreferencesKey("calculation_method")
        val ADHAN_VOICE = stringPreferencesKey("adhan_voice")
        val FAJR_ADHAN = booleanPreferencesKey("fajr_adhan")
        val DHUHR_ADHAN = booleanPreferencesKey("dhuhr_adhan")
        val ASR_ADHAN = booleanPreferencesKey("asr_adhan")
        val MAGHRIB_ADHAN = booleanPreferencesKey("maghrib_adhan")
        val ISHA_ADHAN = booleanPreferencesKey("isha_adhan")
        val PERIODIC_DHIKR_ENABLED = booleanPreferencesKey("periodic_dhikr_enabled")
        val PERIODIC_DHIKR_INTERVAL = intPreferencesKey("periodic_dhikr_interval")
        val PERIODIC_DHIKR_MODE = stringPreferencesKey("periodic_dhikr_mode")
        val NEXT_PERIODIC_DHIKR_EPOCH = longPreferencesKey("next_periodic_dhikr_epoch")
        val PERIODIC_DHIKR_INDEX = intPreferencesKey("periodic_dhikr_index")
        val LAST_READ_PAGE = intPreferencesKey("last_read_page")
        val MUSHAF_FONT_SIZE = floatPreferencesKey("mushaf_font_size")
        val MUSHAF_PAPER_THEME = stringPreferencesKey("mushaf_paper_theme")
        val SELECTED_RECITER_ID = stringPreferencesKey("selected_reciter_id")
        val DARK_MODE_SETTING = stringPreferencesKey("dark_mode_setting")
        val TASBIH_SOUND = booleanPreferencesKey("tasbih_sound")
        val TASBIH_VIBRATION = booleanPreferencesKey("tasbih_vibration")
        val SELECTED_TASBIH_ID = intPreferencesKey("selected_tasbih_id")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val defaultCity = QuranCatalog.cities.first()
        UserSettings(
            cityName = prefs[Keys.CITY_NAME] ?: defaultCity.arabicName,
            latitude = prefs[Keys.LATITUDE] ?: defaultCity.latitude,
            longitude = prefs[Keys.LONGITUDE] ?: defaultCity.longitude,
            timeZoneId = prefs[Keys.TIMEZONE_ID] ?: defaultCity.timeZoneId,
            isGpsLocation = prefs[Keys.IS_GPS_LOCATION] ?: false,
            calculationMethod = CalculationMethod.fromId(prefs[Keys.CALCULATION_METHOD] ?: "egypt"),
            adhanVoice = AdhanVoice.fromId(prefs[Keys.ADHAN_VOICE] ?: "makkah"),
            fajrAdhan = prefs[Keys.FAJR_ADHAN] ?: true,
            dhuhrAdhan = prefs[Keys.DHUHR_ADHAN] ?: true,
            asrAdhan = prefs[Keys.ASR_ADHAN] ?: true,
            maghribAdhan = prefs[Keys.MAGHRIB_ADHAN] ?: true,
            ishaAdhan = prefs[Keys.ISHA_ADHAN] ?: true,
            isPeriodicDhikrEnabled = prefs[Keys.PERIODIC_DHIKR_ENABLED] ?: false,
            periodicDhikrIntervalMinutes = prefs[Keys.PERIODIC_DHIKR_INTERVAL] ?: 5,
            periodicDhikrMode = PeriodicDhikrMode.fromId(prefs[Keys.PERIODIC_DHIKR_MODE] ?: "voice"),
            nextPeriodicDhikrEpochMillis = prefs[Keys.NEXT_PERIODIC_DHIKR_EPOCH] ?: 0L,
            periodicDhikrPhraseIndex = prefs[Keys.PERIODIC_DHIKR_INDEX] ?: 0,
            lastReadPage = (prefs[Keys.LAST_READ_PAGE] ?: 1).coerceIn(1, 604),
            mushafFontSize = prefs[Keys.MUSHAF_FONT_SIZE] ?: 24f,
            mushafPaperTheme = MushafPaperTheme.fromId(prefs[Keys.MUSHAF_PAPER_THEME] ?: "cream"),
            selectedReciterId = prefs[Keys.SELECTED_RECITER_ID] ?: "yasser",
            darkModeSetting = prefs[Keys.DARK_MODE_SETTING] ?: "system",
            tasbihSoundEnabled = prefs[Keys.TASBIH_SOUND] ?: true,
            tasbihVibrationEnabled = prefs[Keys.TASBIH_VIBRATION] ?: true,
            selectedTasbihId = prefs[Keys.SELECTED_TASBIH_ID] ?: 1
        )
    }

    suspend fun getCurrentSettings(): UserSettings = settingsFlow.first()

    suspend fun updateCity(
        cityName: String,
        latitude: Double,
        longitude: Double,
        timeZoneId: String = TimeZone.getDefault().id,
        isGps: Boolean = false
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CITY_NAME] = cityName
            prefs[Keys.LATITUDE] = latitude
            prefs[Keys.LONGITUDE] = longitude
            prefs[Keys.TIMEZONE_ID] = timeZoneId
            prefs[Keys.IS_GPS_LOCATION] = isGps
        }
    }

    suspend fun updateCalculationMethod(method: CalculationMethod) {
        context.dataStore.edit { it[Keys.CALCULATION_METHOD] = method.id }
    }

    suspend fun updateAdhanVoice(voice: AdhanVoice) {
        context.dataStore.edit { it[Keys.ADHAN_VOICE] = voice.id }
    }

    suspend fun togglePrayerAdhan(prayer: PrayerName, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            when (prayer) {
                PrayerName.FAJR -> prefs[Keys.FAJR_ADHAN] = enabled
                PrayerName.DHUHR -> prefs[Keys.DHUHR_ADHAN] = enabled
                PrayerName.ASR -> prefs[Keys.ASR_ADHAN] = enabled
                PrayerName.MAGHRIB -> prefs[Keys.MAGHRIB_ADHAN] = enabled
                PrayerName.ISHA -> prefs[Keys.ISHA_ADHAN] = enabled
                PrayerName.SUNRISE -> Unit
            }
        }
    }

    suspend fun updatePeriodicDhikr(
        enabled: Boolean? = null,
        intervalMinutes: Int? = null,
        mode: PeriodicDhikrMode? = null,
        nextTriggerMillis: Long? = null,
        phraseIndex: Int? = null
    ) {
        context.dataStore.edit { prefs ->
            enabled?.let { prefs[Keys.PERIODIC_DHIKR_ENABLED] = it }
            intervalMinutes?.let { prefs[Keys.PERIODIC_DHIKR_INTERVAL] = it }
            mode?.let { prefs[Keys.PERIODIC_DHIKR_MODE] = it.id }
            nextTriggerMillis?.let { prefs[Keys.NEXT_PERIODIC_DHIKR_EPOCH] = it }
            phraseIndex?.let { prefs[Keys.PERIODIC_DHIKR_INDEX] = it }
        }
    }

    suspend fun updateLastReadPage(page: Int) {
        context.dataStore.edit { it[Keys.LAST_READ_PAGE] = page.coerceIn(1, 604) }
    }

    suspend fun updateMushafFontSize(size: Float) {
        context.dataStore.edit { it[Keys.MUSHAF_FONT_SIZE] = size.coerceIn(18f, 36f) }
    }

    suspend fun updateMushafPaperTheme(theme: MushafPaperTheme) {
        context.dataStore.edit { it[Keys.MUSHAF_PAPER_THEME] = theme.id }
    }

    suspend fun updateSelectedReciter(reciterId: String) {
        context.dataStore.edit { it[Keys.SELECTED_RECITER_ID] = reciterId }
    }

    suspend fun updateDarkMode(setting: String) {
        context.dataStore.edit { it[Keys.DARK_MODE_SETTING] = setting }
    }

    suspend fun updateTasbihPreferences(sound: Boolean? = null, vibration: Boolean? = null, selectedId: Int? = null) {
        context.dataStore.edit { prefs ->
            sound?.let { prefs[Keys.TASBIH_SOUND] = it }
            vibration?.let { prefs[Keys.TASBIH_VIBRATION] = it }
            selectedId?.let { prefs[Keys.SELECTED_TASBIH_ID] = it }
        }
    }
}
