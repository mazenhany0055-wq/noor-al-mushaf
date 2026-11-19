package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
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
import com.example.domain.model.AdhkarVoicePreset
import com.example.domain.model.DhikrItem
import com.example.domain.model.OfflineTtsVoiceInfo
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
    availableSystemVoices: List<OfflineTtsVoiceInfo>,
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

    val selectedVoiceLabel = remember(settings.adhkarVoiceId, availableSystemVoices) {
        AdhkarVoicePreset.fromId(settings.adhkarVoiceId)?.arabicName
            ?: availableSystemVoices.find { it.id == settings.adhkarVoiceId }?.displayName
            ?: AdhkarVoicePreset.CALM_DEEP.arabicName
    }

    val selectedQuranReciterName = remember(settings.adhkarQuranReciterId) {
        QuranCatalog.reciters.find { it.id == settings.adhkarQuranReciterId }?.arabicName ?: "ياسر الدوسري"
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
            // Card 1: Adhkar Reciter & Offline Voice Selection
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
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "قارئ وصوت الأذكار (بدون نت)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "الصوت الحالي: $selectedVoiceLabel • الآيات: الشيخ $selectedQuranReciterName",
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
                                Text("اختيار القارئ والصوت")
                            }

                            OutlinedButton(
                                onClick = { onPreviewAdhkarVoice(settings.adhkarVoiceId) },
                                modifier = Modifier
                                    .weight(0.8f)
                                    .testTag("preview_adhkar_voice_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("تجربة")
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
                                    text = "المذكّر التلقائي (يعمل بدون نت)",
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
                            Text("تشغيل الذكر الآن (تجربة)")
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
                                text = if (isSpeakingThis) "جاري قراءة الذكر الآن..." else item.virtueOrSource,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSpeakingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))

                            // Speak/Stop Button for this individual Dhikr
                            IconButton(
                                onClick = { onSpeakSingleDhikr(item) },
                                modifier = Modifier.testTag("speak_dhikr_${item.id}")
                            ) {
                                Icon(
                                    imageVector = if (isSpeakingThis) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (isSpeakingThis) "إيقاف القراءة" else "استماع للذكر بالصوت",
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
        AdhkarVoicePickerDialog(
            selectedVoiceId = settings.adhkarVoiceId,
            selectedQuranReciterId = settings.adhkarQuranReciterId,
            chimeBeforeVoice = settings.adhkarChimeBeforeVoice,
            availableSystemVoices = availableSystemVoices,
            onSelectVoice = { id ->
                onSelectAdhkarVoice(id)
                onPreviewAdhkarVoice(id)
            },
            onSelectQuranReciter = onSelectAdhkarQuranReciter,
            onToggleChime = onToggleChimeBeforeVoice,
            onPreviewVoice = onPreviewAdhkarVoice,
            onInstallSystemVoices = {
                try {
                    context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                } catch (_: Exception) {
                }
            },
            onDismiss = { showVoicePickerDialog = false }
        )
    }
}

@Composable
private fun AdhkarVoicePickerDialog(
    selectedVoiceId: String,
    selectedQuranReciterId: String,
    chimeBeforeVoice: Boolean,
    availableSystemVoices: List<OfflineTtsVoiceInfo>,
    onSelectVoice: (String) -> Unit,
    onSelectQuranReciter: (String) -> Unit,
    onToggleChime: (Boolean) -> Unit,
    onPreviewVoice: (String) -> Unit,
    onInstallSystemVoices: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختيار قارئ وصوت الأذكار") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "أصوات النطق التلقائي بدون نت:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                AdhkarVoicePreset.entries.forEach { preset ->
                    val isSelected = selectedVoiceId == preset.id
                    VoiceSelectionRow(
                        title = preset.arabicName,
                        subtitle = preset.description,
                        isSelected = isSelected,
                        onSelect = { onSelectVoice(preset.id) },
                        onPreview = { onPreviewVoice(preset.id) }
                    )
                }

                if (availableSystemVoices.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "أصوات الجهاز العربية المكتشفة:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    availableSystemVoices.forEach { sysVoice ->
                        VoiceSelectionRow(
                            title = sysVoice.displayName,
                            subtitle = "صوت مثبت على الهاتف",
                            isSelected = selectedVoiceId == sysVoice.id,
                            onSelect = { onSelectVoice(sysVoice.id) },
                            onPreview = { onPreviewVoice(sysVoice.id) }
                        )
                    }
                }

                OutlinedButton(
                    onClick = onInstallSystemVoices,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إضافة / تحميل أصوات عربية من إعدادات الجهاز")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "قارئ آية الكرسي والمعوذات والأدعية القرآنية:",
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
                        text = "نغمة تنبيه خفيفة قبل نطق الذكر",
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
    onSelect: () -> Unit,
    onPreview: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
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
                    contentDescription = "تجربة الصوت",
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
            "يذكّرك بالصلاة على النبي ﷺ والأذكار تلقائيًا بدون نت"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
    )
}
