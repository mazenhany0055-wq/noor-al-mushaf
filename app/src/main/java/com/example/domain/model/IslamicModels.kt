package com.example.domain.model

enum class PrayerName(val arabicName: String, val isObligatoryAdhan: Boolean) {
    FAJR("الفجر", true),
    SUNRISE("الشروق", false),
    DHUHR("الظهر", true),
    ASR("العصر", true),
    MAGHRIB("المغرب", true),
    ISHA("العشاء", true)
}

enum class CalculationMethod(
    val id: String,
    val arabicName: String,
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaMinutesAfterMaghrib: Int? = null
) {
    EGYPT("egypt", "الهيئة المصرية العامة للمساحة", 19.5, 17.5),
    UMM_AL_QURA("makkah", "أم القرى (مكة المكرمة)", 18.5, 0.0, ishaMinutesAfterMaghrib = 90),
    MWL("mwl", "رابطة العالم الإسلامي", 18.0, 17.0),
    KARACHI("karachi", "جامعة العلوم الإسلامية بكراتشي", 18.0, 18.0),
    ISNA("isna", "أمريكا الشمالية (ISNA)", 15.0, 15.0);

    companion object {
        fun fromId(id: String): CalculationMethod =
            entries.find { it.id == id } ?: EGYPT
    }
}

enum class AdhanVoice(
    val id: String,
    val arabicName: String,
    val streamUrl: String?,
    val fallbackUrl: String? = null
) {
    MAKKAH(
        id = "makkah",
        arabicName = "أذان الحرم المكي",
        streamUrl = "https://www.islamcan.com/audio/adhan/azan1.mp3",
        fallbackUrl = "https://cdn.aladhan.com/audio/adhans/a1.mp3"
    ),
    ALAFASY(
        id = "alafasy",
        arabicName = "مشاري راشد العفاسي",
        streamUrl = "https://cdn.aladhan.com/audio/adhans/a9.mp3",
        fallbackUrl = "https://cdn.aladhan.com/audio/adhans/a4.mp3"
    ),
    MADINAH(
        id = "madinah",
        arabicName = "أذان المسجد النبوي",
        streamUrl = "https://www.islamcan.com/audio/adhan/azan2.mp3",
        fallbackUrl = "https://cdn.aladhan.com/audio/adhans/a2.mp3"
    ),
    SYSTEM_TONE(
        id = "tone",
        arabicName = "تنبيه نغمة الجهاز فقط",
        streamUrl = null
    );

    companion object {
        fun fromId(id: String): AdhanVoice =
            entries.find { it.id == id } ?: MAKKAH
    }
}

data class CityPreset(
    val id: String,
    val arabicName: String,
    val countryArabic: String,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String
)

data class PrayerTimeItem(
    val prayer: PrayerName,
    val hour: Int,
    val minute: Int,
    val formattedTime: String,
    val epochMillis: Long,
    val isAdhanEnabled: Boolean,
    val isNext: Boolean = false
)

data class DailyPrayerSchedule(
    val cityName: String,
    val hijriDate: String,
    val gregorianDate: String,
    val qiblaDegrees: Double,
    val items: List<PrayerTimeItem>,
    val nextPrayer: PrayerTimeItem?
)

data class SurahInfo(
    val number: Int,
    val arabicName: String,
    val englishName: String,
    val isMeccan: Boolean,
    val ayahCount: Int,
    val startPage: Int
) {
    val revelationLabel: String get() = if (isMeccan) "مكية" else "مدنية"
}

data class JuzInfo(
    val number: Int,
    val arabicName: String,
    val startSurahName: String,
    val startPage: Int
)

data class ReciterInfo(
    val id: String,
    val arabicName: String,
    val styleLabel: String,
    val serverBaseUrl: String,
    val alQuranCloudEdition: String? = null,
    val fallbackServerUrls: List<String> = emptyList(),
    val everyAyahFolder: String? = null
) {
    fun surahAudioUrls(surahNumber: Int): List<String> {
        val padded = surahNumber.toString().padStart(3, '0')
        val urls = mutableListOf<String>()
        if (alQuranCloudEdition != null) {
            urls.add("https://cdn.islamic.network/quran/audio-surah/128/$alQuranCloudEdition/$surahNumber.mp3")
        }
        urls.add("$serverBaseUrl/$padded.mp3")
        fallbackServerUrls.forEach { base ->
            urls.add("$base/$padded.mp3")
        }
        return urls.distinct()
    }

    fun everyAyahUrl(surahNumber: Int, ayahInSurah: Int): String? {
        val folder = everyAyahFolder ?: return null
        val s = surahNumber.toString().padStart(3, '0')
        val a = ayahInSurah.toString().padStart(3, '0')
        return "https://everyayah.com/data/$folder/$s$a.mp3"
    }
}

data class MushafAyah(
    val globalNumber: Int,
    val numberInSurah: Int,
    val text: String,
    val surahNumber: Int,
    val surahName: String,
    val page: Int,
    val juz: Int,
    val hizbQuarter: Int,
    val isSajda: Boolean = false
)

enum class MushafPaperTheme(val id: String, val arabicLabel: String) {
    CREAM("cream", "ورق المصحف"),
    LIGHT("light", "أبيض نقي"),
    NIGHT("night", "ليلي مريح");

    companion object {
        fun fromId(id: String): MushafPaperTheme =
            entries.find { it.id == id } ?: CREAM
    }
}

enum class AdhkarCategory(val id: String, val arabicTitle: String, val subtitle: String) {
    MORNING("morning", "أذكار الصباح", "حصن المسلم في بداية اليوم"),
    EVENING("evening", "أذكار المساء", "الطمأنينة والذكر عند المساء"),
    AFTER_PRAYER("after_prayer", "أذكار بعد الصلاة", "ما يُقال عقب السلام من الفريضة"),
    SLEEP("sleep", "أذكار النوم", "الأدعية المأثورة قبل المنام"),
    WAKE_UP("wake_up", "أذكار الاستيقاظ", "شكر الله عند الاستيقاظ"),
    QURANIC_DUAS("quranic_duas", "أدعية قرآنية", "جوامع الدعاء من كتاب الله")
}

data class DhikrItem(
    val id: String,
    val category: AdhkarCategory,
    val text: String,
    val targetCount: Int,
    val virtueOrSource: String
)

enum class PeriodicDhikrMode(val id: String, val arabicLabel: String) {
    VOICE_AND_NOTIFICATION("voice", "صوت (نطق الذكر) + إشعار"),
    CHIME_AND_NOTIFICATION("chime", "نغمة تنبيه + إشعار"),
    SILENT_NOTIFICATION("silent", "إشعار هادئ فقط");

    companion object {
        fun fromId(id: String): PeriodicDhikrMode =
            entries.find { it.id == id } ?: VOICE_AND_NOTIFICATION
    }
}
