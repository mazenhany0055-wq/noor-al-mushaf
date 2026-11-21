package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Xml
import com.example.BuildConfig
import com.example.data.catalog.ScholarCatalog
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
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class LessonsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val rootDir = File(appContext.filesDir, "offline_lessons").apply { mkdirs() }
    private val cacheFile = File(rootDir, "youtube_official_lessons_v3.json")
    private val blacklistFile = File(rootDir, "broken_video_blacklist.json")
    private val favoritesFile = File(rootDir, "favorite_lessons.json")
    private val handleCacheFile = File(rootDir, "channel_id_cache.json")

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _lessonsByScholar = MutableStateFlow<Map<String, List<ReligiousLesson>>>(emptyMap())
    val lessonsByScholar: StateFlow<Map<String, List<ReligiousLesson>>> = _lessonsByScholar.asStateFlow()

    private val _favoriteVideoIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteVideoIds: StateFlow<Set<String>> = _favoriteVideoIds.asStateFlow()

    private val blacklistedVideoIds = mutableSetOf<String>()
    private val resolvedHandleCache = mutableMapOf<String, String>()

    init {
        purgeLegacyNonYouTubeFiles()
        loadBlacklist()
        loadHandleCache()
        loadFavorites()
        loadCachedLessons()
    }

    fun hasConfiguredYouTubeApiKey(): Boolean = validApiKeyOrNull() != null

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

    fun toggleFavorite(youtubeVideoId: String) {
        _favoriteVideoIds.update { cur ->
            val next = if (cur.contains(youtubeVideoId)) cur - youtubeVideoId else cur + youtubeVideoId
            persistStringSet(favoritesFile, next)
            next
        }
    }

    suspend fun removeAndBlacklistLesson(lesson: ReligiousLesson) = withContext(Dispatchers.IO) {
        synchronized(blacklistedVideoIds) {
            blacklistedVideoIds.add(lesson.youtubeVideoId)
            persistStringSet(blacklistFile, blacklistedVideoIds)
        }
        _lessonsByScholar.update { currentMap ->
            currentMap.mapValues { (_, list) ->
                list.filterNot { it.youtubeVideoId == lesson.youtubeVideoId }
            }
        }
        _favoriteVideoIds.update { cur ->
            if (cur.contains(lesson.youtubeVideoId)) {
                val next = cur - lesson.youtubeVideoId
                persistStringSet(favoritesFile, next)
                next
            } else cur
        }
        persistCachedLessons()
    }

    suspend fun syncScholarLessons(scholar: LessonScholar): List<ReligiousLesson> =
        withContext(Dispatchers.IO) {
            if (!isNetworkConnected()) {
                return@withContext _lessonsByScholar.value[scholar.id].orEmpty()
            }

            if (scholar.id == "latest") {
                // Auto-sync newest videos across the primary official scholar channels
                val coreScholars = ScholarCatalog.scholars.filter { it.id != "latest" }
                val fetched = coroutineScope {
                    coreScholars.map { s ->
                        async { fetchOfficialVideosForScholar(s, perChannelLimit = 4, markNewest = true) }
                    }.awaitAll().flatten()
                }
                val clean = filterAndDedupe(fetched)
                    .sortedByDescending { it.publishedDateLabel }
                    .take(40)
                if (clean.isNotEmpty()) {
                    _lessonsByScholar.update { it + ("latest" to clean) }
                    persistCachedLessons()
                }
                return@withContext _lessonsByScholar.value["latest"].orEmpty()
            }

            val fetched = fetchOfficialVideosForScholar(scholar, perChannelLimit = 14, markNewest = false)
            val existing = _lessonsByScholar.value[scholar.id].orEmpty()
            val merged = filterAndDedupe(fetched + existing)
                .sortedWith(
                    compareByDescending<ReligiousLesson> { it.isNewArrival }
                        .thenByDescending { it.publishedDateLabel }
                )
                .take(40)

            if (merged.isNotEmpty()) {
                _lessonsByScholar.update { it + (scholar.id to merged) }
                persistCachedLessons()
            }
            merged
        }

    suspend fun searchYouTubeOfficialLessons(query: String): List<ReligiousLesson> =
        withContext(Dispatchers.IO) {
            val cleanQuery = query.trim()
            if (cleanQuery.isBlank()) return@withContext emptyList()

            if (!isNetworkConnected()) {
                return@withContext searchLocalCached(cleanQuery)
            }

            val apiKey = validApiKeyOrNull() ?: return@withContext searchLocalCached(cleanQuery)
            val encodedQ = URLEncoder.encode(cleanQuery, "UTF-8")
            val searchUrl =
                "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&videoEmbeddable=true&videoSyndicated=true&relevanceLanguage=ar&safeSearch=strict&maxResults=15&q=$encodedQ&key=$apiKey"
            val raw = fetchString(searchUrl) ?: return@withContext searchLocalCached(cleanQuery)

            val candidateIds = extractVideoIdsFromSearchJson(raw)
            if (candidateIds.isEmpty()) return@withContext emptyList()

            val verified = verifyVideosWithDataApi(
                videoIds = candidateIds,
                apiKey = apiKey,
                scholarId = "search_${cleanQuery.hashCode()}",
                fallbackScholarName = cleanQuery,
                requireVisualFilter = false,
                markNewest = false
            )
            filterAndDedupe(verified)
        }

    private suspend fun fetchOfficialVideosForScholar(
        scholar: LessonScholar,
        perChannelLimit: Int,
        markNewest: Boolean
    ): List<ReligiousLesson> = coroutineScope {
        val apiKey = validApiKeyOrNull()

        // 1. Resolve all official channel IDs (both hardcoded UC... and @handles)
        val channelIds = mutableSetOf<String>()
        channelIds.addAll(scholar.officialChannelIds.filter { isValidChannelId(it) })
        val handleJobs = scholar.officialHandles.map { handle ->
            async { resolveChannelIdFromHandle(handle, apiKey) }
        }
        handleJobs.awaitAll().filterNotNull().forEach { channelIds.add(it) }

        val collected = mutableListOf<ReligiousLesson>()

        // 2. Fetch directly from the official channels' upload playlists via YouTube Data API v3
        if (apiKey != null && channelIds.isNotEmpty()) {
            val channelJobs = channelIds.map { cid ->
                async {
                    val uploadsPlaylistId = "UU" + cid.removePrefix("UC")
                    val playlistUrl =
                        "https://www.googleapis.com/youtube/v3/playlistItems?part=snippet,contentDetails&playlistId=$uploadsPlaylistId&maxResults=${perChannelLimit.coerceIn(1, 20)}&key=$apiKey"
                    val json = fetchString(playlistUrl) ?: return@async emptyList()
                    val ids = extractVideoIdsFromPlaylistJson(json)
                    if (ids.isEmpty()) return@async emptyList()
                    verifyVideosWithDataApi(
                        videoIds = ids,
                        apiKey = apiKey,
                        scholarId = scholar.id,
                        fallbackScholarName = scholar.arabicName,
                        requireVisualFilter = scholar.requireVisualAppearanceFilter,
                        markNewest = markNewest
                    )
                }
            }
            collected.addAll(channelJobs.awaitAll().flatten())

            // For scholars who also need query refinement (e.g. Hussary rare TV broadcasts), query YouTube Data API scoped & verified
            if (collected.size < 4 && scholar.youtubeVideoSearchQueries.isNotEmpty()) {
                val queryJobs = scholar.youtubeVideoSearchQueries.take(1).map { q ->
                    async {
                        val encodedQ = URLEncoder.encode(q, "UTF-8")
                        val url =
                            "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&videoEmbeddable=true&videoSyndicated=true&relevanceLanguage=ar&maxResults=10&q=$encodedQ&key=$apiKey"
                        val raw = fetchString(url) ?: return@async emptyList()
                        val ids = extractVideoIdsFromSearchJson(raw)
                        if (ids.isEmpty()) return@async emptyList()
                        verifyVideosWithDataApi(
                            videoIds = ids,
                            apiKey = apiKey,
                            scholarId = scholar.id,
                            fallbackScholarName = scholar.arabicName,
                            requireVisualFilter = scholar.requireVisualAppearanceFilter,
                            markNewest = markNewest
                        )
                    }
                }
                collected.addAll(queryJobs.awaitAll().flatten())
            }
        }

        // 3. Fallback when YOUTUBE_API_KEY is missing/exhausted: Official YouTube Channel RSS Feeds + live YouTube oEmbed embeddability check
        if (collected.isEmpty() && channelIds.isNotEmpty()) {
            val rssJobs = channelIds.map { cid ->
                async {
                    fetchFromOfficialChannelAtomFeed(
                        channelId = cid,
                        scholar = scholar,
                        limit = perChannelLimit,
                        markNewest = markNewest
                    )
                }
            }
            collected.addAll(rssJobs.awaitAll().flatten())
        }

        filterAndDedupe(collected)
    }

    private suspend fun verifyVideosWithDataApi(
        videoIds: List<String>,
        apiKey: String,
        scholarId: String,
        fallbackScholarName: String,
        requireVisualFilter: Boolean,
        markNewest: Boolean
    ): List<ReligiousLesson> = withContext(Dispatchers.IO) {
        val cleanIds = videoIds
            .filter { YOUTUBE_VIDEO_ID_REGEX.matches(it) && !isBlacklisted(it) }
            .distinct()
            .take(25)
        if (cleanIds.isEmpty()) return@withContext emptyList()

        val videosUrl =
            "https://www.googleapis.com/youtube/v3/videos?part=snippet,contentDetails,status&id=${cleanIds.joinToString(",")}&key=$apiKey"
        val raw = fetchString(videosUrl) ?: return@withContext emptyList()
        val items = try {
            JSONObject(raw).optJSONArray("items") ?: return@withContext emptyList()
        } catch (_: Exception) {
            return@withContext emptyList()
        }

        val verified = mutableListOf<ReligiousLesson>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val videoId = item.optString("id", "")
            if (!YOUTUBE_VIDEO_ID_REGEX.matches(videoId) || isBlacklisted(videoId)) continue

            val status = item.optJSONObject("status") ?: continue
            if (!status.optBoolean("embeddable", false)) continue
            if (status.optString("privacyStatus", "") != "public") continue
            val uploadStatus = status.optString("uploadStatus", "processed")
            if (uploadStatus != "processed" && uploadStatus.isNotBlank()) continue

            val snippet = item.optJSONObject("snippet") ?: continue
            val title = snippet.optString("title", "").trim()
            val description = snippet.optString("description", "").trim()
            val channelId = snippet.optString("channelId", "")
            val channelTitle = snippet.optString("channelTitle", fallbackScholarName)
            val publishedAt = snippet.optString("publishedAt", "").take(10)

            val contentDetails = item.optJSONObject("contentDetails")
            val durationSec = parseIsoDurationSeconds(contentDetails?.optString("duration", "") ?: "")
            // Reject YouTube Shorts, bumper clips, or broken 0s uploads
            if (durationSec in 1L until 90L || durationSec == 0L) continue

            if (isLikelyAiOrStaticOrNonSpeaking(title, description, requireVisualFilter)) continue

            val thumbs = snippet.optJSONObject("thumbnails")
            val thumbUrl = thumbs?.optJSONObject("high")?.optString("url")
                ?: thumbs?.optJSONObject("medium")?.optString("url")
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

            verified.add(
                ReligiousLesson(
                    id = "yt_$videoId",
                    youtubeVideoId = videoId,
                    scholarId = scholarId,
                    scholarName = fallbackScholarName.ifBlank { channelTitle },
                    channelId = channelId,
                    channelTitle = channelTitle,
                    title = title,
                    description = description.take(220),
                    thumbnailUrl = thumbUrl,
                    durationSeconds = durationSec,
                    publishedDateLabel = publishedAt,
                    isVerifiedOfficial = true,
                    isNewArrival = markNewest
                )
            )
        }
        verified
    }

    private suspend fun fetchFromOfficialChannelAtomFeed(
        channelId: String,
        scholar: LessonScholar,
        limit: Int,
        markNewest: Boolean
    ): List<ReligiousLesson> = coroutineScope {
        val feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
        val xmlText = fetchString(feedUrl) ?: return@coroutineScope emptyList()
        val parsed = parseYouTubeAtomFeed(xmlText, channelId, scholar, markNewest)
            .filterNot { isBlacklisted(it.youtubeVideoId) }
            .filterNot { isLikelyAiOrStaticOrNonSpeaking(it.title, it.description, scholar.requireVisualAppearanceFilter) }
            .take(limit)

        // Verify live embeddability + public availability via YouTube official oEmbed
        parsed.map { candidate ->
            async {
                if (verifyVideoEmbeddableViaOEmbed(candidate.youtubeVideoId)) candidate else null
            }
        }.awaitAll().filterNotNull()
    }

    private fun parseYouTubeAtomFeed(
        xmlText: String,
        channelId: String,
        scholar: LessonScholar,
        markNewest: Boolean
    ): List<ReligiousLesson> {
        val list = mutableListOf<ReligiousLesson>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xmlText))

            var inEntry = false
            var channelAuthor = scholar.arabicName
            var videoId = ""
            var title = ""
            var published = ""
            var description = ""
            var thumbUrl = ""

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tag) {
                            "entry" -> {
                                inEntry = true
                                videoId = ""
                                title = ""
                                published = ""
                                description = ""
                                thumbUrl = ""
                            }
                            "yt:videoId" -> if (inEntry) videoId = safeNextText(parser).trim()
                            "title" -> if (inEntry) title = safeNextText(parser).trim()
                            "published" -> if (inEntry) published = safeNextText(parser).trim().take(10)
                            "media:description" -> if (inEntry) description = safeNextText(parser).trim().take(220)
                            "media:thumbnail" -> if (inEntry) {
                                thumbUrl = parser.getAttributeValue(null, "url") ?: ""
                            }
                            "name" -> if (!inEntry) {
                                channelAuthor = safeNextText(parser).trim().ifBlank { scholar.arabicName }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tag == "entry" && inEntry) {
                            inEntry = false
                            if (YOUTUBE_VIDEO_ID_REGEX.matches(videoId) && title.isNotBlank()) {
                                list.add(
                                    ReligiousLesson(
                                        id = "yt_$videoId",
                                        youtubeVideoId = videoId,
                                        scholarId = scholar.id,
                                        scholarName = scholar.arabicName,
                                        channelId = channelId,
                                        channelTitle = channelAuthor,
                                        title = title,
                                        description = description,
                                        thumbnailUrl = thumbUrl.ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" },
                                        durationSeconds = 0L,
                                        publishedDateLabel = published,
                                        isVerifiedOfficial = true,
                                        isNewArrival = markNewest
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
        }
        return list
    }

    private fun safeNextText(parser: XmlPullParser): String = try {
        parser.nextText() ?: ""
    } catch (_: Exception) {
        ""
    }

    private fun verifyVideoEmbeddableViaOEmbed(videoId: String): Boolean {
        return try {
            val oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
            val req = Request.Builder()
                .url(oembedUrl)
                .header("User-Agent", "NoorAlQuran-Android/1.3")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val body = resp.body?.string() ?: return false
                val obj = JSONObject(body)
                obj.optString("html", "").contains(videoId)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveChannelIdFromHandle(rawHandle: String, apiKey: String?): String? {
        val cleanHandle = rawHandle.trim().removePrefix("@")
        if (cleanHandle.isBlank()) return null

        synchronized(resolvedHandleCache) {
            resolvedHandleCache[cleanHandle]?.let { if (isValidChannelId(it)) return it }
        }

        // Method 1: YouTube Data API v3 channels?forHandle
        if (apiKey != null) {
            val encodedHandle = URLEncoder.encode(cleanHandle, "UTF-8")
            val url =
                "https://www.googleapis.com/youtube/v3/channels?part=id&forHandle=$encodedHandle&key=$apiKey"
            val json = fetchString(url)
            if (json != null) {
                try {
                    val items = JSONObject(json).optJSONArray("items")
                    val cid = items?.optJSONObject(0)?.optString("id", "")
                    if (cid != null && isValidChannelId(cid)) {
                        saveResolvedHandle(cleanHandle, cid)
                        return cid
                    }
                } catch (_: Exception) {
                }
            }
        }

        // Method 2: Official YouTube channel page canonical channel ID extraction
        val encodedPath = URLEncoder.encode(cleanHandle, "UTF-8").replace("+", "%20")
        val html = fetchString("https://www.youtube.com/@$encodedPath") ?: return null
        val matcher = CHANNEL_ID_EXTRACTOR.matcher(html)
        if (matcher.find()) {
            val cid = matcher.group(1)
            if (cid != null && isValidChannelId(cid)) {
                saveResolvedHandle(cleanHandle, cid)
                return cid
            }
        }
        return null
    }

    private fun saveResolvedHandle(handle: String, channelId: String) {
        synchronized(resolvedHandleCache) {
            resolvedHandleCache[handle] = channelId
            try {
                val obj = JSONObject()
                resolvedHandleCache.forEach { (k, v) -> obj.put(k, v) }
                handleCacheFile.writeText(obj.toString(), Charsets.UTF_8)
            } catch (_: Exception) {
            }
        }
    }

    private fun extractVideoIdsFromPlaylistJson(json: String): List<String> {
        return try {
            val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
            (0 until items.length()).mapNotNull { i ->
                val obj = items.optJSONObject(i) ?: return@mapNotNull null
                val vid = obj.optJSONObject("contentDetails")?.optString("videoId")
                    ?: obj.optJSONObject("snippet")?.optJSONObject("resourceId")?.optString("videoId")
                vid?.takeIf { YOUTUBE_VIDEO_ID_REGEX.matches(it) }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractVideoIdsFromSearchJson(json: String): List<String> {
        return try {
            val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
            (0 until items.length()).mapNotNull { i ->
                val vid = items.optJSONObject(i)?.optJSONObject("id")?.optString("videoId")
                vid?.takeIf { YOUTUBE_VIDEO_ID_REGEX.matches(it) }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun searchLocalCached(query: String): List<ReligiousLesson> {
        val q = query.trim()
        return _lessonsByScholar.value.values
            .flatten()
            .filter {
                !isBlacklisted(it.youtubeVideoId) &&
                    (it.title.contains(q, ignoreCase = true) ||
                        it.scholarName.contains(q, ignoreCase = true) ||
                        it.description.contains(q, ignoreCase = true))
            }
            .distinctBy { it.youtubeVideoId }
    }

    private fun filterAndDedupe(list: List<ReligiousLesson>): List<ReligiousLesson> {
        return list
            .filter { YOUTUBE_VIDEO_ID_REGEX.matches(it.youtubeVideoId) }
            .filterNot { isBlacklisted(it.youtubeVideoId) }
            .filterNot { isLikelyAiOrStaticOrNonSpeaking(it.title, it.description, false) }
            .distinctBy { it.youtubeVideoId }
    }

    private fun isBlacklisted(videoId: String): Boolean = synchronized(blacklistedVideoIds) {
        blacklistedVideoIds.contains(videoId)
    }

    private fun validApiKeyOrNull(): String? {
        val raw = BuildConfig.YOUTUBE_API_KEY.trim()
        if (raw.isBlank() ||
            raw.equals("REPLACE_WITH_YOUR_YOUTUBE_API_KEY", ignoreCase = true) ||
            raw.equals("YOUR_API_KEY", ignoreCase = true)
        ) {
            return null
        }
        return raw
    }

    private fun isValidChannelId(cid: String): Boolean =
        cid.startsWith("UC") && cid.length >= 22

    private fun fetchString(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36")
                .header("Accept-Language", "ar,en-US;q=0.9,en;q=0.8")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun purgeLegacyNonYouTubeFiles() {
        try {
            File(rootDir, "lessons_catalog_cache.json").takeIf { it.exists() }?.delete()
            File(rootDir, "media").takeIf { it.exists() }?.deleteRecursively()
        } catch (_: Exception) {
        }
    }

    private fun loadBlacklist() {
        if (!blacklistFile.exists()) return
        try {
            val arr = JSONArray(blacklistFile.readText(Charsets.UTF_8))
            synchronized(blacklistedVideoIds) {
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let { blacklistedVideoIds.add(it) }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun loadFavorites() {
        if (!favoritesFile.exists()) return
        try {
            val arr = JSONArray(favoritesFile.readText(Charsets.UTF_8))
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                arr.optString(i).takeIf { it.isNotBlank() }?.let { set.add(it) }
            }
            _favoriteVideoIds.value = set
        } catch (_: Exception) {
        }
    }

    private fun loadHandleCache() {
        if (!handleCacheFile.exists()) return
        try {
            val obj = JSONObject(handleCacheFile.readText(Charsets.UTF_8))
            val keys = obj.keys()
            synchronized(resolvedHandleCache) {
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = obj.optString(k)
                    if (isValidChannelId(v)) resolvedHandleCache[k] = v
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun persistStringSet(file: File, set: Set<String>) {
        try {
            val arr = JSONArray()
            set.forEach { arr.put(it) }
            file.writeText(arr.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    private fun loadCachedLessons() {
        if (!cacheFile.exists()) return
        val map = mutableMapOf<String, List<ReligiousLesson>>()
        try {
            val root = JSONObject(cacheFile.readText(Charsets.UTF_8))
            val keys = root.keys()
            while (keys.hasNext()) {
                val scholarId = keys.next()
                val arr = root.optJSONArray(scholarId) ?: continue
                val list = mutableListOf<ReligiousLesson>()
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { lessonFromJson(it) }?.let { item ->
                        if (YOUTUBE_VIDEO_ID_REGEX.matches(item.youtubeVideoId) && !isBlacklisted(item.youtubeVideoId)) {
                            list.add(item)
                        }
                    }
                }
                if (list.isNotEmpty()) {
                    map[scholarId] = list
                }
            }
            _lessonsByScholar.value = map
        } catch (_: Exception) {
        }
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
        put("youtubeVideoId", l.youtubeVideoId)
        put("scholarId", l.scholarId)
        put("scholarName", l.scholarName)
        put("channelId", l.channelId)
        put("channelTitle", l.channelTitle)
        put("title", l.title)
        put("description", l.description)
        put("thumbnailUrl", l.thumbnailUrl)
        put("durationSeconds", l.durationSeconds)
        put("publishedDateLabel", l.publishedDateLabel)
        put("isVerifiedOfficial", l.isVerifiedOfficial)
        put("isNewArrival", l.isNewArrival)
    }

    private fun lessonFromJson(o: JSONObject): ReligiousLesson? {
        val vid = o.optString("youtubeVideoId", "")
        if (!YOUTUBE_VIDEO_ID_REGEX.matches(vid)) return null
        return ReligiousLesson(
            id = o.optString("id", "yt_$vid"),
            youtubeVideoId = vid,
            scholarId = o.optString("scholarId", "latest"),
            scholarName = o.optString("scholarName", ""),
            channelId = o.optString("channelId", ""),
            channelTitle = o.optString("channelTitle", ""),
            title = o.optString("title", ""),
            description = o.optString("description", ""),
            thumbnailUrl = o.optString("thumbnailUrl", "https://i.ytimg.com/vi/$vid/hqdefault.jpg"),
            durationSeconds = o.optLong("durationSeconds", 0L),
            publishedDateLabel = o.optString("publishedDateLabel", ""),
            isVerifiedOfficial = o.optBoolean("isVerifiedOfficial", true),
            isNewArrival = o.optBoolean("isNewArrival", false)
        )
    }

    companion object {
        private val YOUTUBE_VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")
        private val CHANNEL_ID_EXTRACTOR = Pattern.compile("\"(?:externalId|channelId|browseId)\"\\s*:\\s*\"(UC[A-Za-z0-9_-]{20,26})\"")

        private val blockedAiOrStaticKeywords = listOf(
            "ذكاء اصطناعي", "الذكاء الاصطناعي", "بالذكاء الاصطناعي",
            "صوت آلي", "صوت مولد", "بدون صوت", "صامت",
            "صورة ثابتة", "audio only", "mp3", "شاشة سوداء",
            "#shorts", "shorts", "قصة حياة الشيخ", "وثائقي عن الشيخ", "معلومات عن الشيخ"
        )

        private val visualAppearanceHints = listOf(
            "مرئية", "فيديو", "تلفزيون", "نور على نور", "خطبة", "محاضرة", "درس", "لقاء", "حفل"
        )

        fun isLikelyAiOrStaticOrNonSpeaking(
            title: String,
            description: String,
            requireVisualHint: Boolean
        ): Boolean {
            val combined = "$title $description".lowercase()
            if (blockedAiOrStaticKeywords.any { combined.contains(it.lowercase()) }) {
                return true
            }
            if (requireVisualHint && visualAppearanceHints.none { combined.contains(it) }) {
                return true
            }
            return false
        }

        fun parseIsoDurationSeconds(iso: String): Long {
            if (!iso.startsWith("PT") && !iso.startsWith("P")) return 0L
            var total = 0L
            val regex = Regex("(\\d+)([HMS])")
            regex.findAll(iso).forEach { match ->
                val num = match.groupValues[1].toLongOrNull() ?: 0L
                when (match.groupValues[2]) {
                    "H" -> total += num * 3600L
                    "M" -> total += num * 60L
                    "S" -> total += num
                }
            }
            return total
        }

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
    }
}
