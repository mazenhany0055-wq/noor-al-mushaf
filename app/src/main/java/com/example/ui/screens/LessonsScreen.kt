package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.catalog.ScholarCatalog
import com.example.data.repository.LessonsRepository
import com.example.domain.model.LessonMediaType
import com.example.domain.model.LessonScholar
import com.example.domain.model.ReligiousLesson
import com.example.domain.prayer.PrayerTimeCalculator
import com.example.ui.components.LessonPlayerDialog
import com.example.ui.viewmodel.LessonsUiState

@Composable
fun LessonsScreen(
    uiState: LessonsUiState,
    displayedLessons: List<ReligiousLesson>,
    offlineLessonFiles: Map<String, String>,
    downloadProgress: Map<String, Float>,
    totalOfflineBytes: Long,
    onSelectScholar: (LessonScholar) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onRefreshLessons: () -> Unit,
    onToggleOfflineOnly: (Boolean) -> Unit,
    onSelectMediaFilter: (LessonMediaType?) -> Unit,
    onOpenLesson: (ReligiousLesson) -> Unit,
    onDownloadLesson: (ReligiousLesson) -> Unit,
    onDeleteDownloadedLesson: (String) -> Unit,
    onTogglePlayerAudioOnly: () -> Unit,
    onClosePlayer: () -> Unit,
    onDismissStatusBanner: () -> Unit
) {
    val selectedScholar = remember(uiState.selectedScholarId) {
        ScholarCatalog.scholars.find { it.id == uiState.selectedScholarId }
            ?: ScholarCatalog.scholars.first()
    }
    val offlineCount = offlineLessonFiles.size
    val offlineSizeLabel = remember(totalOfflineBytes) {
        LessonsRepository.formatSizeMb(totalOfflineBytes)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Card with Auto-Sync Status & Offline Storage Counter
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF0A3A2D),
                                        Color(0xFF145946)
                                    )
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OndemandVideo,
                                        contentDescription = null,
                                        tint = Color(0xFFE3BE64),
                                        modifier = Modifier.size(30.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "دروس دين (فيديو وصوت)",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "تحديث تلقائي • محفوظ بدون نت: ${PrayerTimeCalculator.toArabicDigits(offlineCount)}${if (offlineSizeLabel.isNotBlank()) " ($offlineSizeLabel)" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFD6EAE2)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = onRefreshLessons,
                                    modifier = Modifier.testTag("refresh_lessons_button")
                                ) {
                                    if (uiState.isSyncing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            strokeWidth = 2.5.dp,
                                            color = Color(0xFFE3BE64)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "تحديث وجلب أحدث الدروس",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Status / Feedback Banner
            if (uiState.statusBanner != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDismissStatusBanner() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = uiState.statusBanner,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onDismissStatusBanner) {
                                Text("حسناً")
                            }
                        }
                    }
                }
            }

            // Live Auto Search for any Sheikh or Topic
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = {
                            Text("ابحث باسم أي شيخ أو درس (الشعراوي، الحصري، سمير مصطفى...)")
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("lessons_search_input")
                    )

                    if (uiState.searchQuery.isNotBlank()) {
                        FilledTonalButton(
                            onClick = onSubmitSearch,
                            modifier = Modifier.testTag("lessons_search_submit")
                        ) {
                            Text("بحث")
                        }
                    }
                }
            }

            // Scholar Chips Carousel
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ScholarCatalog.scholars, key = { it.id }) { scholar ->
                        val selected = uiState.selectedScholarId == scholar.id && uiState.searchQuery.isBlank()
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectScholar(scholar) },
                            label = {
                                Text(
                                    text = scholar.arabicName,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = if (scholar.id == "latest") {
                                {
                                    Icon(
                                        Icons.Default.NewReleases,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            modifier = Modifier.testTag("scholar_chip_${scholar.id}")
                        )
                    }
                }
            }

            // Selected Scholar Banner + Offline / Type Filters
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (uiState.searchQuery.isBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedScholar.arabicName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = selectedScholar.titleOrSpecialty,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = selectedScholar.categoryTag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = !uiState.showOfflineOnly && uiState.mediaFilter == null,
                                onClick = {
                                    onToggleOfflineOnly(false)
                                    onSelectMediaFilter(null)
                                },
                                label = { Text("الكل") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = uiState.mediaFilter == LessonMediaType.VIDEO,
                                onClick = {
                                    onSelectMediaFilter(
                                        if (uiState.mediaFilter == LessonMediaType.VIDEO) null else LessonMediaType.VIDEO
                                    )
                                },
                                label = { Text("فيديو (صوت وصورة)") },
                                leadingIcon = {
                                    Icon(Icons.Default.OndemandVideo, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                        item {
                            FilterChip(
                                selected = uiState.mediaFilter == LessonMediaType.AUDIO,
                                onClick = {
                                    onSelectMediaFilter(
                                        if (uiState.mediaFilter == LessonMediaType.AUDIO) null else LessonMediaType.AUDIO
                                    )
                                },
                                label = { Text("دروس صوتية") },
                                leadingIcon = {
                                    Icon(Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                        item {
                            FilterChip(
                                selected = uiState.showOfflineOnly,
                                onClick = { onToggleOfflineOnly(!uiState.showOfflineOnly) },
                                label = { Text("المحفوظة بدون نت (${PrayerTimeCalculator.toArabicDigits(offlineCount)})") },
                                leadingIcon = {
                                    Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.testTag("filter_offline_lessons_chip")
                            )
                        }
                    }
                }
            }

            // Empty State
            if (displayedLessons.isEmpty() && !uiState.isSyncing) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OndemandVideo,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = if (uiState.showOfflineOnly) {
                                    "لا توجد دروس محفوظة بدون نت بعد"
                                } else {
                                    "اضغط تحديث لجلب دروس ${selectedScholar.arabicName}"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (uiState.showOfflineOnly) {
                                    "اضغط على زر التحميل بجانب أي فيديو أو درس لمشاهدته لاحقاً بدون إنترنت."
                                } else {
                                    "التطبيق يبحث تلقائياً في المكتبات الإسلامية المفتوحة عن أحدث الفيديوهات والدروس."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = onRefreshLessons) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("تحديث الدروس الآن")
                            }
                        }
                    }
                }
            }

            // Lessons List
            items(displayedLessons, key = { it.id }) { lesson ->
                val sanitizedId = lesson.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val isOffline = offlineLessonFiles.containsKey(sanitizedId)
                val progress = downloadProgress[lesson.id]

                LessonItemCard(
                    lesson = lesson,
                    isOffline = isOffline,
                    downloadProgress = progress,
                    onPlay = { onOpenLesson(lesson) },
                    onDownload = { onDownloadLesson(lesson) },
                    onDeleteOffline = { onDeleteDownloadedLesson(lesson.id) }
                )
            }
        }
    }

    val active = uiState.activeLesson
    val playable = uiState.activePlayableUri
    if (active != null && playable != null) {
        val sanitized = active.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        LessonPlayerDialog(
            lesson = active,
            playableUri = playable,
            isAudioOnly = uiState.audioOnlyInPlayer,
            isDownloadedOffline = offlineLessonFiles.containsKey(sanitized),
            downloadProgress = downloadProgress[active.id],
            onToggleAudioOnly = onTogglePlayerAudioOnly,
            onDownloadOffline = { onDownloadLesson(active) },
            onClose = onClosePlayer
        )
    }
}

@Composable
private fun LessonItemCard(
    lesson: ReligiousLesson,
    isOffline: Boolean,
    downloadProgress: Float?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onDeleteOffline: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .testTag("lesson_card_${lesson.id}")
    ) {
        Column {
            // Thumbnail Banner + Play Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 8.5f)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF0B3529),
                                Color(0xFF195745)
                            )
                        )
                    )
            ) {
                if (!lesson.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = lesson.thumbnailUrl,
                        contentDescription = lesson.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x55000000))
                    )
                }

                // Center Play Icon
                Icon(
                    imageVector = Icons.Default.PlayCircleFilled,
                    contentDescription = "تشغيل الدرس",
                    tint = Color.White.copy(alpha = 0.94f),
                    modifier = Modifier
                        .size(56.dp)
                        .align(Alignment.Center)
                )

                // Top Badges Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                        .align(Alignment.TopStart),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (lesson.isNewArrival) {
                        Surface(
                            color = Color(0xFFE3BE64),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "جديد",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1C180B),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Surface(
                        color = Color(0xCC06281F),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = lesson.mediaType.arabicLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (isOffline) {
                        Surface(
                            color = Color(0xDD1B6E4F),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "بدون نت",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                val durationLabel = LessonsRepository.formatDuration(lesson.durationSeconds)
                val sizeLabel = LessonsRepository.formatSizeMb(lesson.fileSizeBytes)
                if (durationLabel.isNotBlank() || sizeLabel.isNotBlank()) {
                    Surface(
                        color = Color(0xB3000000),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = listOfNotNull(
                                durationLabel.takeIf { it.isNotBlank() },
                                sizeLabel.takeIf { it.isNotBlank() }
                            ).joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = lesson.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${lesson.scholarName}${if (lesson.publishedDateLabel.isNotBlank()) " • ${lesson.publishedDateLabel}" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPlay,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("play_lesson_${lesson.id}")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (lesson.mediaType == LessonMediaType.VIDEO) "مشاهدة الآن" else "استماع الآن")
                    }

                    if (isOffline) {
                        IconButton(
                            onClick = onDeleteOffline,
                            modifier = Modifier
                                .clip(CircleShape)
                                .testTag("delete_lesson_${lesson.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "حذف النسخة المحفوظة",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onDownload,
                            enabled = downloadProgress == null,
                            modifier = Modifier.testTag("download_lesson_${lesson.id}")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (downloadProgress != null) {
                                    "${(downloadProgress * 100).toInt()}%"
                                } else {
                                    "بدون نت"
                                }
                            )
                        }
                    }
                }

                if (downloadProgress != null) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
