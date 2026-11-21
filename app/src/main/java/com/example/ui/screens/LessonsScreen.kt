package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.catalog.ScholarCatalog
import com.example.data.repository.LessonsRepository
import com.example.domain.model.LessonScholar
import com.example.domain.model.ReligiousLesson
import com.example.domain.prayer.PrayerTimeCalculator
import com.example.ui.components.LessonPlayerDialog
import com.example.ui.viewmodel.LessonsUiState

@Composable
fun LessonsScreen(
    uiState: LessonsUiState,
    displayedLessons: List<ReligiousLesson>,
    favoriteVideoIds: Set<String>,
    onSelectScholar: (LessonScholar) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onRefreshLessons: () -> Unit,
    onToggleFavoritesOnly: (Boolean) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenLesson: (ReligiousLesson) -> Unit,
    onBrokenVideoDetected: (ReligiousLesson, String) -> Unit,
    onClosePlayer: () -> Unit,
    onDismissStatusBanner: () -> Unit
) {
    val context = LocalContext.current
    val selectedScholar = remember(uiState.selectedScholarId) {
        ScholarCatalog.scholars.find { it.id == uiState.selectedScholarId }
            ?: ScholarCatalog.scholars.first()
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
            // Header Card showing Verified YouTube Official Channels status
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
                                        text = "دروس دين (فيديو يوتيوب رسمي HD)",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "قنوات الشيوخ الرسمية بالصوت والصورة الحقيقية • تحديث تلقائي",
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

            // Search bar
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
                            Text("ابحث باسم الشيخ أو عنوان الدرس...")
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

            // Scholars horizontal carousel
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

            // Active scholar info + All / Favorites chips
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
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Verified,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = selectedScholar.categoryTag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !uiState.showFavoritesOnly,
                            onClick = { onToggleFavoritesOnly(false) },
                            label = { Text("كل الفيديوهات الرسمية") }
                        )
                        FilterChip(
                            selected = uiState.showFavoritesOnly,
                            onClick = { onToggleFavoritesOnly(!uiState.showFavoritesOnly) },
                            label = {
                                Text("المفضلة (${PrayerTimeCalculator.toArabicDigits(favoriteVideoIds.size)})")
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.testTag("filter_favorite_lessons_chip")
                        )
                    }
                }
            }

            // Empty / Loading / Owner-Blocked State
            if (displayedLessons.isEmpty() && uiState.isSyncing) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(34.dp))
                            Text(
                                text = "جاري تجهيز الفيديوهات المسموح تشغيلها...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else if (displayedLessons.isEmpty()) {
                item {
                    val youtubeUrl = uiState.blockedFallbackYouTubeUrl
                        ?: LessonsRepository.buildScholarYouTubeFallbackUrl(selectedScholar)
                    val nextScholar = ScholarCatalog.scholars.firstOrNull {
                        it.id != selectedScholar.id && it.id != "latest"
                    } ?: ScholarCatalog.scholars.first()

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scholar_empty_or_blocked_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OndemandVideo,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )

                            if (uiState.showFavoritesOnly) {
                                Text(
                                    text = "لا توجد دروس في المفضلة بعد",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            } else if (uiState.isScholarEmbedBlocked) {
                                Text(
                                    text = "الفيديوهات دي مقفولة من صاحبها، جرب شيخ تاني",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.testTag("scholar_blocked_message")
                                )
                                Button(
                                    onClick = {
                                        try {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl))
                                            )
                                        } catch (_: Exception) {
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("watch_on_youtube_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("شاهد على YouTube")
                                }
                                FilledTonalButton(
                                    onClick = { onSelectScholar(nextScholar) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("try_another_scholar_button")
                                ) {
                                    Text("جرب شيخ تاني (${nextScholar.arabicName})")
                                }
                            } else {
                                Text(
                                    text = "جلب أحدث فيديوهات ${selectedScholar.arabicName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "يتم عرض الفيديوهات الرسمية الموثقة والمسموح تشغيلها داخل التطبيق فقط.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = onRefreshLessons,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("تحديث من القنوات الرسمية")
                                }
                                FilledTonalButton(
                                    onClick = {
                                        try {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl))
                                            )
                                        } catch (_: Exception) {
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("watch_on_youtube_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("شاهد على YouTube")
                                }
                            }
                        }
                    }
                }
            }

            items(displayedLessons, key = { it.youtubeVideoId }) { lesson ->
                val isFav = favoriteVideoIds.contains(lesson.youtubeVideoId)
                OfficialYouTubeLessonCard(
                    lesson = lesson,
                    isFavorite = isFav,
                    onPlay = { onOpenLesson(lesson) },
                    onToggleFavorite = { onToggleFavorite(lesson.youtubeVideoId) }
                )
            }
        }
    }

    val active = uiState.activeLesson
    if (active != null) {
        LessonPlayerDialog(
            lesson = active,
            isFavorite = favoriteVideoIds.contains(active.youtubeVideoId),
            onToggleFavorite = { onToggleFavorite(active.youtubeVideoId) },
            onBrokenVideoDetected = onBrokenVideoDetected,
            onClose = onClosePlayer
        )
    }
}

@Composable
private fun OfficialYouTubeLessonCard(
    lesson: ReligiousLesson,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .testTag("lesson_card_${lesson.youtubeVideoId}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color(0xFF0B3529))
            ) {
                AsyncImage(
                    model = lesson.thumbnailUrl,
                    contentDescription = lesson.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x44000000))
                )

                Icon(
                    imageVector = Icons.Default.PlayCircleFilled,
                    contentDescription = "مشاهدة الفيديو بالصوت والصورة",
                    tint = Color.White.copy(alpha = 0.94f),
                    modifier = Modifier
                        .size(58.dp)
                        .align(Alignment.Center)
                )

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
                }

                val durationLabel = LessonsRepository.formatDuration(lesson.durationSeconds)
                if (durationLabel.isNotBlank()) {
                    Surface(
                        color = Color(0xC0000000),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = durationLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(14.dp)) {
                val cleanTitle = LessonsRepository.cleanLessonTitle(lesson.title, lesson.scholarName)
                    .ifBlank { lesson.title }
                Text(
                    text = cleanTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
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
                            .testTag("play_lesson_${lesson.youtubeVideoId}")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("مشاهدة الآن")
                    }

                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.testTag("favorite_lesson_${lesson.youtubeVideoId}")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isFavorite) "إزالة من المفضلة" else "حفظ في المفضلة",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
