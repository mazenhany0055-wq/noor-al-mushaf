package com.example.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.TasbihItemEntity
import com.example.domain.prayer.PrayerTimeCalculator
import com.example.ui.theme.AmiriFontFamily
import kotlinx.coroutines.delay

@Composable
fun TasbihScreen(
    items: List<TasbihItemEntity>,
    selectedId: Int,
    totalLifetimeCount: Long,
    soundEnabled: Boolean,
    vibrationEnabled: Boolean,
    onSelectTasbih: (Int) -> Unit,
    onIncrement: (TasbihItemEntity) -> Unit,
    onReset: (TasbihItemEntity) -> Unit,
    onUpdateTarget: (TasbihItemEntity, Int) -> Unit,
    onAddCustom: (String, Int) -> Unit,
    onToggleSound: (Boolean) -> Unit,
    onToggleVibration: (Boolean) -> Unit
) {
    val activeItem = items.find { it.id == selectedId } ?: items.firstOrNull()
    val haptic = LocalHapticFeedback.current
    var showAddDialog by remember { mutableStateOf(false) }
    var tapPulse by remember { mutableFloatStateOf(1f) }

    var toneGenerator by remember { mutableStateOf<ToneGenerator?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            try {
                toneGenerator?.release()
            } catch (_: Exception) {
            }
        }
    }

    val animatedScale by animateFloatAsState(
        targetValue = tapPulse,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "tasbih_orb_scale"
    )
    LaunchedEffect(tapPulse) {
        if (tapPulse != 1f) {
            delay(80L)
            tapPulse = 1f
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Summary & Settings Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("المسبحة الإلكترونية", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        text = "إجمالي التسبيحات: ${PrayerTimeCalculator.toArabicDigits(totalLifetimeCount.toString())}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row {
                    IconButton(
                        onClick = { onToggleVibration(!vibrationEnabled) },
                        modifier = Modifier.testTag("tasbih_vibration_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Vibration,
                            contentDescription = "تبديل الاهتزاز",
                            tint = if (vibrationEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                    IconButton(
                        onClick = { onToggleSound(!soundEnabled) },
                        modifier = Modifier.testTag("tasbih_sound_toggle")
                    ) {
                        Icon(
                            imageVector = if (soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "تبديل الصوت",
                            tint = if (soundEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Tasbih Phrase Selector Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    FilterChip(
                        selected = activeItem?.id == item.id,
                        onClick = { onSelectTasbih(item.id) },
                        label = { Text(item.phrase) }
                    )
                }
                item {
                    FilterChip(
                        selected = false,
                        onClick = { showAddDialog = true },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("إضافة ذكر") }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            if (activeItem != null) {
                // Active Phrase Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = activeItem.phrase,
                            fontFamily = AmiriFontFamily,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        val targetLabel = if (activeItem.targetCount > 0) {
                            "الهدف: ${PrayerTimeCalculator.toArabicDigits(activeItem.targetCount)}"
                        } else {
                            "مسبحة مفتوحة"
                        }
                        Text(
                            text = "الدورة ${PrayerTimeCalculator.toArabicDigits(activeItem.roundsCompleted + 1)} • $targetLabel",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                // Interactive Circular Tasbih Orb
                val progress = if (activeItem.targetCount > 0) {
                    (activeItem.currentCount.toFloat() / activeItem.targetCount.toFloat()).coerceIn(0f, 1f)
                } else {
                    ((activeItem.currentCount % 33) / 33f)
                }
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = spring(stiffness = 400f),
                    label = "tasbih_ring"
                )

                val primaryColor = MaterialTheme.colorScheme.primary
                val secondaryColor = MaterialTheme.colorScheme.secondary
                val trackColor = MaterialTheme.colorScheme.surfaceVariant

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(250.dp)
                        .scale(animatedScale)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            tapPulse = 0.94f
                            if (vibrationEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            if (soundEnabled) {
                                try {
                                    val gen = toneGenerator ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, 35).also {
                                        toneGenerator = it
                                    }
                                    gen.startTone(ToneGenerator.TONE_PROP_BEEP, 35)
                                } catch (_: Exception) {
                                }
                            }
                            onIncrement(activeItem)
                        }
                        .testTag("tasbih_tap_orb")
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 18.dp.toPx()
                        drawCircle(
                            color = trackColor,
                            radius = size.minDimension / 2f - strokeWidth,
                            style = Stroke(width = strokeWidth)
                        )
                        drawArc(
                            brush = Brush.sweepGradient(listOf(primaryColor, secondaryColor, primaryColor)),
                            startAngle = -90f,
                            sweepAngle = animatedProgress * 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(
                                    primaryColor.copy(alpha = 0.16f),
                                    primaryColor.copy(alpha = 0.05f)
                                )
                            ),
                            radius = size.minDimension / 2f - strokeWidth * 1.5f
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = PrayerTimeCalculator.toArabicDigits(activeItem.currentCount),
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 54.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "اضغط للتسبيح",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Target & Reset Controls
                Text(
                    text = "عدد التسبيحات في الدورة",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(33 to "٣٣", 99 to "٩٩", 100 to "١٠٠", 0 to "مفتوح").forEach { (target, label) ->
                        FilterChip(
                            selected = activeItem.targetCount == target,
                            onClick = { onUpdateTarget(activeItem, target) },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { onReset(activeItem) },
                    modifier = Modifier.testTag("tasbih_reset_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("تصفير العداد الحالي")
                }
            }
        }
    }

    if (showAddDialog) {
        var newPhrase by remember { mutableStateOf("") }
        var target by remember { mutableIntStateOf(33) }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("إضافة ذكر جديد للمسبحة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newPhrase,
                        onValueChange = { newPhrase = it },
                        label = { Text("نص الذكر") },
                        placeholder = { Text("مثال: سبحان الله وبحمده") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(33, 99, 100).forEach { t ->
                            FilterChip(
                                selected = target == t,
                                onClick = { target = t },
                                label = { Text(PrayerTimeCalculator.toArabicDigits(t)) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPhrase.isNotBlank()) {
                            onAddCustom(newPhrase, target)
                            showAddDialog = false
                        }
                    }
                ) { Text("إضافة") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("إلغاء") }
            }
        )
    }
}
