package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.example.data.catalog.QuranCatalog
import com.example.domain.model.AdhanVoice
import com.example.domain.model.AdhkarSheikhVoice
import com.example.domain.model.ReciterInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class OfflineAudioRepository(context: Context) {

    private val appContext = context.applicationContext

    private val quranRootDir = File(appContext.filesDir, "offline_quran").apply { mkdirs() }
    private val ayahRootDir = File(appContext.filesDir, "offline_ayahs").apply { mkdirs() }
    private val adhanRootDir = File(appContext.filesDir, "offline_adhan").apply { mkdirs() }
    private val adhkarClipsDir = File(appContext.filesDir, "offline_adhkar_clips").apply { mkdirs() }
    private val adhkarSessionsDir = File(appContext.filesDir, "offline_adhkar_sessions").apply { mkdirs() }
    private val hisnIndexCacheFile = File(adhkarClipsDir, "adhkar_hisn_index.json")

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val bundledSurahsByReciter: Map<String, Set<Int>> by lazy { scanBundledSurahs() }
    private val bundledAdhanIds: Set<String> by lazy { scanBundledAdhans() }
    private val bundledAdhkarSessionKeys: Set<String> by lazy { scanBundledAdhkarSessions() }

    private val _offlineSurahsByReciter = MutableStateFlow<Map<String, Set<Int>>>(emptyMap())
    val offlineSurahsByReciter: StateFlow<Map<String, Set<Int>>> = _offlineSurahsByReciter.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val _offlineAdhanIds = MutableStateFlow<Set<String>>(emptySet())
    val offlineAdhanIds: StateFlow<Set<String>> = _offlineAdhanIds.asStateFlow()

    private val _offlineAdhkarSessionKeys = MutableStateFlow<Set<String>>(emptySet())
    val offlineAdhkarSessionKeys: StateFlow<Set<String>> = _offlineAdhkarSessionKeys.asStateFlow()

    private val _totalStorageBytes = MutableStateFlow(0L)
    val totalStorageBytes: StateFlow<Long> = _totalStorageBytes.asStateFlow()

    init {
        refreshIndex()
    }

    fun refreshIndex() {
        val combined = mutableMapOf<String, MutableSet<Int>>()
        bundledSurahsByReciter.forEach { (reciterId, set) ->
            combined.getOrPut(reciterId) { mutableSetOf() }.addAll(set)
        }

        quranRootDir.listFiles()?.forEach { reciterDir ->
            if (reciterDir.isDirectory) {
                val set = combined.getOrPut(reciterDir.name) { mutableSetOf() }
                reciterDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".mp3") && file.length() > 1024L) {
                        file.nameWithoutExtension.toIntOrNull()?.let { surahNum ->
                            set.add(surahNum)
                        }
                    }
                }
            }
        }
        _offlineSurahsByReciter.value = combined.mapValues { it.value.toSet() }

        val adhans = bundledAdhanIds.toMutableSet()
        adhanRootDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".mp3") && file.length() > 1024L) {
                adhans.add(file.nameWithoutExtension)
            }
        }
        _offlineAdhanIds.value = adhans

        val sessions = bundledAdhkarSessionKeys.toMutableSet()
        adhkarSessionsDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".mp3") && file.length() > 4096L) {
                sessions.add(file.nameWithoutExtension)
            }
        }
        _offlineAdhkarSessionKeys.value = sessions

        _totalStorageBytes.value = computeDirSize(quranRootDir) +
            computeDirSize(ayahRootDir) +
            computeDirSize(adhanRootDir) +
            computeDirSize(adhkarClipsDir) +
            computeDirSize(adhkarSessionsDir)
    }

    fun getOfflineSurahUri(reciterId: String, surahNumber: Int): Uri? {
        val localFile = File(File(quranRootDir, reciterId), "${surahNumber.toString().padStart(3, '0')}.mp3")
        if (localFile.exists() && localFile.length() > 1024L) {
            return Uri.fromFile(localFile)
        }
        val padded = surahNumber.toString().padStart(3, '0')
        val assetPath = "audio/quran/$reciterId/$padded.mp3"
        if (assetExists(assetPath)) {
            return Uri.parse("asset:///$assetPath")
        }
        return null
    }

    fun getOfflineAyahUri(reciterId: String, surahNumber: Int, ayahNumber: Int): Uri? {
        val s = surahNumber.toString().padStart(3, '0')
        val a = ayahNumber.toString().padStart(3, '0')
        val localFile = File(File(ayahRootDir, reciterId), "$s$a.mp3")
        if (localFile.exists() && localFile.length() > 512L) {
            return Uri.fromFile(localFile)
        }
        val assetPath = "audio/ayahs/$reciterId/$s$a.mp3"
        if (assetExists(assetPath)) {
            return Uri.parse("asset:///$assetPath")
        }
        return null
    }

    fun getOfflineAdhanUri(voice: AdhanVoice): Uri? {
        val localFile = File(adhanRootDir, "${voice.id}.mp3")
        if (localFile.exists() && localFile.length() > 1024L) {
            return Uri.fromFile(localFile)
        }
        val bundled = voice.bundledAssetPath
        if (bundled != null && assetExists(bundled)) {
            return Uri.parse("asset:///$bundled")
        }
        return null
    }

    fun isSurahAvailableOffline(reciterId: String, surahNumber: Int): Boolean {
        return _offlineSurahsByReciter.value[reciterId]?.contains(surahNumber) == true
    }

    fun isSurahUserDownloaded(reciterId: String, surahNumber: Int): Boolean {
        val localFile = File(File(quranRootDir, reciterId), "${surahNumber.toString().padStart(3, '0')}.mp3")
        return localFile.exists() && localFile.length() > 1024L
    }

    suspend fun downloadSurah(reciter: ReciterInfo, surahNumber: Int): Result<File> = withContext(Dispatchers.IO) {
        val padded = surahNumber.toString().padStart(3, '0')
        val reciterDir = File(quranRootDir, reciter.id).apply { mkdirs() }
        val targetFile = File(reciterDir, "$padded.mp3")
        if (targetFile.exists() && targetFile.length() > 1024L) {
            return@withContext Result.success(targetFile)
        }

        val key = progressKey(reciter.id, surahNumber)
        _downloadProgress.update { it + (key to 0.02f) }

        val urls = reciter.surahAudioUrls(surahNumber)
        var lastError: Exception? = null
        for (url in urls) {
            val result = downloadUrlToFile(url, targetFile) { progress ->
                _downloadProgress.update { it + (key to progress.coerceIn(0.02f, 0.99f)) }
            }
            if (result.isSuccess) {
                _downloadProgress.update { it - key }
                refreshIndex()
                return@withContext result
            } else {
                lastError = result.exceptionOrNull() as? Exception
            }
        }

        _downloadProgress.update { it - key }
        Result.failure(lastError ?: IllegalStateException("تعذّر تحميل السورة"))
    }

    fun getOfflineDhikrClipUri(dhikrId: String): Uri? {
        val localFile = File(adhkarClipsDir, "dhikr_$dhikrId.mp3")
        if (localFile.exists() && localFile.length() > 512L) {
            return Uri.fromFile(localFile)
        }
        val assetPath = "audio/adhkar/hisn/dhikr_$dhikrId.mp3"
        if (assetExists(assetPath)) {
            return Uri.parse("asset:///$assetPath")
        }
        return null
    }

    fun getOfflinePeriodicDhikrUri(phraseIndex: Int): Uri? {
        val localFile = File(adhkarClipsDir, "periodic_$phraseIndex.mp3")
        if (localFile.exists() && localFile.length() > 512L) {
            return Uri.fromFile(localFile)
        }
        val assetPath = "audio/adhkar/hisn/periodic_$phraseIndex.mp3"
        if (assetExists(assetPath)) {
            return Uri.parse("asset:///$assetPath")
        }
        return null
    }

    fun getOfflineAdhkarSessionUri(sheikh: AdhkarSheikhVoice, isMorning: Boolean): Uri? {
        val suffix = if (isMorning) "morning" else "evening"
        val localFile = File(adhkarSessionsDir, "${sheikh.id}_$suffix.mp3")
        if (localFile.exists() && localFile.length() > 4096L) {
            return Uri.fromFile(localFile)
        }
        val assetPath = if (isMorning) sheikh.bundledMorningAsset else sheikh.bundledEveningAsset
        if (assetPath != null && assetExists(assetPath)) {
            return Uri.parse("asset:///$assetPath")
        }
        return null
    }

    suspend fun downloadAyahClip(reciterId: String, surahNumber: Int, ayahNumber: Int): Uri? =
        withContext(Dispatchers.IO) {
            getOfflineAyahUri(reciterId, surahNumber, ayahNumber)?.let { return@withContext it }
            val reciter = QuranCatalog.reciters.find { it.id == reciterId }
                ?: return@withContext null
            val url = reciter.everyAyahUrl(surahNumber, ayahNumber) ?: return@withContext null
            val s = surahNumber.toString().padStart(3, '0')
            val a = ayahNumber.toString().padStart(3, '0')
            val reciterDir = File(ayahRootDir, reciter.id).apply { mkdirs() }
            val target = File(reciterDir, "$s$a.mp3")
            val res = downloadUrlToFile(url, target) {}
            if (res.isSuccess) {
                refreshIndex()
                Uri.fromFile(target)
            } else {
                null
            }
        }

    suspend fun fetchAndCacheHisnDhikrClip(dhikrId: String, arabicText: String): Uri? =
        withContext(Dispatchers.IO) {
            getOfflineDhikrClipUri(dhikrId)?.let { return@withContext it }
            if (!isNetworkConnected()) return@withContext null
            val audioPath = findMatchingHisnAudioPath(arabicText) ?: return@withContext null
            val target = File(adhkarClipsDir, "dhikr_$dhikrId.mp3")
            downloadHisnAudioPath(audioPath, target)
        }

    suspend fun fetchAndCachePeriodicClip(phraseIndex: Int, phraseText: String): Uri? =
        withContext(Dispatchers.IO) {
            getOfflinePeriodicDhikrUri(phraseIndex)?.let { return@withContext it }
            if (!isNetworkConnected()) return@withContext null
            val audioPath = findMatchingHisnAudioPath(phraseText) ?: return@withContext null
            val target = File(adhkarClipsDir, "periodic_$phraseIndex.mp3")
            downloadHisnAudioPath(audioPath, target)
        }

    suspend fun downloadSheikhAdhkarSession(sheikh: AdhkarSheikhVoice, isMorning: Boolean): Result<File> =
        withContext(Dispatchers.IO) {
            val suffix = if (isMorning) "morning" else "evening"
            val target = File(adhkarSessionsDir, "${sheikh.id}_$suffix.mp3")
            if (target.exists() && target.length() > 4096L) {
                return@withContext Result.success(target)
            }
            val url = (if (isMorning) sheikh.morningSessionUrl else sheikh.eveningSessionUrl)
                ?: return@withContext Result.failure(IllegalStateException("لا يوجد تسجيل جلسة لهذا الشيخ"))
            val key = "session:${sheikh.id}_$suffix"
            _downloadProgress.update { it + (key to 0.02f) }
            val res = downloadUrlToFile(url, target) { progress ->
                _downloadProgress.update { it + (key to progress.coerceIn(0.02f, 0.99f)) }
            }
            _downloadProgress.update { it - key }
            if (res.isSuccess) {
                refreshIndex()
            }
            res
        }

    private fun downloadHisnAudioPath(audioPath: String, target: File): Uri? {
        val cleanPath = if (audioPath.startsWith("/")) audioPath else "/$audioPath"
        val urls = if (audioPath.startsWith("http")) {
            listOf(audioPath)
        } else {
            listOf(
                "https://raw.githubusercontent.com/rn0x/Adhkar-json/main$cleanPath",
                "https://www.hisnmuslim.com$cleanPath"
            )
        }
        for (url in urls) {
            val res = downloadUrlToFile(url, target) {}
            if (res.isSuccess) {
                refreshIndex()
                return Uri.fromFile(target)
            }
        }
        return null
    }

    private fun findMatchingHisnAudioPath(arabicText: String): String? {
        val jsonContent = ensureHisnIndexJson() ?: return null
        return try {
            val normTarget = normalizeArabic(arabicText)
            val probe = normTarget.take(26)
            val rootArray = JSONArray(jsonContent)
            var bestAudio: String? = null
            var bestScore = 0
            for (i in 0 until rootArray.length()) {
                val categoryObj = rootArray.optJSONObject(i) ?: continue
                val items = categoryObj.optJSONArray("array") ?: continue
                for (j in 0 until items.length()) {
                    val itemObj = items.optJSONObject(j) ?: continue
                    val rawText = itemObj.optString("text", "")
                    val audio = itemObj.optString("audio", "").ifBlank { itemObj.optString("filename", "") }
                    if (rawText.isBlank() || audio.isBlank()) continue
                    val normCandidate = normalizeArabic(rawText)
                    if (normCandidate.contains(normTarget) || normTarget.contains(normCandidate)) {
                        return audio
                    }
                    if (probe.length >= 10 && normCandidate.contains(probe)) {
                        return audio
                    }
                    val words = normTarget.split(" ").filter { it.length >= 3 }.take(6)
                    val hits = words.count { normCandidate.contains(it) }
                    if (hits > bestScore && hits >= 3) {
                        bestScore = hits
                        bestAudio = audio
                    }
                }
            }
            bestAudio
        } catch (_: Exception) {
            null
        }
    }

    private fun ensureHisnIndexJson(): String? {
        if (hisnIndexCacheFile.exists() && hisnIndexCacheFile.length() > 5000L) {
            return try { hisnIndexCacheFile.readText(Charsets.UTF_8) } catch (_: Exception) { null }
        }
        return try {
            val request = Request.Builder()
                .url("https://raw.githubusercontent.com/rn0x/Adhkar-json/main/adhkar.json")
                .header("User-Agent", "NoorAlQuran-Android/1.2")
                .build()
            downloadClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bodyStr = resp.body?.string() ?: return null
                if (bodyStr.length > 5000) {
                    hisnIndexCacheFile.writeText(bodyStr, Charsets.UTF_8)
                    bodyStr
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeArabic(input: String): String {
        return input
            .replace(Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]"), "")
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ٱ", "ا")
            .replace("ى", "ي")
            .replace("ة", "ه")
            .replace(Regex("[^\\u0621-\\u064A\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    suspend fun downloadAdhanVoice(voice: AdhanVoice): Result<File> = withContext(Dispatchers.IO) {
        val target = File(adhanRootDir, "${voice.id}.mp3")
        if (target.exists() && target.length() > 1024L) {
            return@withContext Result.success(target)
        }
        val urls = listOfNotNull(voice.streamUrl, voice.fallbackUrl)
        var lastErr: Exception? = null
        for (url in urls) {
            val res = downloadUrlToFile(url, target) {}
            if (res.isSuccess) {
                refreshIndex()
                return@withContext res
            } else {
                lastErr = res.exceptionOrNull() as? Exception
            }
        }
        Result.failure(lastErr ?: IllegalStateException("تعذّر تحميل صوت الأذان"))
    }

    suspend fun deleteDownloadedSurah(reciterId: String, surahNumber: Int) = withContext(Dispatchers.IO) {
        val padded = surahNumber.toString().padStart(3, '0')
        val localFile = File(File(quranRootDir, reciterId), "$padded.mp3")
        if (localFile.exists()) {
            localFile.delete()
        }
        refreshIndex()
    }

    fun isNetworkConnected(): Boolean {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    private fun downloadUrlToFile(
        url: String,
        targetFile: File,
        onProgress: (Float) -> Unit
    ): Result<File> {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IllegalStateException("HTTP ${response.code}"))
                }
                val body = response.body ?: return Result.failure(IllegalStateException("Empty body"))
                val totalBytes = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloaded = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0L) {
                                onProgress(downloaded.toFloat() / totalBytes.toFloat())
                            }
                        }
                        output.flush()
                    }
                }
                if (tempFile.length() < 1024L) {
                    tempFile.delete()
                    return Result.failure(IllegalStateException("Invalid audio file"))
                }
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                Result.success(targetFile)
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            Result.failure(e)
        }
    }

    private fun scanBundledSurahs(): Map<String, Set<Int>> {
        val result = mutableMapOf<String, Set<Int>>()
        QuranCatalog.reciters.forEach { reciter ->
            try {
                val files = appContext.assets.list("audio/quran/${reciter.id}") ?: emptyArray()
                val numbers = files.mapNotNull { name ->
                    if (name.endsWith(".mp3")) name.removeSuffix(".mp3").toIntOrNull() else null
                }.toSet()
                if (numbers.isNotEmpty()) {
                    result[reciter.id] = numbers
                }
            } catch (_: Exception) {
            }
        }
        return result
    }

    private fun scanBundledAdhans(): Set<String> {
        return AdhanVoice.entries.mapNotNull { voice ->
            val path = voice.bundledAssetPath
            if (path != null && assetExists(path)) voice.id else null
        }.toSet()
    }

    private fun scanBundledAdhkarSessions(): Set<String> {
        val result = mutableSetOf<String>()
        AdhkarSheikhVoice.entries.forEach { sheikh ->
            sheikh.bundledMorningAsset?.let { path ->
                if (assetExists(path)) result.add("${sheikh.id}_morning")
            }
            sheikh.bundledEveningAsset?.let { path ->
                if (assetExists(path)) result.add("${sheikh.id}_evening")
            }
        }
        return result
    }

    private fun assetExists(assetPath: String): Boolean {
        return try {
            appContext.assets.openFd(assetPath).use { it.length > 512L }
        } catch (_: Exception) {
            try {
                appContext.assets.open(assetPath).use { it.available() > 0 }
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun computeDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) total += file.length()
        }
        return total
    }

    companion object {
        fun progressKey(reciterId: String, surahNumber: Int): String = "$reciterId:$surahNumber"

        val quickDownloadShortSurahs: List<Int> = (93..114).toList() + listOf(1)
        val essentialSurahs: List<Int> = listOf(1, 18, 36, 55, 56, 67, 78, 112, 113, 114)
    }
}
