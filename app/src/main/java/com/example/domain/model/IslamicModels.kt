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
    val fallbackUrl: String? = null,
    val bundledAssetPath: String? = null
) {
    MAKKAH(
        id = "makkah",
        arabicName = "أذان الحرم المكي",
        streamUrl = "https://www.islamcan.com/audio/adhan/azan1.mp3",
        fallbackUrl = "https://cdn.aladhan.com/audio/adhans/a1.mp3",
        bundledAssetPath = "audio/adhan_makkah.mp3"
    ),
    ALAFASY(
        id = "alafasy",
        arabicName = "مشاري راشد العفاسي",
        streamUrl = "https://cdn.aladhan.com/audio/adhans/a9.mp3",
        fallbackUrl = "https://cdn.aladhan.com/audio/adhans/a4.mp3",
        bundledAssetPath = "audio/adhan_alafasy.mp3"
    ),
    MADINAH(
        id = "madinah",
        arabicName = "أذان المسجد النبوي",
        streamUrl = "https://www.islamcan.com/audio/adhan/azan2.mp3",
        fallbackUrl = "https://cdn.aladhan.com/audio/adhans/a2.mp3",
        bundledAssetPath = "audio/adhan_madinah.mp3"
    ),
    SYSTEM_TONE(
        id = "tone",
        arabicName = "تنبيه نغمة الجهاز فقط",
        streamUrl = null,
        bundledAssetPath = null
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
    val virtueOrSource: String,
    val surahNumber: Int? = null,
    val ayahNumbers: List<Int> = emptyList()
)

enum class PeriodicDhikrMode(
    val id: String,
    val arabicLabel: String,
    val description: String
) {
    VOICE_AND_NOTIFICATION(
        "voice",
        "صوت القارئ + إشعار على الشاشة",
        "ينطق الذكر بالصوت مع ظهور إشعار"
    ),
    VOICE_ONLY(
        "voice_only",
        "صوت القارئ فقط (بدون إشعار)",
        "ينطق الذكر بالصوت دون إظهار إشعار على الشاشة"
    ),
    CHIME_AND_NOTIFICATION(
        "chime",
        "نغمة تنبيه + إشعار على الشاشة",
        "نغمة هادئة مع إشعار مكتوب فقط"
    ),
    SILENT_NOTIFICATION(
        "silent",
        "إشعار صامت فقط (بدون صوت)",
        "إشعار مكتوب على الشاشة بدون صوت"
    );

    val includesVoice: Boolean
        get() = this == VOICE_AND_NOTIFICATION || this == VOICE_ONLY

    val includesNotification: Boolean
        get() = this != VOICE_ONLY

    companion object {
        fun fromId(id: String): PeriodicDhikrMode =
            entries.find { it.id == id } ?: VOICE_AND_NOTIFICATION
    }
}

enum class AdhkarSheikhVoice(
    val id: String,
    val arabicName: String,
    val description: String,
    val quranReciterId: String,
    val morningSessionUrl: String? = null,
    val eveningSessionUrl: String? = null,
    val bundledMorningAsset: String? = null,
    val bundledEveningAsset: String? = null
) {
    MULTI_SHEIKHS(
        id = "sheikh_multi",
        arabicName = "تنويع تلقائي بين الشيوخ الحقيقيين",
        description = "يتنوع الصوت تلقائياً بين العفاسي والدوسري والحصري والقطامي والغامدي والمعيقلي وحمد الدريهم",
        quranReciterId = "afs",
        morningSessionUrl = "https://archive.org/download/adkar_sabah_masae_safar/2%20%D8%A3%D8%B0%D9%83%D8%A7%D8%B1%20%D8%A7%D9%84%D8%B5%D8%A8%D8%A7%D8%AD%20-%20%D8%A8%D8%B5%D9%88%D8%AA%20%D9%85%D8%B4%D8%A7%D8%B1%D9%8A%20%D8%A7%D9%84%D8%B9%D9%81%D8%A7%D8%B3%D9%8A.mp3",
        eveningSessionUrl = "https://archive.org/download/adkar_sabah_masae_safar/3%20%D8%A3%D8%B0%D9%83%D8%A7%D8%B1%20%D8%A7%D9%84%D9%85%D8%B3%D8%A7%D8%A1%20-%20%D8%A8%D8%B5%D9%88%D8%AA%20%D9%85%D8%B4%D8%A7%D8%B1%D9%8A%20%D8%A7%D9%84%D8%B9%D9%81%D8%A7%D8%B3%D9%8A.mp3",
        bundledMorningAsset = "audio/adhkar/sessions/sheikh_alafasy_morning.mp3",
        bundledEveningAsset = "audio/adhkar/sessions/sheikh_alafasy_evening.mp3"
    ),
    ALAFASY(
        id = "sheikh_alafasy",
        arabicName = "الشيخ مشاري راشد العفاسي",
        description = "أذكار الصباح والمساء كاملة + الآيات بصوت حقيقي",
        quranReciterId = "afs",
        morningSessionUrl = "https://archive.org/download/adkar_sabah_masae_safar/2%20%D8%A3%D8%B0%D9%83%D8%A7%D8%B1%20%D8%A7%D9%84%D8%B5%D8%A8%D8%A7%D8%AD%20-%20%D8%A8%D8%B5%D9%88%D8%AA%20%D9%85%D8%B4%D8%A7%D8%B1%D9%8A%20%D8%A7%D9%84%D8%B9%D9%81%D8%A7%D8%B3%D9%8A.mp3",
        eveningSessionUrl = "https://archive.org/download/adkar_sabah_masae_safar/3%20%D8%A3%D8%B0%D9%83%D8%A7%D8%B1%20%D8%A7%D9%84%D9%85%D8%B3%D8%A7%D8%A1%20-%20%D8%A8%D8%B5%D9%88%D8%AA%20%D9%85%D8%B4%D8%A7%D8%B1%D9%8A%20%D8%A7%D9%84%D8%B9%D9%81%D8%A7%D8%B3%D9%8A.mp3",
        bundledMorningAsset = "audio/adhkar/sessions/sheikh_alafasy_morning.mp3",
        bundledEveningAsset = "audio/adhkar/sessions/sheikh_alafasy_evening.mp3"
    ),
    YASSER_ALDOSSARY(
        id = "sheikh_yasser",
        arabicName = "الشيخ ياسر الدوسري",
        description = "أذكار وآيات قرآنية خاشعة بصوت الشيخ الدوسري",
        quranReciterId = "yasser"
    ),
    HUSSARY(
        id = "sheikh_hussary",
        arabicName = "الشيخ محمود خليل الحصري",
        description = "تلاوات وأدعية قرآنية متقنة بصوت الشيخ الحصري",
        quranReciterId = "husr"
    ),
    HAMAD_AL_DURAYHIM(
        id = "sheikh_hisn",
        arabicName = "الشيخ حمد الدريهم (حصن المسلم)",
        description = "كل ذكر منفرد بصوت شيخ حقيقي • مدمج بدون نت",
        quranReciterId = "afs"
    ),
    NASSER_ALQATAMI(
        id = "sheikh_qatami",
        arabicName = "الشيخ ناصر القطامي",
        description = "أذكار الصباح والمساء + تلاوة الآيات بصوته",
        quranReciterId = "qtm",
        morningSessionUrl = "https://archive.org/download/way2sona_20160320_2316/%D8%A3%D8%B0%D9%83%D8%A7%D8%B1%20%D8%A7%D9%84%D8%B5%D8%A8%D8%A7%D8%AD%20%D9%88%D8%A7%D9%84%D9%85%D8%B3%D8%A7%D8%A1%20%D8%A8%D8%B5%D9%88%D8%AA%20%D8%A7%D9%84%D8%B4%D9%8A%D8%AE%20%D9%86%D8%A7%D8%B5%D8%B1%20%D8%A7%D9%84%D9%82%D8%B7%D8%A7%D9%85%D9%8A.mp3",
        eveningSessionUrl = "https://archive.org/download/way2sona_20160320_2316/%D8%A3%D8%B0%D9%83%D8%A7%D8%B1%20%D8%A7%D9%84%D8%B5%D8%A8%D8%A7%D8%AD%20%D9%88%D8%A7%D9%84%D9%85%D8%B3%D8%A7%D8%A1%20%D8%A8%D8%B5%D9%88%D8%AA%20%D8%A7%D9%84%D8%B4%D9%8A%D8%AE%20%D9%86%D8%A7%D8%B5%D8%B1%20%D8%A7%D9%84%D9%82%D8%B7%D8%A7%D9%85%D9%8A.mp3"
    ),
    SAAD_ALGHAMDI(
        id = "sheikh_ghamdi",
        arabicName = "الشيخ سعد الغامدي",
        description = "أذكار الصباح بصوت الشيخ + تلاوة الآيات",
        quranReciterId = "sgmd",
        morningSessionUrl = "https://archive.org/download/20250607_20250607_1005/20250607_20250607_1005.mp3"
    ),
    MAHER_ALMUAIQLY(
        id = "sheikh_maher",
        arabicName = "الشيخ ماهر المعيقلي",
        description = "آيات الأذكار والأدعية القرآنية بصوت المعيقلي",
        quranReciterId = "maher"
    );

    companion object {
        fun fromId(id: String): AdhkarSheikhVoice =
            entries.find { it.id == id } ?: MULTI_SHEIKHS
    }
}
