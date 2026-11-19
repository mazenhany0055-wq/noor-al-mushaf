package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.catalog.QuranCatalog
import com.example.data.preferences.UserSettings
import com.example.domain.model.AdhkarCategory
import com.example.domain.model.AdhkarSheikhVoice
import com.example.domain.model.DhikrItem
import com.example.domain.model.PeriodicDhikrMode
import com.example.domain.prayer.PrayerTimeCalculator
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.viewmodel.AdhkarUiState
import com.example.ui.viewmodel.CountdownUiState
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AdhkarScreen(
    adhkarState: AdhkarUiState,
    settings: UserSettings,
    countdownFlow: StateFlow<CountdownUiState>,
    offlineAdhkarSessionKeys: Set<String>,
    downloadProgress: Map<String, Float>,
    onSelectCategory: (AdhkarCategory) -> Unit,
    onDecrementDhikr: (DhikrItem) -> Unit,
    onResetCategory: () -> Unit,
    onTogglePeriodicDhikr: (Boolean) -> Unit,
    onSelectInterval: (Int) -> Unit,
    onSelectMode: (PeriodicDhikrMode) -> Unit,
    onTestDhikrNow: () -> Unit,
    onSelectAdhkarVoice: (String) -> Unit,
    onSelectAdhkarQuranReciter: (String) -> Unit,
    onToggleChimeBeforeVoice: (Boolean) -> Unit,
    onPreviewAdhkarVoice: (String) -> Unit,
    onPlaySheikhSession: (AdhkarSheikhVoice, Boolean) -> Boolean,
    onDownloadSheikhSession: (AdhkarSheikhVoice, Boolean) -> Unit,
    onSpeakSingleDhikr: (DhikrItem) -> Unit,
    onToggleAutoPlayAll: () -> Unit,
    onStopSpeaking: () -> Unit
) {
    val context = LocalContext.current
    var showVoicePickerDialog by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onTogglePeriodicDhikr(true)
    }

    val selectedSheikh = remember(settings.adhkarVoiceId) {
        AdhkarSheikhVoice.fromId(settings.adhkarVoiceId)
    }

    val selectedQuranReciterName = remember(settings.adhkarQuranReciterId) {
        QuranCatalog.reciters.find { it.id == settings.adhkarQuranReciterId }?.arabicName ?: "مشاري راشد العفاسي"
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
            // Card 1: Real Human Sheikh Voices & Full Morning/Evening Sessions
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .testTag("adhkar_voice_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "أصوات الشيوخ الحقيقيين في الأذكار",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "${selectedSheikh.arabicName} • الآيات: $selectedQuranReciterName",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showVoicePickerDialog = true },
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("choose_adhkar_reciter_button")
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("اختيار الشيخ")
                            }

                            OutlinedButton(
                                onClick = { onPreviewAdhkarVoice(selectedSheikh.id) },
                                modifier = Modifier
                                    .weight(0.9f)
                                    .testTag("preview_adhkar_voice_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("تجربة صوت الشيخ")
                            }
                        }

                        if (selectedSheikh.morningSessionUrl != null || selectedSheikh.eveningSessionUrl != null) {
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.14f)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "الاستماع الكامل لأذكار الصباح والمساء بصوت ${selectedSheikh.arabicName}:",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.height(8.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (selectedSheikh.morningSessionUrl != null) {
                                    SheikhSessionRow(
                                        sheikh = selectedSheikh,
                                        isMorning = true,
                                        isOffline = offlineAdhkarSessionKeys.contains("${selectedSheikh.id}_morning"),
                                        progress = downloadProgress["session:${selectedSheikh.id}_morning"],
                                        isPlaying = adhkarState.activeSessionLabel == "${selectedSheikh.arabicName} • أذكار الصباح كاملة",
                                        onPlay = {
                                            val ok = onPlaySheikhSession(selectedSheikh, true)
                                            if (!ok) {
                                                Toast.makeText(
                                                    context,
                                                    "اتصل بالإنترنت أولاً أو حمّل أذكار الصباح لتعمل بدون نت",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        onDownload = { onDownloadSheikhSession(selectedSheikh, true) }
                                    )
                                }
                                if (selectedSheikh.eveningSessionUrl != null) {
                                    SheikhSessionRow(
                                        sheikh = selectedSheikh,
                                        isMorning = false,
                                        isOffline = offlineAdhkarSessionKeys.contains("${selectedSheikh.id}_evening"),
                                        progress = downloadProgress["session:${selectedSheikh.id}_evening"],
                                        isPlaying = adhkarState.activeSessionLabel == "${selectedSheikh.arabicName} • أذكار المساء كاملة",
                                        onPlay = {
                                            val ok = onPlaySheikhSession(selectedSheikh, false)
                                            if (!ok) {
                                                Toast.makeText(
                                                    context,
                                                    "اتصل بالإنترنت أولاً أو حمّل أذكار المساء لتعمل بدون نت",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        onDownload = { onDownloadSheikhSession(selectedSheikh, false) }
                                    )
                                }
                            }
                        }

                        if (adhkarState.activeSessionLabel != null) {
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "جاري التشغيل: ${adhkarState.activeSessionLabel}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = onStopSpeaking,
                                        modifier = Modifier.testTag("stop_sheikh_session_button")
                                    ) {
                                        Icon(
                                            Icons.Default.Stop,
                                            contentDescription = "إيقاف التشغيل",
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Card 2: Periodic Auto-Dhikr & Notification vs Voice Mode Control
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AlarmOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "المذكّر التلقائي بصوت حقيقي (بدون نت)",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                AdhkarPeriodicStatusText(
                                    settings = settings,
                                    countdownFlow = countdownFlow
                                )
                            }
                            Switch(
                                checked = settings.isPeriodicDhikrEnabled,
                                onCheckedChange = { wantEnable ->
                                    if (wantEnable &&
                                        settings.periodicDhikrMode.includesNotification &&
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                    ) {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (!granted) {
                                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            return@Switch
                                        }
                                    }
                                    onTogglePeriodicDhikr(wantEnable)
                                },
                                modifier = Modifier.testTag("adhkar_periodic_switch")
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "التكرار كل:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf(5, 10, 15, 30, 60)) { mins ->
                                FilterChip(
                                    selected = settings.periodicDhikrIntervalMinutes == mins,
                                    onClick = { onSelectInterval(mins) },
                                    label = {
                                        Text("${PrayerTimeCalculator.toArabicDigits(mins)} دقائق")
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "طريقة التذكير (بإشعار أو بدون إشعار):",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PeriodicDhikrMode.entries.forEach { mode ->
                                val selected = settings.periodicDhikrMode == mode
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectMode(mode) }
                                        .testTag("dhikr_mode_${mode.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (mode.includesNotification) {
                                                Icons.Default.NotificationsActive
                                            } else {
                                                Icons.Default.NotificationsOff
                                            },
                                            contentDescription = null,
                                            tint = if (selected) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = mode.arabicLabel,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (selected) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                            Text(
                                                text = mode.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (selected) {
                                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                        if (selected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "محدد",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = onTestDhikrNow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("test_periodic_dhikr_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("تشغيل الذكر الآن بصوت الشيخ")
                        }
                    }
                }
            }

            // Adhkar Categories Header + Play All Button
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "حصن المسلم والأذكار",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f)
                    )

                    FilledTonalButton(
                        onClick = onToggleAutoPlayAll,
                        modifier = Modifier.testTag("adhkar_play_all_button")
                    ) {
                        Icon(
                            imageVector = if (adhkarState.isAutoPlayingCategory) {
                                Icons.Default.Stop
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (adhkarState.isAutoPlayingCategory) "إيقاف" else "استماع للكل")
                    }

                    IconButton(onClick = {
                        onStopSpeaking()
                        onResetCategory()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "إعادة تعيين عداد الأذكار")
                    }
                }
            }

            // Category Pills
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(AdhkarCategory.entries) { category ->
                        FilterChip(
                            selected = adhkarState.selectedCategory == category,
                            onClick = { onSelectCategory(category) },
                            label = { Text(category.arabicTitle) }
                        )
                    }
                }
            }

            // Adhkar Items List
            items(adhkarState.categoryItems, key = { it.id }) { item ->
                val remaining = adhkarState.remainingFor(item)
                val isDone = remaining <= 0
                val isSpeakingThis = adhkarState.speakingDhikrId == item.id
                val progress = ((item.targetCount - remaining).toFloat() / item.targetCount.toFloat()).coerceIn(0f, 1f)

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isSpeakingThis -> MaterialTheme.colorScheme.primaryContainer
                            isDone -> MaterialTheme.colorScheme.surfaceVariant
                            else -> MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = if (isSpeakingThis) {
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    },
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isDone) 0.dp else 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable { onDecrementDhikr(item) }
                        .testTag("dhikr_card_${item.id}")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = item.text,
                            fontFamily = AmiriFontFamily,
                            fontSize = 21.sp,
                            lineHeight = 34.sp,
                            color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isSpeakingThis) "جاري تشغيل صوت الشيخ..." else item.virtueOrSource,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSpeakingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))

                            IconButton(
                                onClick = { onSpeakSingleDhikr(item) },
                                modifier = Modifier.testTag("speak_dhikr_${item.id}")
                            ) {
                                Icon(
                                    imageVector = if (isSpeakingThis) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (isSpeakingThis) "إيقاف القراءة" else "استماع للذكر بصوت الشيخ",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(Modifier.width(4.dp))
                            Surface(
                                color = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                shape = CircleShape
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isDone) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "مكتمل",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "تم",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Text(
                                            text = "${PrayerTimeCalculator.toArabicDigits(remaining)} / ${PrayerTimeCalculator.toArabicDigits(item.targetCount)}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    if (showVoicePickerDialog) {
        AdhkarSheikhPickerDialog(
            selectedSheikhId = selectedSheikh.id,
            selectedQuranReciterId = settings.adhkarQuranReciterId,
            chimeBeforeVoice = settings.adhkarChimeBeforeVoice,
            onSelectSheikh = { sheikh ->
                onSelectAdhkarVoice(sheikh.id)
                onPreviewAdhkarVoice(sheikh.id)
            },
            onSelectQuranReciter = onSelectAdhkarQuranReciter,
            onToggleChime = onToggleChimeBeforeVoice,
            onPreviewSheikh = { sheikh -> onPreviewAdhkarVoice(sheikh.id) },
            onDismiss = { showVoicePickerDialog = false }
        )
    }
}

@Composable
private fun SheikhSessionRow(
    sheikh: AdhkarSheikhVoice,
    isMorning: Boolean,
    isOffline: Boolean,
    progress: Float?,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    val label = if (isMorning) "أذكار الصباح كاملة" else "أذكار المساء كاملة"
    val suffix = if (isMorning) "morning" else "evening"
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onPlay,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("play_session_${sheikh.id}_$suffix")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isPlaying) "إيقاف $label" else label)
                }

                Spacer(Modifier.width(8.dp))

                if (isOffline) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CloudDone,
                                contentDescription = "محفوظ بدون نت",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "بدون نت",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                } else {
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier.testTag("download_session_${sheikh.id}_$suffix")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "حفظ $label للعمل بدون نت",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (progress != null) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun AdhkarSheikhPickerDialog(
    selectedSheikhId: String,
    selectedQuranReciterId: String,
    chimeBeforeVoice: Boolean,
    onSelectSheikh: (AdhkarSheikhVoice) -> Unit,
    onSelectQuranReciter: (String) -> Unit,
    onToggleChime: (Boolean) -> Unit,
    onPreviewSheikh: (AdhkarSheikhVoice) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختيار قارئ الأذكار (أصوات حقيقية)") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "اختر الشيخ الحقيقي للأذكار:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                AdhkarSheikhVoice.entries.forEach { sheikh ->
                    val isSelected = selectedSheikhId == sheikh.id
                    VoiceSelectionRow(
                        title = sheikh.arabicName,
                        subtitle = sheikh.description,
                        isSelected = isSelected,
                        tag = "sheikh_option_${sheikh.id}",
                        onSelect = { onSelectSheikh(sheikh) },
                        onPreview = { onPreviewSheikh(sheikh) }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "قارئ الآيات القرآنية في الأذكار:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(QuranCatalog.reciters, key = { it.id }) { reciter ->
                        FilterChip(
                            selected = selectedQuranReciterId == reciter.id,
                            onClick = { onSelectQuranReciter(reciter.id) },
                            label = { Text(reciter.arabicName) }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "نغمة تنبيه خفيفة قبل الأذكار",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = chimeBeforeVoice,
                        onCheckedChange = onToggleChime
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("حفظ وإغلاق")
            }
        }
    )
}

@Composable
private fun VoiceSelectionRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    tag: String,
    onSelect: () -> Unit,
    onPreview: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onPreview) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "تجربة صوت الشيخ",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "محدد",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AdhkarPeriodicStatusText(
    settings: UserSettings,
    countdownFlow: StateFlow<CountdownUiState>
) {
    val countdown by countdownFlow.collectAsStateWithLifecycle()
    Text(
        text = if (settings.isPeriodicDhikrEnabled && countdown.periodicCountdownText.isNotEmpty()) {
            "يعمل الآن (${settings.periodicDhikrMode.arabicLabel}) • التالي خلال ${countdown.periodicCountdownText}"
        } else if (settings.isPeriodicDhikrEnabled) {
            "يعمل كل ${PrayerTimeCalculator.toArabicDigits(settings.periodicDhikrIntervalMinutes)} دقائق • ${settings.periodicDhikrMode.arabicLabel}"
        } else {
            "يذكّرك بالصلاة على النبي ﷺ والأذكار بصوت حقيقي بدون نت"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
    )
}
