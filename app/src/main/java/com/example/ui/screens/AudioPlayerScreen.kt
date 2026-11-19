package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.catalog.QuranCatalog
import com.example.data.repository.OfflineAudioRepository
import com.example.domain.model.ReciterInfo
import com.example.domain.model.SurahInfo
import com.example.domain.prayer.PrayerTimeCalculator
import com.example.service.AudioPlaybackState
import com.example.ui.components.formatAudioTime
import com.example.ui.theme.AmiriFontFamily
import java.util.Locale

@Composable
fun AudioPlayerScreen(
    selectedReciterId: String,
    audioState: AudioPlaybackState,
    offlineSurahsByReciter: Map<String, Set<Int>>,
    downloadProgress: Map<String, Float>,
    totalOfflineBytes: Long,
    onSelectReciter: (String) -> Unit,
    onPlaySurah: (surahNumber: Int, reciterId: String) -> Unit,
    onDownloadSurah: (reciterId: String, surahNumber: Int) -> Unit,
    onDownloadBatch: (reciterId: String, surahNumbers: List<Int>) -> Unit,
    onDeleteDownloadedSurah: (reciterId: String, surahNumber: Int) -> Unit,
    onTogglePlayPause: () -> Unit,
    onStop: () -> Unit,
    onNextSurah: () -> Unit,
    onPreviousSurah: () -> Unit,
    onSeekTo: (Long) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showOfflineOnly by remember { mutableStateOf(false) }

    val selectedReciter = remember(selectedReciterId) {
        QuranCatalog.reciters.find { it.id == selectedReciterId } ?: QuranCatalog.reciters.first()
    }
    val offlineSurahsForReciter = offlineSurahsByReciter[selectedReciterId].orEmpty()

    val filteredSurahs = remember(searchQuery, showOfflineOnly, offlineSurahsForReciter) {
        QuranCatalog.surahs.filter { surah ->
            val matchesQuery = searchQuery.isBlank() ||
                surah.arabicName.contains(searchQuery.trim()) ||
                surah.englishName.contains(searchQuery.trim(), ignoreCase = true) ||
                surah.number.toString() == searchQuery.trim()
            val matchesOffline = !showOfflineOnly || offlineSurahsForReciter.contains(surah.number)
            matchesQuery && matchesOffline
        }
    }

    val offlineMb = remember(totalOfflineBytes) {
        String.format(Locale.US, "%.1f", totalOfflineBytes / (1024f * 1024f))
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = "الاستماع للقرآن الكريم",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        text = "استمع أو حمّل السور للاستماع بدون نت بصوت الشيخ ${selectedReciter.arabicName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Prominent Audio Control Card with Clear Play / Pause & Stop Buttons
            item {
                ActiveQuranPlayerCard(
                    selectedReciter = selectedReciter,
                    audioState = audioState,
                    onPlayDefault = {
                        onPlaySurah(audioState.currentSurahNumber, selectedReciter.id)
                    },
                    onTogglePlayPause = onTogglePlayPause,
                    onStop = onStop,
                    onNextSurah = onNextSurah,
                    onPreviousSurah = onPreviousSurah,
                    onSeekTo = onSeekTo
                )
            }

            // Reciters Horizontal Selector
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(QuranCatalog.reciters, key = { it.id }) { reciter ->
                        val offlineCount = offlineSurahsByReciter[reciter.id]?.size ?: 0
                        ReciterCard(
                            reciter = reciter,
                            offlineCount = offlineCount,
                            isSelected = reciter.id == selectedReciterId,
                            onClick = {
                                onSelectReciter(reciter.id)
                                onPlaySurah(audioState.currentSurahNumber, reciter.id)
                            }
                        )
                    }
                }
            }

            // Offline Downloads & Filter Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.OfflinePin,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "جاهز بدون نت: ${PrayerTimeCalculator.toArabicDigits(offlineSurahsForReciter.size)} / ١١٤ سورة",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "$offlineMb MB",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    onDownloadBatch(selectedReciter.id, OfflineAudioRepository.quickDownloadShortSurahs)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DownloadForOffline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("تحميل قصار السور", maxLines = 1)
                            }

                            FilterChip(
                                selected = showOfflineOnly,
                                onClick = { showOfflineOnly = !showOfflineOnly },
                                label = { Text("بدون نت فقط", maxLines = 1) },
                                modifier = Modifier.testTag("filter_offline_surahs_chip")
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("ابحث عن سورة للاستماع...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .testTag("audio_surah_search")
                )
            }

            items(filteredSurahs, key = { it.number }) { surah ->
                val isCurrentSurah = audioState.hasActiveItem &&
                    audioState.currentSurahNumber == surah.number &&
                    audioState.currentReciterId == selectedReciterId
                val isOfflineReady = offlineSurahsForReciter.contains(surah.number)
                val progress = downloadProgress[OfflineAudioRepository.progressKey(selectedReciterId, surah.number)]

                SurahAudioRow(
                    surah = surah,
                    isCurrent = isCurrentSurah,
                    isPlaying = isCurrentSurah && audioState.isPlaying,
                    isBuffering = isCurrentSurah && audioState.isBuffering,
                    isOfflineReady = isOfflineReady,
                    downloadProgress = progress,
                    onClick = {
                        if (isCurrentSurah) onTogglePlayPause()
                        else onPlaySurah(surah.number, selectedReciterId)
                    },
                    onDownloadClick = {
                        if (isOfflineReady) {
                            onDeleteDownloadedSurah(selectedReciterId, surah.number)
                        } else {
                            onDownloadSurah(selectedReciterId, surah.number)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ActiveQuranPlayerCard(
    selectedReciter: ReciterInfo,
    audioState: AudioPlaybackState,
    onPlayDefault: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onStop: () -> Unit,
    onNextSurah: () -> Unit,
    onPreviousSurah: () -> Unit,
    onSeekTo: (Long) -> Unit
) {
    val activeSurahName = if (audioState.hasActiveItem) audioState.currentSurahName else "الفاتحة"
    val activeReciterName = if (audioState.hasActiveItem) audioState.currentReciterName else selectedReciter.arabicName
    val ayahInfo = audioState.currentAyahNumber?.let {
        " • الآية ${PrayerTimeCalculator.toArabicDigits(it)}"
    } ?: ""

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("audio_main_player_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    if (audioState.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "سورة $activeSurahName$ayahInfo",
                        fontFamily = AmiriFontFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "الشيخ $activeReciterName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(onClick = onPreviousSurah) {
                    Icon(Icons.Default.SkipNext, contentDescription = "السورة السابقة")
                }
                IconButton(onClick = onNextSurah) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "السورة التالية")
                }
            }

            if (audioState.hasActiveItem && audioState.durationMs > 0L) {
                Spacer(Modifier.height(10.dp))
                val duration = audioState.durationMs.coerceAtLeast(1L).toFloat()
                Slider(
                    value = audioState.positionMs.toFloat().coerceIn(0f, duration),
                    onValueChange = { onSeekTo(it.toLong()) },
                    valueRange = 0f..duration,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatAudioTime(audioState.positionMs), style = MaterialTheme.typography.labelMedium)
                    Text(formatAudioTime(audioState.durationMs), style = MaterialTheme.typography.labelMedium)
                }
            }

            if (audioState.errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = audioState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (audioState.hasActiveItem) onTogglePlayPause() else onPlayDefault()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("audio_screen_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (audioState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when {
                            audioState.isBuffering -> "جاري التشغيل..."
                            audioState.isPlaying -> "إيقاف مؤقت"
                            audioState.hasActiveItem -> "متابعة التشغيل"
                            else -> "تشغيل التلاوة"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }

                if (audioState.hasActiveItem) {
                    OutlinedButton(
                        onClick = onStop,
                        modifier = Modifier
                            .weight(0.8f)
                            .testTag("audio_screen_stop_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("إيقاف")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReciterCard(
    reciter: ReciterInfo,
    offlineCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderMod = if (isSelected) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(18.dp))
    } else {
        Modifier
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        modifier = Modifier
            .width(176.dp)
            .then(borderMod)
            .clickable { onClick() }
            .testTag("reciter_card_${reciter.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "القارئ المختار",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = reciter.arabicName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (offlineCount > 0) "بدون نت: ${PrayerTimeCalculator.toArabicDigits(offlineCount)} سورة" else reciter.styleLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SurahAudioRow(
    surah: SurahInfo,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isOfflineReady: Boolean,
    downloadProgress: Float?,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrent) 3.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() }
            .testTag("audio_surah_row_${surah.number}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrent) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = PrayerTimeCalculator.toArabicDigits(surah.number),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "سورة ${surah.arabicName}",
                    fontFamily = AmiriFontFamily,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = buildString {
                        append(surah.revelationLabel)
                        append(" • ")
                        append(PrayerTimeCalculator.toArabicDigits(surah.ayahCount))
                        append(" آية")
                        if (isOfflineReady) append(" • بدون نت ✓")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isOfflineReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (downloadProgress != null) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 2.5.dp
                    )
                }
            } else {
                IconButton(
                    onClick = onDownloadClick,
                    modifier = Modifier.testTag("download_surah_${surah.number}")
                ) {
                    Icon(
                        imageVector = if (isOfflineReady) Icons.Default.OfflinePin else Icons.Default.DownloadForOffline,
                        contentDescription = if (isOfflineReady) "محفوظة بدون نت" else "تحميل السورة للاستماع بدون نت",
                        tint = if (isOfflineReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            Surface(
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل سورة ${surah.arabicName}",
                            tint = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}
