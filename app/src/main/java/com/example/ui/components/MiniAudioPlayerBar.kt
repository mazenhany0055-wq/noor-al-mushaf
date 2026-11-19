package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.domain.prayer.PrayerTimeCalculator
import com.example.service.AudioPlaybackState

@Composable
fun MiniAudioPlayerBar(
    audioState: AudioPlaybackState,
    onTogglePlayPause: () -> Unit,
    onStop: () -> Unit,
    onNextSurah: () -> Unit,
    onPreviousSurah: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onToggleRepeat: () -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!audioState.hasActiveItem) return
    var showFullSheet by remember { mutableStateOf(false) }

    val progress = if (audioState.durationMs > 0L) {
        (audioState.positionMs.toFloat() / audioState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { showFullSheet = true }
            .testTag("mini_audio_player"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    if (audioState.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "تلاوة",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val ayahSuffix = audioState.currentAyahNumber?.let {
                        " • آية ${PrayerTimeCalculator.toArabicDigits(it)}"
                    } ?: ""
                    Text(
                        text = "سورة ${audioState.currentSurahName}$ayahSuffix",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = audioState.errorMessage ?: audioState.currentReciterName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (audioState.errorMessage != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onPreviousSurah,
                    modifier = Modifier.testTag("mini_player_prev")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "السورة السابقة",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                FilledIconButton(
                    onClick = onTogglePlayPause,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("mini_player_play_pause")
                ) {
                    Icon(
                        imageVector = if (audioState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (audioState.isPlaying) "إيقاف مؤقت" else "تشغيل"
                    )
                }

                IconButton(
                    onClick = onStop,
                    modifier = Modifier.testTag("mini_player_stop")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إيقاف التلاوة",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
            )
        }
    }

    if (showFullSheet) {
        ExpandedAudioSheet(
            audioState = audioState,
            onDismiss = { showFullSheet = false },
            onTogglePlayPause = onTogglePlayPause,
            onStop = {
                onStop()
                showFullSheet = false
            },
            onNextSurah = onNextSurah,
            onPreviousSurah = onPreviousSurah,
            onSeekTo = onSeekTo,
            onSetSpeed = onSetSpeed,
            onToggleRepeat = onToggleRepeat,
            onSetSleepTimer = onSetSleepTimer
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpandedAudioSheet(
    audioState: AudioPlaybackState,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onStop: () -> Unit,
    onNextSurah: () -> Unit,
    onPreviousSurah: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onToggleRepeat: () -> Unit,
    onSetSleepTimer: (Int?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "المشغل",
                    modifier = Modifier.size(42.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "سورة ${audioState.currentSurahName}",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = audioState.currentReciterName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            if (audioState.errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = audioState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(18.dp))

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
                Text(
                    text = formatAudioTime(audioState.positionMs),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = formatAudioTime(audioState.durationMs),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleRepeat) {
                    Icon(
                        imageVector = if (audioState.repeatSurah) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "تكرار السورة",
                        tint = if (audioState.repeatSurah) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onPreviousSurah, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "السورة السابقة",
                        modifier = Modifier.size(32.dp)
                    )
                }

                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (audioState.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (audioState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "تشغيل أو إيقاف",
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                IconButton(onClick = onNextSurah, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "السورة التالية",
                        modifier = Modifier.size(32.dp)
                    )
                }

                AssistChip(
                    onClick = {
                        val nextSpeed = when (audioState.playbackSpeed) {
                            1.0f -> 1.25f
                            1.25f -> 1.5f
                            1.5f -> 0.75f
                            else -> 1.0f
                        }
                        onSetSpeed(nextSpeed)
                    },
                    label = {
                        Text(
                            text = "${audioState.playbackSpeed}x",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors()
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (audioState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (audioState.isPlaying) "إيقاف مؤقت" else "تشغيل")
                }

                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("إيقاف")
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "مؤقت النوم التلقائي",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                listOf(15, 30, 60).forEach { mins ->
                    FilterChip(
                        selected = false,
                        onClick = { onSetSleepTimer(mins) },
                        label = { Text("${PrayerTimeCalculator.toArabicDigits(mins)} د") }
                    )
                }
                if (audioState.sleepTimerRemainingSec != null) {
                    FilterChip(
                        selected = true,
                        onClick = { onSetSleepTimer(null) },
                        label = { Text("إلغاء (${audioState.sleepTimerRemainingSec / 60 + 1}د)") }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

fun formatAudioTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60
    val secs = totalSec % 60
    val raw = String.format("%02d:%02d", mins, secs)
    return PrayerTimeCalculator.toArabicDigits(raw)
}
