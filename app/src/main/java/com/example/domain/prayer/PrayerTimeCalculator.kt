package com.example.domain.prayer

import com.example.domain.model.CalculationMethod
import com.example.domain.model.DailyPrayerSchedule
import com.example.domain.model.PrayerName
import com.example.domain.model.PrayerTimeItem
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

object PrayerTimeCalculator {

    private const val KAABA_LAT = 21.422487
    private const val KAABA_LNG = 39.826206

    fun calculateSchedule(
        cityName: String,
        latitude: Double,
        longitude: Double,
        timeZoneId: String,
        method: CalculationMethod,
        enabledPrayers: Map<PrayerName, Boolean>,
        nowMillis: Long = System.currentTimeMillis()
    ): DailyPrayerSchedule {
        val tz = TimeZone.getTimeZone(timeZoneId)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = nowMillis }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val tzOffsetHours = tz.getOffset(nowMillis) / 3_600_000.0

        val rawTimes = computeDayHours(year, month, day, latitude, longitude, tzOffsetHours, method)
        val items = rawTimes.map { (prayer, hours) ->
            val (h, m) = hoursToHourMinute(hours)
            val prayerCal = Calendar.getInstance(tz).apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            PrayerTimeItem(
                prayer = prayer,
                hour = h,
                minute = m,
                formattedTime = format12HourArabic(h, m),
                epochMillis = prayerCal.timeInMillis,
                isAdhanEnabled = enabledPrayers[prayer] ?: prayer.isObligatoryAdhan
            )
        }

        // Determine next prayer (or tomorrow's Fajr if after Isha)
        val upcomingToday = items.firstOrNull { it.epochMillis > nowMillis }
        val nextPrayerItem: PrayerTimeItem
        val finalItems: List<PrayerTimeItem>

        if (upcomingToday != null) {
            nextPrayerItem = upcomingToday.copy(isNext = true)
            finalItems = items.map { if (it.prayer == upcomingToday.prayer) it.copy(isNext = true) else it }
        } else {
            val tomorrowCal = Calendar.getInstance(tz).apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, 1)
            }
            val tomorrowRaw = computeDayHours(
                tomorrowCal.get(Calendar.YEAR),
                tomorrowCal.get(Calendar.MONTH) + 1,
                tomorrowCal.get(Calendar.DAY_OF_MONTH),
                latitude,
                longitude,
                tzOffsetHours,
                method
            )
            val fajrHours = tomorrowRaw.first { it.first == PrayerName.FAJR }.second
            val (fh, fm) = hoursToHourMinute(fajrHours)
            tomorrowCal.set(Calendar.HOUR_OF_DAY, fh)
            tomorrowCal.set(Calendar.MINUTE, fm)
            tomorrowCal.set(Calendar.SECOND, 0)
            tomorrowCal.set(Calendar.MILLISECOND, 0)
            nextPrayerItem = PrayerTimeItem(
                prayer = PrayerName.FAJR,
                hour = fh,
                minute = fm,
                formattedTime = format12HourArabic(fh, fm),
                epochMillis = tomorrowCal.timeInMillis,
                isAdhanEnabled = enabledPrayers[PrayerName.FAJR] ?: true,
                isNext = true
            )
            finalItems = items
        }

        return DailyPrayerSchedule(
            cityName = cityName,
            hijriDate = getArabicHijriDate(cal),
            gregorianDate = getArabicGregorianDate(cal),
            qiblaDegrees = calculateQiblaBearing(latitude, longitude),
            items = finalItems,
            nextPrayer = nextPrayerItem
        )
    }

    fun calculateQiblaBearing(latDeg: Double, lngDeg: Double): Double {
        val phiK = Math.toRadians(KAABA_LAT)
        val lambdaK = Math.toRadians(KAABA_LNG)
        val phi = Math.toRadians(latDeg)
        val lambda = Math.toRadians(lngDeg)
        val y = sin(lambdaK - lambda)
        val x = cos(phi) * tan(phiK) - sin(phi) * cos(lambdaK - lambda)
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360.0) % 360.0
    }

    private fun computeDayHours(
        year: Int,
        month: Int,
        day: Int,
        lat: Double,
        lng: Double,
        tzOffset: Double,
        method: CalculationMethod
    ): List<Pair<PrayerName, Double>> {
        val jd = julianDate(year, month, day) - lng / (15.0 * 24.0)
        val d = jd - 2451545.0

        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sinDeg(g) + 0.020 * sinDeg(2 * g))
        val e = 23.439 - 0.00000036 * d

        val ra = atan2Deg(cosDeg(e) * sinDeg(l), cosDeg(l)) / 15.0
        val decl = asinDeg(sinDeg(e) * sinDeg(l))
        val eqt = q / 15.0 - fixHour(ra)

        val dhuhr = 12.0 + tzOffset - lng / 15.0 - eqt
        val sunrise = dhuhr - sunAngleTime(0.833, lat, decl)
        val sunset = dhuhr + sunAngleTime(0.833, lat, decl)
        val fajr = dhuhr - sunAngleTime(method.fajrAngle, lat, decl)

        // Shafi'i / Standard Asr (shadow factor = 1): sun altitude above horizon is positive, so depression angle is negative
        val asrDepressionAngle = -atanDeg(1.0 / (1.0 + tanDeg(abs(lat - decl))))
        val asr = dhuhr + sunAngleTime(asrDepressionAngle, lat, decl)

        val maghrib = sunset
        val isha = if (method.ishaMinutesAfterMaghrib != null) {
            maghrib + method.ishaMinutesAfterMaghrib / 60.0
        } else {
            dhuhr + sunAngleTime(method.ishaAngle, lat, decl)
        }

        return listOf(
            PrayerName.FAJR to fajr,
            PrayerName.SUNRISE to sunrise,
            PrayerName.DHUHR to dhuhr,
            PrayerName.ASR to asr,
            PrayerName.MAGHRIB to maghrib,
            PrayerName.ISHA to isha
        )
    }

    private fun sunAngleTime(angleDeg: Double, lat: Double, decl: Double): Double {
        val num = -sinDeg(angleDeg) - sinDeg(lat) * sinDeg(decl)
        val den = cosDeg(lat) * cosDeg(decl)
        val ratio = (num / den).coerceIn(-1.0, 1.0)
        return acosDeg(ratio) / 15.0
    }

    private fun julianDate(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun hoursToHourMinute(hoursRaw: Double): Pair<Int, Int> {
        val hFixed = fixHour(hoursRaw + 0.5 / 60.0)
        val h = floor(hFixed).toInt().coerceIn(0, 23)
        val m = floor((hFixed - h) * 60.0).toInt().coerceIn(0, 59)
        return h to m
    }

    fun format12HourArabic(hour24: Int, minute: Int): String {
        val period = if (hour24 < 12) "ص" else "م"
        val hour12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        val hStr = hour12.toString().padStart(2, '0')
        val mStr = minute.toString().padStart(2, '0')
        return "${toArabicDigits("$hStr:$mStr")} $period"
    }

    fun formatCountdown(diffMillis: Long): String {
        val totalSeconds = (diffMillis / 1000L).coerceAtLeast(0L)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        val raw = String.format("%02d:%02d:%02d", h, m, s)
        return toArabicDigits(raw)
    }

    fun toArabicDigits(input: String): String {
        val map = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        return buildString(input.length) {
            for (ch in input) {
                if (ch in '0'..'9') append(map[ch - '0']) else append(ch)
            }
        }
    }

    fun toArabicDigits(number: Int): String = toArabicDigits(number.toString())

    private fun getArabicGregorianDate(cal: Calendar): String {
        val days = arrayOf(
            "الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"
        )
        val months = arrayOf(
            "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
            "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
        )
        val dayName = days[(cal.get(Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6)]
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = months[cal.get(Calendar.MONTH).coerceIn(0, 11)]
        val year = cal.get(Calendar.YEAR)
        return "$dayName ${toArabicDigits(day)} $month ${toArabicDigits(year)} م"
    }

    private fun getArabicHijriDate(cal: Calendar): String {
        val jd = julianDate(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        ) + 0.5
        val iYear = 10631.0 / 30.0
        val epochAstro = 1948084.0
        val shift1 = 8.01 / 60.0
        var z = floor(jd) - epochAstro
        val cyc = floor(z / 10631.0)
        z -= 10631.0 * cyc
        val j = floor((z - shift1) / iYear)
        val iy = (30 * cyc + j).toInt()
        z -= floor(j * iYear + shift1)
        var im = floor((z + 28.5001) / 29.5).toInt()
        if (im == 13) im = 12
        val id = (z - floor(29.5001 * im - 29.0)).toInt().coerceIn(1, 30)

        val hijriMonths = arrayOf(
            "محرم", "صفر", "ربيع الأول", "ربيع الآخر",
            "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان",
            "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
        )
        val monthName = hijriMonths[(im - 1).coerceIn(0, 11)]
        return "${toArabicDigits(id)} $monthName ${toArabicDigits(iy)} هـ"
    }

    private fun fixAngle(a: Double): Double = a - 360.0 * floor(a / 360.0)
    private fun fixHour(a: Double): Double = a - 24.0 * floor(a / 24.0)
    private fun sinDeg(d: Double): Double = sin(d * PI / 180.0)
    private fun cosDeg(d: Double): Double = cos(d * PI / 180.0)
    private fun tanDeg(d: Double): Double = tan(d * PI / 180.0)
    private fun asinDeg(x: Double): Double = asin(x.coerceIn(-1.0, 1.0)) * 180.0 / PI
    private fun acosDeg(x: Double): Double = acos(x.coerceIn(-1.0, 1.0)) * 180.0 / PI
    private fun atanDeg(x: Double): Double = atan(x) * 180.0 / PI
    private fun atan2Deg(y: Double, x: Double): Double = atan2(y, x) * 180.0 / PI
}
