package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.catalog.QuranCatalog
import com.example.data.local.BookmarkEntity
import com.example.domain.model.SurahInfo
import com.example.domain.prayer.PrayerTimeCalculator
import com.example.ui.theme.AmiriFontFamily

@Composable
fun QuranIndexScreen(
    lastReadPage: Int,
    bookmarks: List<BookmarkEntity>,
    cachedPagesCount: Int,
    onOpenMushafPage: (Int) -> Unit,
    onPlaySurah: (Int) -> Unit,
    onDeleteBookmark: (Int) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showJumpPageDialog by remember { mutableStateOf(false) }

    val filteredSurahs = remember(searchQuery) {
        if (searchQuery.isBlank()) QuranCatalog.surahs
        else QuranCatalog.surahs.filter {
            it.arabicName.contains(searchQuery.trim()) ||
                it.englishName.contains(searchQuery.trim(), ignoreCase = true) ||
                it.number.toString() == searchQuery.trim()
        }
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
            // Hero Mushaf Resume Card
            item {
                val lastSurah = QuranCatalog.surahForPage(lastReadPage)
                Card(
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_quran_frame),
                            contentDescription = "زخرفة المصحف الشريف",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xD60B3B2E),
                                            Color(0xEB08281F)
                                        )
                                    )
                                )
                        )

                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "المصحف الشريف",
                                style = MaterialTheme.typography.displayMedium,
                                color = Color(0xFFE3BE64)
                            )
                            Text(
                                text = "تقليب الصفحات • ٦٠٤ صفحة بالرسم العثماني (محفوظ تلقائياً: ${PrayerTimeCalculator.toArabicDigits(cachedPagesCount)} صفحة)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )

                            Spacer(Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { onOpenMushafPage(lastReadPage) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFD4AF37),
                                        contentColor = Color(0xFF0A241C)
                                    ),
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .testTag("resume_mushaf_button")
                                ) {
                                    Icon(Icons.Default.AutoStories, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "متابعة صفحة ${PrayerTimeCalculator.toArabicDigits(lastReadPage)} (${lastSurah.arabicName})",
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }

                                OutlinedButton(
                                    onClick = { showJumpPageDialog = true },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(0.8f)
                                        .testTag("jump_page_button")
                                ) {
                                    Text("انتقال لصفحة")
                                }
                            }
                        }
                    }
                }
            }

            // Segmented Tab Switcher (Surahs / Juz / Bookmarks)
            item {
                val tabs = listOf("السور (١١٤)", "الأجزاء (٣٠)", "العلامات (${PrayerTimeCalculator.toArabicDigits(bookmarks.size)})")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    tabs.forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size)
                        ) {
                            Text(label, maxLines = 1)
                        }
                    }
                }
            }

            if (selectedTab == 0) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("ابحث باسم السورة أو رقمها...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("surah_search_input")
                    )
                }

                items(filteredSurahs, key = { it.number }) { surah ->
                    SurahRowCard(
                        surah = surah,
                        onRead = { onOpenMushafPage(surah.startPage) },
                        onPlay = { onPlaySurah(surah.number) }
                    )
                }
            } else if (selectedTab == 1) {
                items(QuranCatalog.juzList, key = { it.number }) { juz ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenMushafPage(juz.startPage) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = PrayerTimeCalculator.toArabicDigits(juz.number),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(juz.arabicName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "يبدأ من سورة ${juz.startSurahName} • صفحة ${PrayerTimeCalculator.toArabicDigits(juz.startPage)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                if (bookmarks.isEmpty()) {
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
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Bookmark,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "لا توجد علامات محفوظة بعد",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "أثناء قراءة المصحف اضغط على أيقونة العلامة لحفظ مكان وقوفك",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(bookmarks, key = { it.id }) { bm ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenMushafPage(bm.page) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(bm.surahName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "صفحة ${PrayerTimeCalculator.toArabicDigits(bm.page)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف العلامة")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showJumpPageDialog) {
        var pageInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJumpPageDialog = false },
            title = { Text("الانتقال السريع إلى صفحة") },
            text = {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 3) pageInput = it },
                    label = { Text("رقم الصفحة (١ - ٦٠٤)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pageNum = pageInput.toIntOrNull()?.coerceIn(1, 604) ?: 1
                        showJumpPageDialog = false
                        onOpenMushafPage(pageNum)
                    }
                ) {
                    Text("فتح الصفحة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpPageDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun SurahRowCard(
    surah: SurahInfo,
    onRead: () -> Unit,
    onPlay: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRead() }
            .testTag("surah_card_${surah.number}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = PrayerTimeCalculator.toArabicDigits(surah.number),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "سورة ${surah.arabicName}",
                    fontFamily = AmiriFontFamily,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${surah.revelationLabel} • ${PrayerTimeCalculator.toArabicDigits(surah.ayahCount)} آية • صفحة ${PrayerTimeCalculator.toArabicDigits(surah.startPage)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape,
                modifier = Modifier
                    .size(44.dp)
                    .clickable { onPlay() }
                    .testTag("play_surah_${surah.number}")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "استماع لسورة ${surah.arabicName}",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}
