package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.example.data.catalog.ScholarCatalog
import com.example.domain.model.LessonMediaType
import com.example.domain.model.LessonScholar
import com.example.domain.model.ReligiousLesson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class LessonsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val rootDir = File(appContext.filesDir, "offline_lessons").apply { mkdirs() }
    private val mediaDir = File(rootDir, "media").apply { mkdirs() }
    private val cacheFile = File(rootDir, "lessons_catalog_cache.json")

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _lessonsByScholar = MutableStateFlow<Map<String, List<ReligiousLesson>>>(emptyMap())
    val lessonsByScholar: StateFlow<Map<String, List<ReligiousLesson>>> = _lessonsByScholar.asStateFlow()

    private val _offlineLessonFiles = MutableStateFlow<Map<String, String>>(emptyMap())
    val offlineLessonFiles: StateFlow<Map<String, String>> = _offlineLessonFiles.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val _totalOfflineBytes = MutableStateFlow(0L)
    val totalOfflineBytes: StateFlow<Long> = _totalOfflineBytes.asStateFlow()

    init {
        loadCachedLessons()
        refreshOfflineFilesIndex()
    }

    fun refreshOfflineFilesIndex() {
        val map = mutableMapOf<String, String>()
        var bytes = 0L
        mediaDir.listFiles()?.forEach { file ->
            if (file.isFile && !file.name.endsWith(".tmp") && file.length() > 4096L) {
                map[file.nameWithoutExtension] = file.absolutePath
                bytes += file.length()
            }
        }
        _offlineLessonFiles.value = map
        _totalOfflineBytes.value = bytes
    }

    fun getPlayableUri(lesson: ReligiousLesson): Uri {
        val key = sanitizeId(lesson.id)
        val localPath = _offlineLessonFiles.value[key]
        if (localPath != null) {
            val file = File(localPath)
            if (file.exists() && file.length() > 4096L) {
                return Uri.fromFile(file)
            }
        }
        return Uri.parse(lesson.mediaUrl)
    }

    fun isDownloadedOffline(lessonId: String): Boolean =
        _offlineLessonFiles.value.containsKey(sanitizeId(lessonId))

    fun isNetworkConnected(): Boolean {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun syncScholarLessons(scholar: LessonScholar): List<ReligiousLesson> =
        withContext(Dispatchers.IO) {
            if (!isNetworkConnected()) {
                return@withContext _lessonsByScholar.value[scholar.id].orEmpty()
            }

            val discovered = mutableListOf<ReligiousLesson>()

            if (scholar.id == "latest") {
                // Auto-fetch newest Arabic Islamic video lessons from IslamHouse + Archive.org
                coroutineScope {
                    val ihJob = async { fetchIslamHouseLatestVideos() }
                    val iaLatestJob = async {
                        searchArchiveOrgLessons(
                            scholarId = "latest",
                            defaultScholarName = "علماء ودعاة",
                            query = scholar.archiveSearchQueries.first(),
                            sortBy = "publicdate desc",
                            rows = 12,
                            markAsNew = true
                        )
                    }
                    discovered.addAll(ihJob.await())
                    discovered.addAll(iaLatestJob.await())
                }
            } else {
                // Fetch dynamic Archive.org collections + curated fallback identifiers in parallel
                coroutineScope {
                    val queryJobs = scholar.archiveSearchQueries.map { q ->
                        async {
                            searchArchiveOrgLessons(
                                scholarId = scholar.id,
                                defaultScholarName = scholar.arabicName,
                                query = q,
                                sortBy = "downloads desc",
                                rows = 10,
                                markAsNew = false
                            )
                        }
                    }
                    val recentJobs = scholar.archiveSearchQueries.take(1).map { q ->
                        async {
                            searchArchiveOrgLessons(
                                scholarId = scholar.id,
                                defaultScholarName = scholar.arabicName,
                                query = q,
                                sortBy = "publicdate desc",
                                rows = 5,
                                markAsNew = true
                            )
                        }
                    }
                    val fallbackJobs = scholar.fallbackIdentifiers.map { identifier ->
                        async {
                            fetchLessonsFromArchiveIdentifier(
                                identifier = identifier,
                                titleHint = scholar.arabicName,
                                scholarId = scholar.id,
                                scholarName = scholar.arabicName,
                                descriptionHint = scholar.titleOrSpecialty,
                                publishedDate = "",
                                isNewArrival = false,
                                maxFiles = 12
                            )
                        }
                    }
                    (queryJobs + recentJobs + fallbackJobs).awaitAll().forEach { list ->
                        discovered.addAll(list)
                    }
                }
            }

            val existing = _lessonsByScholar.value[scholar.id].orEmpty()
            val merged = (discovered + existing)
                .distinctBy { it.mediaUrl }
                .sortedWith(
                    compareByDescending<ReligiousLesson> { it.mediaType == LessonMediaType.VIDEO }
                        .thenByDescending { it.isNewArrival }
                )
                .take(45)

            if (merged.isNotEmpty()) {
                _lessonsByScholar.update { it + (scholar.id to merged) }
                persistCachedLessons()
            }
            merged
        }

    suspend fun searchCustomScholarOrTopic(query: String): List<ReligiousLesson> =
        withContext(Dispatchers.IO) {
            if (!isNetworkConnected() || query.isBlank()) return@withContext emptyList()
            val clean = query.trim().replace("\"", "")
            val luceneQuery = "\"$clean\""
            val results = mutableListOf<ReligiousLesson>()
            coroutineScope {
                val moviesJob = async {
                    searchArchiveOrgLessons(
                        scholarId = "custom_${clean.hashCode()}",
                        defaultScholarName = clean,
                        query = luceneQuery,
                        sortBy = "downloads desc",
                        rows = 12,
                        markAsNew = false
                    )
                }
                val latestJob = async {
                    searchArchiveOrgLessons(
                        scholarId = "custom_${clean.hashCode()}",
                        defaultScholarName = clean,
                        query = luceneQuery,
                        sortBy = "publicdate desc",
                        rows = 6,
                        markAsNew = true
                    )
                }
                results.addAll(latestJob.await())
                results.addAll(moviesJob.await())
            }
            results.distinctBy { it.mediaUrl }
        }

    suspend fun downloadLesson(lesson: ReligiousLesson): Result<File> =
        withContext(Dispatchers.IO) {
            val safeKey = sanitizeId(lesson.id)
            val ext = if (lesson.mediaType == LessonMediaType.VIDEO || lesson.mediaUrl.lowercase().contains(".mp4")) {
                "mp4"
            } else {
                "mp3"
            }
            val target = File(mediaDir, "$safeKey.$ext")
            if (target.exists() && target.length() > 8192L) {
                refreshOfflineFilesIndex()
                return@withContext Result.success(target)
            }

            val temp = File(mediaDir, "$safeKey.$ext.tmp")
            _downloadProgress.update { it + (lesson.id to 0.02f) }

            return@withContext try {
                val request = Request.Builder()
                    .url(lesson.mediaUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _downloadProgress.update { it - lesson.id }
                        return@withContext Result.failure(IllegalStateException("HTTP ${response.code}"))
                    }
                    val body = response.body ?: run {
                        _downloadProgress.update { it - lesson.id }
                        return@withContext Result.failure(IllegalStateException("Empty body"))
                    }
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        FileOutputStream(temp).use { output ->
                            val buf = ByteArray(32 * 1024)
                            var downloaded = 0L
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                output.write(buf, 0, read)
                                downloaded += read
                                if (total > 0L) {
                                    _downloadProgress.update {
                                        it + (lesson.id to (downloaded.toFloat() / total.toFloat()).coerceIn(0.02f, 0.99f))
                                    }
                                }
                            }
                            output.flush()
                        }
                    }
                    if (temp.length() < 8192L) {
                        temp.delete()
                        _downloadProgress.update { it - lesson.id }
                        return@withContext Result.failure(IllegalStateException("Invalid media file"))
                    }
                    if (target.exists()) target.delete()
                    if (!temp.renameTo(target)) {
                        temp.copyTo(target, overwrite = true)
                        temp.delete()
                    }
                    _downloadProgress.update { it - lesson.id }
                    refreshOfflineFilesIndex()
                    Result.success(target)
                }
            } catch (e: Exception) {
                if (temp.exists()) temp.delete()
                _downloadProgress.update { it - lesson.id }
                Result.failure(e)
            }
        }

    suspend fun deleteDownloadedLesson(lessonId: String) = withContext(Dispatchers.IO) {
        val safeKey = sanitizeId(lessonId)
        mediaDir.listFiles()?.forEach { file ->
            if (file.nameWithoutExtension == safeKey) {
                file.delete()
            }
        }
        refreshOfflineFilesIndex()
    }

    private suspend fun searchArchiveOrgLessons(
        scholarId: String,
        defaultScholarName: String,
        query: String,
        sortBy: String,
        rows: Int,
        markAsNew: Boolean
    ): List<ReligiousLesson> = coroutineScope {
        val fullQuery = "($query) AND (mediatype:movies OR mediatype:audio)"
        val encodedQ = URLEncoder.encode(fullQuery, "UTF-8")
        val encodedSort = URLEncoder.encode(sortBy, "UTF-8")
        val searchUrl =
            "https://archive.org/advancedsearch.php?q=$encodedQ&fl[]=identifier&fl[]=title&fl[]=description&fl[]=creator&fl[]=publicdate&fl[]=mediatype&sort[]=$encodedSort&rows=$rows&page=1&output=json"

        val jsonText = fetchString(searchUrl) ?: return@coroutineScope emptyList()
        val docs = try {
            JSONObject(jsonText).optJSONObject("response")?.optJSONArray("docs") ?: return@coroutineScope emptyList()
        } catch (_: Exception) {
            return@coroutineScope emptyList()
        }

        val jobs = (0 until docs.length()).map { i ->
            async {
                val doc = docs.optJSONObject(i) ?: return@async emptyList()
                val identifier = doc.optString("identifier", "").takeIf { it.isNotBlank() }
                    ?: return@async emptyList()
                val itemTitle = doc.optString("title", "").ifBlank { defaultScholarName }
                val creator = doc.optString("creator", "").ifBlank { defaultScholarName }
                val desc = doc.optString("description", "").take(180)
                val pubDate = doc.optString("publicdate", "").take(10)

                fetchLessonsFromArchiveIdentifier(
                    identifier = identifier,
                    titleHint = itemTitle,
                    scholarId = scholarId,
                    scholarName = if (scholarId == "latest") creator else defaultScholarName,
                    descriptionHint = desc,
                    publishedDate = pubDate,
                    isNewArrival = markAsNew,
                    maxFiles = 4
                )
            }
        }
        jobs.awaitAll().flatten()
    }

    private fun fetchLessonsFromArchiveIdentifier(
        identifier: String,
        titleHint: String,
        scholarId: String,
        scholarName: String,
        descriptionHint: String,
        publishedDate: String,
        isNewArrival: Boolean,
        maxFiles: Int
    ): List<ReligiousLesson> {
        val metaUrl = "https://archive.org/metadata/$identifier/files"
        val jsonText = fetchString(metaUrl) ?: return emptyList()
        val filesArray = try {
            JSONObject(jsonText).optJSONArray("result")
                ?: JSONObject(jsonText).optJSONArray("files")
                ?: return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }

        val mp4Files = mutableListOf<JSONObject>()
        val mp3Files = mutableListOf<JSONObject>()

        for (i in 0 until filesArray.length()) {
            val f = filesArray.optJSONObject(i) ?: continue
            val name = f.optString("name", "")
            val format = f.optString("format", "").lowercase()
            val size = f.optString("size", "0").toLongOrNull() ?: 0L
            if (name.isBlank() || size < 150_000L) continue
            if (name.endsWith(".mp4", ignoreCase = true) || format.contains("h.264") || format.contains("mpeg4")) {
                mp4Files.add(f)
            } else if (name.endsWith(".mp3", ignoreCase = true) || format.contains("mp3")) {
                mp3Files.add(f)
            }
        }

        // Prefer HD MP4 videos (original or h.264), otherwise fall back to MP3 audio lectures
        val selectedFiles = if (mp4Files.isNotEmpty()) mp4Files else mp3Files
        val isVideoList = mp4Files.isNotEmpty()

        return selectedFiles
            .distinctBy { it.optString("name").substringBeforeLast('.') }
            .take(maxFiles)
            .mapNotNull { fileObj ->
                val rawName = fileObj.optString("name", "")
                if (rawName.isBlank()) return@mapNotNull null
                val encodedPath = rawName.split("/").joinToString("/") { seg ->
                    URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
                }
                val directUrl = "https://archive.org/download/$identifier/$encodedPath"
                val baseTitle = rawName.substringAfterLast('/').substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim()
                val displayTitle = if (selectedFiles.size == 1 || baseTitle.length < 4) {
                    titleHint
                } else {
                    "$titleHint — $baseTitle"
                }
                val duration = fileObj.optString("length", "0").toDoubleOrNull()?.toLong() ?: 0L
                val sizeBytes = fileObj.optString("size", "0").toLongOrNull() ?: 0L
                val matchingAudio = if (isVideoList) {
                    val stem = rawName.substringBeforeLast('.')
                    mp3Files.firstOrNull { it.optString("name").substringBeforeLast('.') == stem }
                        ?.optString("name")
                        ?.let { mp3Name ->
                            val encMp3 = mp3Name.split("/").joinToString("/") { seg ->
                                URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
                            }
                            "https://archive.org/download/$identifier/$encMp3"
                        }
                } else null

                ReligiousLesson(
                    id = "ia_${identifier}_${rawName.hashCode().toUInt()}",
                    scholarId = scholarId,
                    scholarName = scholarName,
                    title = displayTitle,
                    description = descriptionHint.ifBlank { "درس ديني مرئي وصوتي من أرشيف الإنترنت" },
                    mediaUrl = directUrl,
                    alternateAudioUrl = matchingAudio,
                    thumbnailUrl = "https://archive.org/services/img/$identifier",
                    mediaType = if (isVideoList) LessonMediaType.VIDEO else LessonMediaType.AUDIO,
                    durationSeconds = duration,
                    fileSizeBytes = sizeBytes,
                    publishedDateLabel = publishedDate,
                    sourceLabel = if (isVideoList) "فيديو HD" else "صوتي MP3",
                    isNewArrival = isNewArrival
                )
            }
    }

    private suspend fun fetchIslamHouseLatestVideos(): List<ReligiousLesson> = coroutineScope {
        val listUrl = "https://api3.islamhouse.com/v3/paV29H2gm56kvLPy/main/videos/ar/ar/1/14/json"
        val raw = fetchString(listUrl) ?: return@coroutineScope emptyList()
        val dataArray = try {
            JSONObject(raw).optJSONArray("data") ?: return@coroutineScope emptyList()
        } catch (_: Exception) {
            return@coroutineScope emptyList()
        }

        val itemJobs = (0 until dataArray.length().coerceAtMost(10)).map { i ->
            async {
                val summary = dataArray.optJSONObject(i) ?: return@async null
                val id = summary.optLong("id", 0L)
                if (id == 0L) return@async null
                val detailJson = fetchString("https://api3.islamhouse.com/v3/paV29H2gm56kvLPy/main/get-item/$id/ar/json")
                    ?: return@async null
                val obj = try {
                    JSONObject(detailJson)
                } catch (_: Exception) {
                    return@async null
                }

                val title = obj.optString("title", "").ifBlank { return@async null }
                val desc = obj.optString("description", "")
                val thumb = obj.optString("image", "").ifBlank { null }
                val preparedBy = obj.optJSONArray("prepared_by")
                val scholarName = preparedBy?.optJSONObject(0)?.optString("title", "")?.ifBlank { "علماء ودعاة" } ?: "علماء ودعاة"
                val attachments = obj.optJSONArray("attachments") ?: return@async null

                var bestMp4: String? = null
                var bestMp3: String? = null
                for (aIdx in 0 until attachments.length()) {
                    val att = attachments.optJSONObject(aIdx) ?: continue
                    val u = att.optString("url", "")
                    val ext = att.optString("extension_type", "").lowercase()
                    if (u.startsWith("http") && (ext == "mp4" || u.lowercase().contains(".mp4"))) {
                        if (bestMp4 == null) bestMp4 = u
                    } else if (u.startsWith("http") && (ext == "mp3" || u.lowercase().contains(".mp3"))) {
                        if (bestMp3 == null) bestMp3 = u
                    }
                }

                val finalUrl = bestMp4 ?: bestMp3 ?: return@async null
                val type = if (bestMp4 != null) LessonMediaType.VIDEO else LessonMediaType.AUDIO

                ReligiousLesson(
                    id = "ih_$id",
                    scholarId = "latest",
                    scholarName = scholarName,
                    title = title,
                    description = desc.ifBlank { "مادة جديدة مضافة تلقائياً عبر IslamHouse" },
                    mediaUrl = finalUrl,
                    alternateAudioUrl = bestMp3,
                    thumbnailUrl = thumb,
                    mediaType = type,
                    sourceLabel = if (type == LessonMediaType.VIDEO) "جديد • فيديو HD" else "جديد • صوتي",
                    isNewArrival = true
                )
            }
        }
        itemJobs.awaitAll().filterNotNull()
    }

    private fun fetchString(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "NoorAlQuran-Android/1.3")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadCachedLessons() {
        val map = mutableMapOf<String, MutableList<ReligiousLesson>>()
        if (cacheFile.exists()) {
            try {
                val root = JSONObject(cacheFile.readText(Charsets.UTF_8))
                val keys = root.keys()
                while (keys.hasNext()) {
                    val scholarId = keys.next()
                    val arr = root.optJSONArray(scholarId) ?: continue
                    val list = mutableListOf<ReligiousLesson>()
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { list.add(lessonFromJson(it)) }
                    }
                    if (list.isNotEmpty()) {
                        map[scholarId] = list
                    }
                }
            } catch (_: Exception) {
            }
        }

        // Seed bundled starter lessons for scholars not cached yet so first open is never empty
        val latestList = map.getOrPut("latest") { mutableListOf() }
        ScholarCatalog.starterVerifiedLessons.forEach { starter ->
            val scholarBucket = map.getOrPut(starter.scholarId) { mutableListOf() }
            if (scholarBucket.none { it.mediaUrl == starter.mediaUrl }) {
                scholarBucket.add(starter)
            }
            if (latestList.none { it.mediaUrl == starter.mediaUrl }) {
                latestList.add(starter)
            }
        }

        _lessonsByScholar.value = map.mapValues { it.value.toList() }
    }

    private fun persistCachedLessons() {
        try {
            val root = JSONObject()
            _lessonsByScholar.value.forEach { (scholarId, list) ->
                val arr = JSONArray()
                list.forEach { arr.put(lessonToJson(it)) }
                root.put(scholarId, arr)
            }
            cacheFile.writeText(root.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    private fun lessonToJson(l: ReligiousLesson): JSONObject = JSONObject().apply {
        put("id", l.id)
        put("scholarId", l.scholarId)
        put("scholarName", l.scholarName)
        put("title", l.title)
        put("description", l.description)
        put("mediaUrl", l.mediaUrl)
        put("alternateAudioUrl", l.alternateAudioUrl ?: "")
        put("thumbnailUrl", l.thumbnailUrl ?: "")
        put("mediaType", l.mediaType.name)
        put("durationSeconds", l.durationSeconds)
        put("fileSizeBytes", l.fileSizeBytes)
        put("publishedDateLabel", l.publishedDateLabel)
        put("sourceLabel", l.sourceLabel)
        put("isNewArrival", l.isNewArrival)
    }

    private fun lessonFromJson(o: JSONObject): ReligiousLesson {
        val typeName = o.optString("mediaType", LessonMediaType.VIDEO.name)
        val type = runCatching { LessonMediaType.valueOf(typeName) }.getOrDefault(LessonMediaType.VIDEO)
        return ReligiousLesson(
            id = o.optString("id"),
            scholarId = o.optString("scholarId"),
            scholarName = o.optString("scholarName"),
            title = o.optString("title"),
            description = o.optString("description"),
            mediaUrl = o.optString("mediaUrl"),
            alternateAudioUrl = o.optString("alternateAudioUrl").takeIf { it.isNotBlank() },
            thumbnailUrl = o.optString("thumbnailUrl").takeIf { it.isNotBlank() },
            mediaType = type,
            durationSeconds = o.optLong("durationSeconds", 0L),
            fileSizeBytes = o.optLong("fileSizeBytes", 0L),
            publishedDateLabel = o.optString("publishedDateLabel", ""),
            sourceLabel = o.optString("sourceLabel", "Archive.org"),
            isNewArrival = o.optBoolean("isNewArrival", false)
        )
    }

    private fun sanitizeId(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_-]"), "_")

    companion object {
        fun formatDuration(seconds: Long): String {
            if (seconds <= 0L) return ""
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            return if (h > 0) {
                String.format("%d:%02d:%02d", h, m, s)
            } else {
                String.format("%02d:%02d", m, s)
            }
        }

        fun formatSizeMb(bytes: Long): String {
            if (bytes <= 0L) return ""
            val mb = bytes / (1024.0 * 1024.0)
            return if (mb >= 1024.0) {
                String.format("%.1f ج.ب", mb / 1024.0)
            } else {
                String.format("%.1f م.ب", mb)
            }
        }
    }
}
