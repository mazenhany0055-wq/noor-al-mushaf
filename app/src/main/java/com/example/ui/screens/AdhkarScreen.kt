package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.UserSettings
import com.example.domain.model.AdhkarCategory
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
    onSelectCategory: (AdhkarCategory) -> Unit,
    onDecrementDhikr: (DhikrItem) -> Unit,
    onResetCategory: () -> Unit,
    onTogglePeriodicDhikr: (Boolean) -> Unit,
    onSelectInterval: (Int) -> Unit,
    onSelectMode: (PeriodicDhikrMode) -> Unit,
    onTestDhikrNow: () -> Unit
) {
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onTogglePeriodicDhikr(true)
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
            // Featured Periodic Auto-Dhikr Card ("ذكر كل 5 دقايق")
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
                                    text = "المذكّر التلقائي (اذكر الله)",
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
                                    if (wantEnable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
                            text = "طريقة التذكير:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            PeriodicDhikrMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = settings.periodicDhikrMode == mode,
                                    onClick = { onSelectMode(mode) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.NotificationsActive,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text(mode.arabicLabel) },
                                    modifier = Modifier.fillMaxWidth()
                                )
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

            // Adhkar Categories Header
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
                        style = MaterialTheme.typography.headlineMedium
                    )
                    IconButton(onClick = onResetCategory) {
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
                val progress = ((item.targetCount - remaining).toFloat() / item.targetCount.toFloat()).coerceIn(0f, 1f)

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDone) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                    ),
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
                                text = item.virtueOrSource,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(12.dp))
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
}

@Composable
private fun AdhkarPeriodicStatusText(
    settings: UserSettings,
    countdownFlow: StateFlow<CountdownUiState>
) {
    val countdown by countdownFlow.collectAsStateWithLifecycle()
    Text(
        text = if (settings.isPeriodicDhikrEnabled && countdown.periodicCountdownText.isNotEmpty()) {
            "يعمل الآن • الذكر القادم خلال ${countdown.periodicCountdownText}"
        } else if (settings.isPeriodicDhikrEnabled) {
            "يعمل الآن كل ${PrayerTimeCalculator.toArabicDigits(settings.periodicDhikrIntervalMinutes)} دقائق"
        } else {
            "يذكّرك بالصلاة على النبي ﷺ والأذكار تلقائيًا"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
    )
}
