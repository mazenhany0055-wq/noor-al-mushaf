package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.catalog.QuranCatalog
import com.example.data.local.BookmarkEntity
import com.example.domain.model.MushafAyah
import com.example.domain.model.MushafPaperTheme
import com.example.domain.prayer.PrayerTimeCalculator
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.GoldAntique
import com.example.ui.theme.GoldBright
import com.example.ui.theme.MushafCream
import com.example.ui.theme.MushafCreamBorder
import com.example.ui.theme.NightMushafPaper
import com.example.ui.viewmodel.MushafPageUiState
import kotlinx.coroutines.launch

@Composable
fun MushafReaderScreen(
    initialPage: Int,
    pagesCache: Map<Int, MushafPageUiState>,
    bookmarks: List<BookmarkEntity>,
    fontSize: Float,
    paperTheme: MushafPaperTheme,
    onLoadPage: (Int) -> Unit,
    onRetryPage: (Int) -> Unit,
    onSaveLastReadPage: (Int) -> Unit,
    onToggleBookmark: (page: Int, surahNumber: Int, surahName: String) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onPaperThemeChange: (MushafPaperTheme) -> Unit,
    onPlaySurah: (Int) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val pagerState = rememberPagerState(
        initialPage = (initialPage - 1).coerceIn(0, 603),
        pageCount = { 604 }
    )
    val coroutineScope = rememberCoroutineScope()
    var showOverlay by remember { mutableStateOf(true) }
    var showDisplaySettings by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }

    val currentPageNumber = pagerState.currentPage + 1
    val currentSurah = remember(currentPageNumber) { QuranCatalog.surahForPage(currentPageNumber) }
    val isCurrentBookmarked = remember(bookmarks, currentPageNumber) {
        bookmarks.any { it.page == currentPageNumber }
    }

    LaunchedEffect(currentPageNumber) {
        onLoadPage(currentPageNumber)
        onSaveLastReadPage(currentPageNumber)
    }

    val paperBgColor = when (paperTheme) {
        MushafPaperTheme.CREAM -> MushafCream
        MushafPaperTheme.LIGHT -> Color(0xFFFFFFFF)
        MushafPaperTheme.NIGHT -> NightMushafPaper
    }
    val inkColor = when (paperTheme) {
        MushafPaperTheme.CREAM -> Color(0xFF1A1610)
        MushafPaperTheme.LIGHT -> Color(0xFF121815)
        MushafPaperTheme.NIGHT -> Color(0xFFEBE5D5)
    }
    val markerColor = when (paperTheme) {
        MushafPaperTheme.NIGHT -> GoldBright
        else -> GoldAntique
    }
    val borderTint = when (paperTheme) {
        MushafPaperTheme.NIGHT -> Color(0xFF364940)
        else -> MushafCreamBorder
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(paperBgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showOverlay = !showOverlay
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) { pageIdx ->
            val pageNumber = pageIdx + 1
            val pageState = pagesCache[pageNumber] ?: MushafPageUiState.Loading

            LaunchedEffect(pageNumber) {
                onLoadPage(pageNumber)
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                MushafSinglePageContent(
                    pageNumber = pageNumber,
                    pageState = pageState,
                    fontSize = fontSize,
                    inkColor = inkColor,
                    markerColor = markerColor,
                    borderColor = borderTint,
                    paperBgColor = paperBgColor,
                    onRetry = { onRetryPage(pageNumber) },
                    modifier = Modifier
                        .widthIn(max = 620.dp)
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                )
            }
        }

        // Top Floating Mushaf Toolbar
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("mushaf_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showJumpDialog = true }
                    ) {
                        Text(
                            text = "سورة ${currentSurah.arabicName}",
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = AmiriFontFamily
                        )
                        Text(
                            text = "صفحة ${PrayerTimeCalculator.toArabicDigits(currentPageNumber)} من ٦٠٤ (اضغط للانتقال)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { onPlaySurah(currentSurah.number) },
                        modifier = Modifier.testTag("mushaf_play_surah_button")
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "استماع للسورة",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = {
                            onToggleBookmark(
                                currentPageNumber,
                                currentSurah.number,
                                "سورة ${currentSurah.arabicName}"
                            )
                        },
                        modifier = Modifier.testTag("mushaf_bookmark_button")
                    ) {
                        Icon(
                            imageVector = if (isCurrentBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "حفظ العلامة",
                            tint = if (isCurrentBookmarked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = { showDisplaySettings = true },
                        modifier = Modifier.testTag("mushaf_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "إعدادات القراءة")
                    }
                }
            }
        }

        // Bottom Floating Page Indicator & Swipe Hint
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        enabled = currentPageNumber > 1,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage((currentPageNumber - 2).coerceAtLeast(0))
                            }
                        }
                    ) {
                        Text("السابقة")
                    }

                    Text(
                        text = "اسحب لتقليب الصفحات • ${PrayerTimeCalculator.toArabicDigits(currentPageNumber)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    TextButton(
                        enabled = currentPageNumber < 604,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage((currentPageNumber).coerceAtMost(603))
                            }
                        }
                    ) {
                        Text("التالية")
                    }
                }
            }
        }
    }

    if (showDisplaySettings) {
        AlertDialog(
            onDismissRequest = { showDisplaySettings = false },
            title = { Text("إعدادات عرض المصحف") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("حجم الخط: ${PrayerTimeCalculator.toArabicDigits(fontSize.toInt())}")
                    Slider(
                        value = fontSize,
                        onValueChange = onFontSizeChange,
                        valueRange = 18f..34f
                    )
                    Text("لون ورق المصحف:")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MushafPaperTheme.entries.forEach { theme ->
                            FilterChip(
                                selected = paperTheme == theme,
                                onClick = { onPaperThemeChange(theme) },
                                label = { Text(theme.arabicLabel) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDisplaySettings = false }) { Text("تم") }
            }
        )
    }

    if (showJumpDialog) {
        var pageInput by remember { mutableStateOf(currentPageNumber.toString()) }
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("انتقال إلى صفحة") },
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
                        val target = (pageInput.toIntOrNull() ?: currentPageNumber).coerceIn(1, 604)
                        showJumpDialog = false
                        coroutineScope.launch { pagerState.scrollToPage(target - 1) }
                    }
                ) { Text("انتقال") }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun MushafSinglePageContent(
    pageNumber: Int,
    pageState: MushafPageUiState,
    fontSize: Float,
    inkColor: Color,
    markerColor: Color,
    borderColor: Color,
    paperBgColor: Color,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .border(width = 1.5.dp, color = borderColor, shape = RoundedCornerShape(14.dp))
            .background(paperBgColor, shape = RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        when (pageState) {
            is MushafPageUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = markerColor)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "جاري تحميل صفحة ${PrayerTimeCalculator.toArabicDigits(pageNumber)}...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = inkColor.copy(alpha = 0.7f)
                    )
                }
            }

            is MushafPageUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = pageState.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = inkColor,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("إعادة المحاولة")
                    }
                }
            }

            is MushafPageUiState.Success -> {
                val ayahs = pageState.ayahs
                val juzNum = ayahs.firstOrNull()?.juz ?: 1
                val surahGroups = remember(ayahs) { ayahs.groupBy { it.surahNumber } }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Page Top Header (Surah & Juz)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = ayahs.firstOrNull()?.surahName ?: "",
                            fontFamily = AmiriFontFamily,
                            fontSize = 14.sp,
                            color = markerColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "الجزء ${PrayerTimeCalculator.toArabicDigits(juzNum)}",
                            fontFamily = AmiriFontFamily,
                            fontSize = 14.sp,
                            color = markerColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    surahGroups.forEach { (surahNum, surahAyahs) ->
                        val startsAtAyahOne = surahAyahs.firstOrNull()?.numberInSurah == 1
                        if (startsAtAyahOne) {
                            SurahOrnamentalBanner(
                                surahName = surahAyahs.first().surahName,
                                borderColor = borderColor,
                                markerColor = markerColor,
                                inkColor = inkColor
                            )
                            if (surahNum != 1 && surahNum != 9) {
                                Text(
                                    text = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
                                    fontFamily = AmiriFontFamily,
                                    fontSize = (fontSize - 1f).sp,
                                    color = inkColor,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                )
                            }
                        }

                        val formattedParagraph = buildMushafAnnotatedText(
                            ayahs = surahAyahs,
                            surahNum = surahNum,
                            inkColor = inkColor,
                            markerColor = markerColor
                        )
                        Text(
                            text = formattedParagraph,
                            fontFamily = AmiriFontFamily,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.75f).sp,
                            textAlign = TextAlign.Justify,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "— ${PrayerTimeCalculator.toArabicDigits(pageNumber)} —",
                        fontFamily = AmiriFontFamily,
                        fontSize = 15.sp,
                        color = markerColor,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(56.dp))
                }
            }
        }
    }
}

@Composable
private fun SurahOrnamentalBanner(
    surahName: String,
    borderColor: Color,
    markerColor: Color,
    inkColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.5.dp, markerColor, RoundedCornerShape(12.dp))
            .background(borderColor.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = surahName,
            fontFamily = AmiriFontFamily,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = inkColor
        )
    }
}

private fun buildMushafAnnotatedText(
    ayahs: List<MushafAyah>,
    surahNum: Int,
    inkColor: Color,
    markerColor: Color
) = buildAnnotatedString {
    ayahs.forEachIndexed { index, ayah ->
        var cleanText = ayah.text
        // AlQuran Cloud embeds Bismillah at the start of ayah 1 for Surahs 2..114 (except 9)
        if (surahNum != 1 && surahNum != 9 && ayah.numberInSurah == 1) {
            val bismillahPrefix = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ "
            if (cleanText.startsWith(bismillahPrefix)) {
                cleanText = cleanText.removePrefix(bismillahPrefix).trimStart()
            }
        }
        withStyle(SpanStyle(color = inkColor)) {
            append(cleanText)
            append(" ")
        }
        withStyle(SpanStyle(color = markerColor, fontWeight = FontWeight.Bold)) {
            append("﴿${PrayerTimeCalculator.toArabicDigits(ayah.numberInSurah)}﴾")
        }
        if (index < ayahs.lastIndex) {
            append(" ")
        }
    }
}
