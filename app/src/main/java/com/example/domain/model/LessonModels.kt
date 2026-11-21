package com.example.domain.model

data class LessonScholar(
    val id: String,
    val arabicName: String,
    val titleOrSpecialty: String,
    val officialChannelIds: List<String> = emptyList(),
    val officialHandles: List<String> = emptyList(),
    val youtubeVideoSearchQueries: List<String> = emptyList(),
    val requireVisualAppearanceFilter: Boolean = false,
    val categoryTag: String = "القناة الرسمية • يوتيوب"
)

data class ReligiousLesson(
    val id: String,
    val youtubeVideoId: String,
    val scholarId: String,
    val scholarName: String,
    val channelId: String = "",
    val channelTitle: String = "",
    val title: String,
    val description: String,
    val thumbnailUrl: String,
    val durationSeconds: Long = 0L,
    val publishedDateLabel: String = "",
    val isVerifiedOfficial: Boolean = true,
    val isNewArrival: Boolean = false
)
