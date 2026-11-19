package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.catalog.QuranCatalog
import com.example.domain.model.AdhanVoice
import com.example.domain.model.CalculationMethod
import com.example.domain.model.CityPreset
import com.example.domain.model.PrayerName
import com.example.domain.model.PrayerTimeItem
import com.example.domain.prayer.PrayerTimeCalculator
import com.example.ui.components.QiblaCompassDialog
import com.example.ui.viewmodel.CountdownUiState
import com.example.ui.viewmodel.HomeUiState
import kotlinx.coroutines.flow.StateFlow

@Composable
fun HomePrayerScreen(
    uiState: HomeUiState,
    countdownFlow: StateFlow<CountdownUiState>,
    onSelectCity: (CityPreset) -> Unit,
    onDetectGps: () -> Unit,
    onSelectMethod: (CalculationMethod) -> Unit,
    onSelectAdhanVoice: (AdhanVoice) -> Unit,
    onTogglePrayerAdhan: (PrayerName, Boolean) -> Unit,
    onTestAdhan: () -> Unit,
    onStopAdhan: () -> Unit,
    onTogglePeriodicDhikr: (Boolean) -> Unit,
    onOpenMushafPage: (Int) -> Unit,
    onNavigateToAdhkar: () -> Unit,
    onUpdateDarkMode: (String) -> Unit,
    onDismissBanner: () -> Unit
) {
    val context = LocalContext.current
    val schedule = uiState.schedule
    val settings = uiState.settings

    var showCityDialog by remember { mutableStateOf(false) }
    var showQiblaDialog by remember { mutableStateOf(false) }
    var showMethodDialog by remember { mutableStateOf(false) }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onDetectGps()
    }

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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Optional active feedback banner
            if (uiState.activeBannerMessage != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDismissBanner() }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = uiState.activeBannerMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onDismissBanner) {
                                Text("حسناً")
                            }
                        }
                    }
                }
            }

            // Hero Mosque Prayer Banner
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_mosque_dawn),
                            contentDescription = "خلفية المسجد لمواقيت الصلاة",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xCC06281F),
                                            Color(0xE00A382B)
                                        )
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            // Top Location & GPS Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = Color.White.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier
                                        .clickable { showCityDialog = true }
                                        .testTag("select_city_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = "تغيير المدينة",
                                            tint = Color(0xFFE3BE64),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = settings.cityName,
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }

                                Surface(
                                    color = Color.White.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier
                                        .clickable {
                                            val hasPerm = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (hasPerm) {
                                                onDetectGps()
                                            } else {
                                                locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                            }
                                        }
                                        .testTag("gps_location_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (uiState.isDetectingLocation) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.MyLocation,
                                                contentDescription = "تحديد الموقع تلقائياً",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "موقعي",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            Text(
                                text = schedule?.hijriDate ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFE3BE64)
                            )
                            Text(
                                text = schedule?.gregorianDate ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )

                            Spacer(Modifier.height(18.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        text = "الصلاة القادمة",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.78f)
                                    )
                                    Text(
                                        text = schedule?.nextPrayer?.prayer?.arabicName ?: "الفجر",
                                        style = MaterialTheme.typography.displayMedium,
                                        color = Color.White
                                    )
                                    Text(
                                        text = schedule?.nextPrayer?.formattedTime ?: "",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFFE3BE64)
                                    )
                                }

                                Surface(
                                    color = Color.Black.copy(alpha = 0.28f),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "متبقي على الأذان",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                        PrayerCountdownText(countdownFlow = countdownFlow)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Action Shortcuts Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenMushafPage(settings.lastReadPage) }
                            .testTag("quick_resume_quran"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Icon(
                                imageVector = Icons.Default.AutoStories,
                                contentDescription = "متابعة القرآن",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "أكمل القراءة",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "صفحة ${PrayerTimeCalculator.toArabicDigits(settings.lastReadPage)} • ${QuranCatalog.surahForPage(settings.lastReadPage).arabicName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showQiblaDialog = true }
                            .testTag("open_qibla_button"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = "اتجاه القبلة",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "اتجاه القبلة",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "بوصلة حية للكعبة",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // 5-Minute Periodic Dhikr Quick Status Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToAdhkar() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = "المذكّر التلقائي",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "المذكّر التلقائي كل ${PrayerTimeCalculator.toArabicDigits(settings.periodicDhikrIntervalMinutes)} دقائق",
                                style = MaterialTheme.typography.titleMedium
                            )
                            PeriodicDhikrSubtitleText(
                                isEnabled = settings.isPeriodicDhikrEnabled,
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
                            modifier = Modifier.testTag("home_periodic_dhikr_switch")
                        )
                    }
                }
            }

            // Prayer Times Section Header
            item {
                Text(
                    text = "مواقيت الصلاة اليوم",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Individual Prayer Rows
            val itemsList = schedule?.items ?: emptyList()
            items(itemsList, key = { it.prayer.name }) { item ->
                PrayerTimeRowCard(
                    item = item,
                    onToggleAdhan = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!granted) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        onTogglePrayerAdhan(item.prayer, enabled)
                    }
                )
            }

            // Adhan Audio & Calculation Settings Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "إعدادات الأذان",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "صوت الأذان وطريقة الحساب",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "اختر صوت المؤذن:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AdhanVoice.entries.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    pair.forEach { voice ->
                                        FilterChip(
                                            selected = settings.adhanVoice == voice,
                                            onClick = { onSelectAdhanVoice(voice) },
                                            label = {
                                                Text(
                                                    text = voice.arabicName,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onTestAdhan,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_adhan_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.PlayCircle, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("تجربة الأذان")
                            }

                            OutlinedButton(
                                onClick = onStopAdhan,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.StopCircle, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("إيقاف")
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        FilledTonalButton(
                            onClick = { showMethodDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("طريقة الحساب: ${settings.calculationMethod.arabicName}")
                        }

                        Spacer(Modifier.height(10.dp))

                        // Theme Selector Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Brightness4,
                                    contentDescription = "المظهر",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("مظهر التطبيق:", style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("system" to "تلقائي", "light" to "نهاري", "dark" to "ليلي").forEach { (key, label) ->
                                    FilterChip(
                                        selected = settings.darkModeSetting == key,
                                        onClick = { onUpdateDarkMode(key) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCityDialog) {
        CityPickerDialog(
            currentCityName = settings.cityName,
            onSelectCity = {
                onSelectCity(it)
                showCityDialog = false
            },
            onDismiss = { showCityDialog = false }
        )
    }

    if (showMethodDialog) {
        CalculationMethodDialog(
            currentMethod = settings.calculationMethod,
            onSelect = {
                onSelectMethod(it)
                showMethodDialog = false
            },
            onDismiss = { showMethodDialog = false }
        )
    }

    if (showQiblaDialog && schedule != null) {
        QiblaCompassDialog(
            qiblaBearingDegrees = schedule.qiblaDegrees,
            cityName = settings.cityName,
            onDismiss = { showQiblaDialog = false }
        )
    }
}

@Composable
private fun PrayerTimeRowCard(
    item: PrayerTimeItem,
    onToggleAdhan: (Boolean) -> Unit
) {
    val containerColor = if (item.isNext) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderModifier = if (item.isNext) {
        Modifier.border(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.secondary,
            shape = RoundedCornerShape(16.dp)
        )
    } else {
        Modifier
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (item.isNext) 4.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.prayer.arabicName,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (item.isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    AnimatedVisibility(visible = item.isNext) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                text = "القادمة",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = item.formattedTime,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (item.isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )

            if (item.prayer.isObligatoryAdhan) {
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { onToggleAdhan(!item.isAdhanEnabled) },
                    modifier = Modifier.testTag("toggle_adhan_${item.prayer.name.lowercase()}")
                ) {
                    Icon(
                        imageVector = if (item.isAdhanEnabled) {
                            Icons.Default.NotificationsActive
                        } else {
                            Icons.Default.NotificationsOff
                        },
                        contentDescription = "تفعيل أو تعطيل الأذان لـ ${item.prayer.arabicName}",
                        tint = if (item.isAdhanEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        }
                    )
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
        }
    }
}

@Composable
private fun CityPickerDialog(
    currentCityName: String,
    onSelectCity: (CityPreset) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) QuranCatalog.cities
        else QuranCatalog.cities.filter {
            it.arabicName.contains(query) || it.countryArabic.contains(query)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختر المدينة لمواقيت الصلاة") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("ابحث عن مدينة أو دولة...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                ) {
                    items(filtered, key = { it.id }) { city ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectCity(city) }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(city.arabicName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    city.countryArabic,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (city.arabicName == currentCityName) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "محددة",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

@Composable
private fun CalculationMethodDialog(
    currentMethod: CalculationMethod,
    onSelect: (CalculationMethod) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("طريقة حساب مواقيت الصلاة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CalculationMethod.entries.forEach { method ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(method) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = method.arabicName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        if (method == currentMethod) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "محددة",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

@Composable
private fun PrayerCountdownText(countdownFlow: StateFlow<CountdownUiState>) {
    val countdown by countdownFlow.collectAsStateWithLifecycle()
    Text(
        text = countdown.prayerCountdownText,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = Color(0xFFE3BE64)
    )
}

@Composable
private fun PeriodicDhikrSubtitleText(
    isEnabled: Boolean,
    countdownFlow: StateFlow<CountdownUiState>
) {
    val countdown by countdownFlow.collectAsStateWithLifecycle()
    Text(
        text = if (isEnabled && countdown.periodicCountdownText.isNotEmpty()) {
            "مفعّل • الذكر القادم خلال ${countdown.periodicCountdownText}"
        } else if (isEnabled) {
            "مفعّل • ينطق الذكر تلقائياً"
        } else {
            "اضغط للتفعيل لتذكيرك بالذكر كل ٥ دقايق"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
