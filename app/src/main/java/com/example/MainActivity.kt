package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTimeFilled
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.components.MiniAudioPlayerBar
import com.example.ui.navigation.NoorRoutes
import com.example.ui.screens.AdhkarScreen
import com.example.ui.screens.AudioPlayerScreen
import com.example.ui.screens.HomePrayerScreen
import com.example.ui.screens.MushafReaderScreen
import com.example.ui.screens.QuranIndexScreen
import com.example.ui.screens.TasbihScreen
import com.example.ui.theme.NoorAlMushafTheme
import com.example.ui.viewmodel.AdhkarViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.QuranViewModel
import com.example.ui.viewmodel.TasbihViewModel

private data class BottomNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomTabs = listOf(
    BottomNavTab(NoorRoutes.HOME, "الصلاة", Icons.Filled.AccessTimeFilled, Icons.Outlined.AccessTime),
    BottomNavTab(NoorRoutes.QURAN, "المصحف", Icons.Filled.MenuBook, Icons.Outlined.MenuBook),
    BottomNavTab(NoorRoutes.AUDIO, "الاستماع", Icons.Filled.Headphones, Icons.Outlined.Headphones),
    BottomNavTab(NoorRoutes.TASBIH, "المسبحة", Icons.Filled.RadioButtonChecked, Icons.Outlined.RadioButtonChecked),
    BottomNavTab(NoorRoutes.ADHKAR, "الأذكار", Icons.Filled.AutoStories, Icons.Outlined.AutoStories)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val quranViewModel: QuranViewModel = viewModel()
            val tasbihViewModel: TasbihViewModel = viewModel()
            val adhkarViewModel: AdhkarViewModel = viewModel()

            val settings by mainViewModel.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings.darkModeSetting) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                NoorAlMushafTheme(darkTheme = darkTheme) {
                    NoorAlMushafAppShell(
                        mainViewModel = mainViewModel,
                        quranViewModel = quranViewModel,
                        tasbihViewModel = tasbihViewModel,
                        adhkarViewModel = adhkarViewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun NoorAlMushafAppShell(
    mainViewModel: MainViewModel,
    quranViewModel: QuranViewModel,
    tasbihViewModel: TasbihViewModel,
    adhkarViewModel: AdhkarViewModel
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val homeUiState by mainViewModel.homeUiState.collectAsStateWithLifecycle()
    val audioState by mainViewModel.audioState.collectAsStateWithLifecycle()
    val settings by mainViewModel.settings.collectAsStateWithLifecycle()
    val offlineSurahsByReciter by mainViewModel.offlineSurahsByReciter.collectAsStateWithLifecycle()
    val downloadProgress by mainViewModel.downloadProgress.collectAsStateWithLifecycle()
    val offlineAdhanIds by mainViewModel.offlineAdhanIds.collectAsStateWithLifecycle()
    val totalOfflineAudioBytes by mainViewModel.totalOfflineAudioBytes.collectAsStateWithLifecycle()
    val pagesCache by quranViewModel.pagesCache.collectAsStateWithLifecycle()
    val bookmarks by quranViewModel.bookmarks.collectAsStateWithLifecycle()
    val cachedPages by quranViewModel.cachedPageNumbers.collectAsStateWithLifecycle()
    val tasbihItems by tasbihViewModel.items.collectAsStateWithLifecycle()
    val totalLifetimeTasbih by tasbihViewModel.totalLifetimeCount.collectAsStateWithLifecycle()
    val adhkarState by adhkarViewModel.uiState.collectAsStateWithLifecycle()
    val availableSystemVoices by adhkarViewModel.availableSystemVoices.collectAsStateWithLifecycle()

    val isFullScreenReader = currentRoute?.startsWith("mushaf_reader") == true

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp && !isFullScreenReader

        Row(modifier = Modifier.fillMaxSize()) {
            if (useNavRail) {
                NavigationRail {
                    bottomTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTopLevel(tab.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag("nav_${tab.route}")
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                contentWindowInsets = if (isFullScreenReader) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
                bottomBar = {
                    if (!isFullScreenReader) {
                        Column {
                            MiniAudioPlayerBar(
                                audioState = audioState,
                                onTogglePlayPause = { mainViewModel.audioManager.togglePlayPause() },
                                onStop = { mainViewModel.audioManager.stopPlayback() },
                                onNextSurah = { mainViewModel.audioManager.playNextSurah() },
                                onPreviousSurah = { mainViewModel.audioManager.playPreviousSurah() },
                                onSeekTo = { mainViewModel.audioManager.seekTo(it) },
                                onSetSpeed = { mainViewModel.audioManager.setPlaybackSpeed(it) },
                                onToggleRepeat = { mainViewModel.audioManager.toggleRepeatSurah() },
                                onSetSleepTimer = { mainViewModel.audioManager.setSleepTimerMinutes(it) }
                            )
                            if (!useNavRail) {
                                NavigationBar {
                                    bottomTabs.forEach { tab ->
                                        val selected = currentRoute == tab.route
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { navigateTopLevel(tab.route) },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = tab.label
                                                )
                                            },
                                            label = { Text(tab.label) },
                                            modifier = Modifier.testTag("nav_${tab.route}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                val contentModifier = if (isFullScreenReader) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                }

                NavHost(
                    navController = navController,
                    startDestination = NoorRoutes.HOME,
                    modifier = contentModifier
                ) {
                    composable(NoorRoutes.HOME) {
                        HomePrayerScreen(
                            uiState = homeUiState,
                            countdownFlow = mainViewModel.countdownState,
                            offlineAdhanIds = offlineAdhanIds,
                            onSelectCity = { mainViewModel.selectCity(it) },
                            onDetectGps = { mainViewModel.detectGpsLocation() },
                            onSelectMethod = { mainViewModel.updateCalculationMethod(it) },
                            onSelectAdhanVoice = { mainViewModel.updateAdhanVoice(it) },
                            onDownloadAdhanOffline = { mainViewModel.downloadAdhanOffline(it) },
                            onTogglePrayerAdhan = { prayer, enabled ->
                                mainViewModel.togglePrayerAdhan(prayer, enabled)
                            },
                            onTestAdhan = { mainViewModel.testAdhanNow() },
                            onStopAdhan = { mainViewModel.stopAdhanNow() },
                            onRescheduleAlarms = { mainViewModel.rescheduleAlarms() },
                            onTogglePeriodicDhikr = { mainViewModel.setPeriodicDhikrEnabled(it) },
                            onOpenMushafPage = { page ->
                                navController.navigate(NoorRoutes.mushafReader(page))
                            },
                            onNavigateToAdhkar = { navigateTopLevel(NoorRoutes.ADHKAR) },
                            onUpdateDarkMode = { mainViewModel.updateDarkModeSetting(it) },
                            onDismissBanner = { mainViewModel.dismissBanner() }
                        )
                    }

                    composable(NoorRoutes.QURAN) {
                        QuranIndexScreen(
                            lastReadPage = settings.lastReadPage,
                            bookmarks = bookmarks,
                            cachedPagesCount = cachedPages.size,
                            isFullMushafBundled = mainViewModel.isFullMushafBundled,
                            onOpenMushafPage = { page ->
                                navController.navigate(NoorRoutes.mushafReader(page))
                            },
                            onPlaySurah = { surahNum ->
                                mainViewModel.audioManager.playSurah(surahNum, settings.selectedReciterId)
                            },
                            onDeleteBookmark = { quranViewModel.deleteBookmark(it) }
                        )
                    }

                    composable(NoorRoutes.AUDIO) {
                        AudioPlayerScreen(
                            selectedReciterId = settings.selectedReciterId,
                            audioState = audioState,
                            offlineSurahsByReciter = offlineSurahsByReciter,
                            downloadProgress = downloadProgress,
                            totalOfflineBytes = totalOfflineAudioBytes,
                            onSelectReciter = { mainViewModel.updateSelectedReciter(it) },
                            onPlaySurah = { surahNum, reciterId ->
                                mainViewModel.audioManager.playSurah(surahNum, reciterId)
                            },
                            onDownloadSurah = { reciterId, surahNum ->
                                mainViewModel.downloadSurahOffline(reciterId, surahNum)
                            },
                            onDownloadBatch = { reciterId, list ->
                                mainViewModel.downloadBatchSurahsOffline(reciterId, list)
                            },
                            onDeleteDownloadedSurah = { reciterId, surahNum ->
                                mainViewModel.deleteDownloadedSurah(reciterId, surahNum)
                            },
                            onTogglePlayPause = { mainViewModel.audioManager.togglePlayPause() },
                            onStop = { mainViewModel.audioManager.stopPlayback() },
                            onNextSurah = { mainViewModel.audioManager.playNextSurah() },
                            onPreviousSurah = { mainViewModel.audioManager.playPreviousSurah() },
                            onSeekTo = { mainViewModel.audioManager.seekTo(it) }
                        )
                    }

                    composable(NoorRoutes.TASBIH) {
                        TasbihScreen(
                            items = tasbihItems,
                            selectedId = settings.selectedTasbihId,
                            totalLifetimeCount = totalLifetimeTasbih,
                            soundEnabled = settings.tasbihSoundEnabled,
                            vibrationEnabled = settings.tasbihVibrationEnabled,
                            onSelectTasbih = { tasbihViewModel.selectTasbih(it) },
                            onIncrement = { tasbihViewModel.increment(it) },
                            onReset = { tasbihViewModel.resetCurrent(it) },
                            onUpdateTarget = { item, target -> tasbihViewModel.updateTarget(item, target) },
                            onAddCustom = { phrase, target -> tasbihViewModel.addCustomTasbih(phrase, target) },
                            onToggleSound = { tasbihViewModel.toggleSound(it) },
                            onToggleVibration = { tasbihViewModel.toggleVibration(it) }
                        )
                    }

                    composable(NoorRoutes.ADHKAR) {
                        AdhkarScreen(
                            adhkarState = adhkarState,
                            settings = settings,
                            countdownFlow = mainViewModel.countdownState,
                            availableSystemVoices = availableSystemVoices,
                            onSelectCategory = { adhkarViewModel.selectCategory(it) },
                            onDecrementDhikr = { adhkarViewModel.decrementDhikr(it) },
                            onResetCategory = { adhkarViewModel.resetCategory() },
                            onTogglePeriodicDhikr = { mainViewModel.setPeriodicDhikrEnabled(it) },
                            onSelectInterval = { mainViewModel.setPeriodicDhikrInterval(it) },
                            onSelectMode = { mainViewModel.setPeriodicDhikrMode(it) },
                            onTestDhikrNow = { mainViewModel.triggerDhikrNow() },
                            onSelectAdhkarVoice = { mainViewModel.updateAdhkarVoice(it) },
                            onSelectAdhkarQuranReciter = { mainViewModel.updateAdhkarQuranReciter(it) },
                            onToggleChimeBeforeVoice = { mainViewModel.updateAdhkarChimeBeforeVoice(it) },
                            onPreviewAdhkarVoice = { voiceId ->
                                adhkarViewModel.previewVoice(voiceId, settings.adhkarChimeBeforeVoice)
                            },
                            onSpeakSingleDhikr = { item ->
                                adhkarViewModel.speakSingleDhikr(
                                    item = item,
                                    voiceId = settings.adhkarVoiceId,
                                    quranReciterId = settings.adhkarQuranReciterId,
                                    chimeBefore = settings.adhkarChimeBeforeVoice
                                )
                            },
                            onToggleAutoPlayAll = {
                                adhkarViewModel.toggleAutoPlayCategory(
                                    voiceId = settings.adhkarVoiceId,
                                    quranReciterId = settings.adhkarQuranReciterId,
                                    chimeBefore = settings.adhkarChimeBeforeVoice
                                )
                            },
                            onStopSpeaking = { adhkarViewModel.stopSpeaking() }
                        )
                    }

                    composable(
                        route = NoorRoutes.MUSHAF_READER,
                        arguments = listOf(navArgument("page") { type = NavType.IntType })
                    ) { entry ->
                        val initialPage = entry.arguments?.getInt("page") ?: 1
                        MushafReaderScreen(
                            initialPage = initialPage,
                            pagesCache = pagesCache,
                            bookmarks = bookmarks,
                            fontSize = settings.mushafFontSize,
                            paperTheme = settings.mushafPaperTheme,
                            onLoadPage = { quranViewModel.loadPage(it) },
                            onRetryPage = { quranViewModel.retryPage(it) },
                            onSaveLastReadPage = { quranViewModel.saveLastReadPage(it) },
                            onToggleBookmark = { page, surahNum, surahName ->
                                quranViewModel.toggleBookmark(page, surahNum, surahName)
                            },
                            onFontSizeChange = { quranViewModel.updateFontSize(it) },
                            onPaperThemeChange = { quranViewModel.updatePaperTheme(it) },
                            onPlaySurah = { surahNum ->
                                mainViewModel.audioManager.playSurah(surahNum, settings.selectedReciterId)
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
