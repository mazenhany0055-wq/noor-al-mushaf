package com.example.domain.model

enum class LessonMediaType(val arabicLabel: String) {
    VIDEO("فيديو صوت وصورة"),
    AUDIO("درس صوتي")
}

data class LessonScholar(
    val id: String,
    val arabicName: String,
    val titleOrSpecialty: String,
    val archiveSearchQueries: List<String>,
    val fallbackIdentifiers: List<String> = emptyList(),
    val categoryTag: String = "دروس وعظية"
)

data class ReligiousLesson(
    val id: String,
    val scholarId: String,
    val scholarName: String,
    val title: String,
    val description: String,
    val mediaUrl: String,
    val alternateAudioUrl: String? = null,
    val thumbnailUrl: String? = null,
    val mediaType: LessonMediaType = LessonMediaType.VIDEO,
    val durationSeconds: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val publishedDateLabel: String = "",
    val sourceLabel: String = "Archive.org",
    val isNewArrival: Boolean = false
)
