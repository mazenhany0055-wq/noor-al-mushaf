package com.example.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidColor
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.repository.LessonsRepository
import com.example.domain.model.ReligiousLesson
import kotlinx.coroutines.delay

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LessonPlayerDialog(
    lesson: ReligiousLesson,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBrokenVideoDetected: (ReligiousLesson, String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val latestLesson by rememberUpdatedState(lesson)
    val latestOnBroken by rememberUpdatedState(onBrokenVideoDetected)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var isFullscreen by remember { mutableStateOf(false) }
    var isAudioVideoConfirmed by remember(lesson.youtubeVideoId) { mutableStateOf(false) }
    var customFullscreenView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var reportedCurrentVideoId by remember(lesson.youtubeVideoId) { mutableStateOf<String?>(null) }

    val cleanTitle = remember(lesson.title, lesson.scholarName) {
        LessonsRepository.cleanLessonTitle(lesson.title, lesson.scholarName)
            .ifBlank { lesson.scholarName }
    }

    // Request active media AudioFocus so YouTube audio plays unmuted at full clarity
    DisposableEffect(context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        var focusRequest: AudioFocusRequest? = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioManager != null) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .build()
                    )
                    .build()
                focusRequest = req
                audioManager.requestAudioFocus(req)
            }
        } catch (_: Exception) {
        }
        onDispose {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                    audioManager?.abandonAudioFocusRequest(focusRequest)
                }
            } catch (_: Exception) {
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Silent background health check: if the video is silent, broken, or stalled past 9s,
    // silently delete it and auto-play the next verified video without any alert or button.
    LaunchedEffect(lesson.youtubeVideoId) {
        delay(9_000L)
        if (!isAudioVideoConfirmed && reportedCurrentVideoId != lesson.youtubeVideoId) {
            reportedCurrentVideoId = lesson.youtubeVideoId
            latestOnBroken(latestLesson, "silent_or_stalled")
        }
    }

    LaunchedEffect(isFullscreen) {
        activity?.requestedOrientation = if (isFullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler {
        if (customFullscreenView != null) {
            customViewCallback?.onCustomViewHidden()
            customFullscreenView = null
            isFullscreen = false
        } else if (isFullscreen) {
            isFullscreen = false
        } else {
            onClose()
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (isFullscreen) Color.Black else MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isFullscreen) Modifier else Modifier.statusBarsPadding())
            ) {
                if (!isFullscreen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.testTag("close_lesson_player_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "العودة لقائمة الدروس"
                            )
                        }
                        Text(
                            text = lesson.scholarName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { isFullscreen = true },
                            modifier = Modifier.testTag("fullscreen_lesson_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "ملء الشاشة"
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isFullscreen) Modifier.weight(1f)
                            else Modifier.aspectRatio(16f / 9f)
                        )
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (customFullscreenView != null) {
                        AndroidView(
                            factory = { ctx ->
                                FrameLayout(ctx).apply {
                                    setBackgroundColor(AndroidColor.BLACK)
                                }
                            },
                            update = { container ->
                                val view = customFullscreenView ?: return@AndroidView
                                if (view.parent !== container) {
                                    (view.parent as? ViewGroup)?.removeView(view)
                                    container.removeAllViews()
                                    container.addView(
                                        view,
                                        FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    setBackgroundColor(AndroidColor.BLACK)
                                    keepScreenOn = true
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        mediaPlaybackRequiresUserGesture = false
                                        loadWithOverviewMode = true
                                        useWideViewPort = true
                                        cacheMode = WebSettings.LOAD_DEFAULT
                                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                        javaScriptCanOpenWindowsAutomatically = false
                                    }
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            val host = request?.url?.host.orEmpty()
                                            return !host.contains("youtube.com") && !host.contains("youtube-nocookie.com")
                                        }
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                            if (view != null) {
                                                customFullscreenView = view
                                                customViewCallback = callback
                                                isFullscreen = true
                                            }
                                        }

                                        override fun onHideCustomView() {
                                            customViewCallback?.onCustomViewHidden()
                                            customFullscreenView = null
                                            customViewCallback = null
                                            isFullscreen = false
                                        }
                                    }
                                    val bridge = YouTubePlayerJsBridge(
                                        onReady = { durationSec ->
                                            mainHandler.post {
                                                val tooShortOrTooLong =
                                                    durationSec <= 0.5 || durationSec > LessonsRepository.MAX_LESSON_DURATION_SECONDS
                                                if (tooShortOrTooLong && reportedCurrentVideoId != latestLesson.youtubeVideoId) {
                                                    reportedCurrentVideoId = latestLesson.youtubeVideoId
                                                    latestOnBroken(latestLesson, "invalid_duration")
                                                }
                                            }
                                        },
                                        onConfirmedHealthy = {
                                            mainHandler.post {
                                                isAudioVideoConfirmed = true
                                            }
                                        },
                                        onError = { code ->
                                            mainHandler.post {
                                                if (reportedCurrentVideoId != latestLesson.youtubeVideoId) {
                                                    reportedCurrentVideoId = latestLesson.youtubeVideoId
                                                    latestOnBroken(latestLesson, "error_$code")
                                                }
                                            }
                                        },
                                        onSilentOrStalled = { reason ->
                                            mainHandler.post {
                                                if (reportedCurrentVideoId != latestLesson.youtubeVideoId) {
                                                    reportedCurrentVideoId = latestLesson.youtubeVideoId
                                                    latestOnBroken(latestLesson, reason)
                                                }
                                            }
                                        }
                                    )
                                    addJavascriptInterface(bridge, "AndroidYT")
                                    tag = ""
                                }
                            },
                            update = { webView ->
                                val loadedId = webView.tag as? String
                                if (loadedId != lesson.youtubeVideoId) {
                                    webView.tag = lesson.youtubeVideoId
                                    val html = buildYouTubePlayerHtml(lesson.youtubeVideoId)
                                    webView.loadDataWithBaseURL(
                                        "https://www.youtube.com",
                                        html,
                                        "text/html",
                                        "utf-8",
                                        null
                                    )
                                }
                            },
                            onRelease = { webView ->
                                webView.loadUrl("about:blank")
                                webView.destroy()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (isFullscreen && customFullscreenView == null) {
                        IconButton(
                            onClick = { isFullscreen = false },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FullscreenExit,
                                contentDescription = "الخروج من ملء الشاشة",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Clean, minimal below-video area: ONLY title + Save to Favorites button
                if (!isFullscreen) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = cleanTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        FilledTonalButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("player_favorite_lesson_button")
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (isFavorite) "في المفضلة ✓" else "حفظ في المفضلة")
                        }
                    }
                }
            }
        }
    }
}

private class YouTubePlayerJsBridge(
    private val onReady: (Double) -> Unit,
    private val onConfirmedHealthy: () -> Unit,
    private val onError: (Int) -> Unit,
    private val onSilentOrStalled: (String) -> Unit
) {
    @JavascriptInterface
    fun onPlayerReady(durationSec: Double) {
        onReady(durationSec)
    }

    @JavascriptInterface
    fun onVideoHealthy() {
        onConfirmedHealthy()
    }

    @JavascriptInterface
    fun onPlaybackError(code: Int) {
        onError(code)
    }

    @JavascriptInterface
    fun onSilentDetected(reason: String) {
        onSilentOrStalled(reason)
    }
}

private fun buildYouTubePlayerHtml(videoId: String): String {
    val cleanId = videoId.replace(Regex("[^A-Za-z0-9_-]"), "")
    return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<style>
  html, body { margin:0; padding:0; width:100%; height:100%; background:#000; overflow:hidden; }
  iframe { position:absolute; top:0; left:0; width:100%; height:100%; border:0; }
</style>
</head>
<body>
<iframe id="ytplayer"
  src="https://www.youtube.com/embed/$cleanId?autoplay=1&playsinline=1&controls=1&rel=0&modestbranding=1&fs=1&enablejsapi=1&vq=hd720&origin=https://www.youtube.com"
  allow="autoplay; encrypted-media; fullscreen"
  allowfullscreen>
</iframe>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
  var player;
  var playStartTime = -1;
  var healthChecked = false;
  var healthInterval = null;
  var autoPlayTriggered = false;

  function onYouTubeIframeAPIReady() {
    player = new YT.Player('ytplayer', {
      events: {
        'onReady': onPlayerReady,
        'onStateChange': onPlayerStateChange,
        'onError': onPlayerError
      }
    });
  }

  function kickAutoPlay() {
    try {
      if (!player) return;
      player.unMute();
      player.setVolume(100);
      player.setPlaybackQuality('hd720');
      player.playVideo();
    } catch (e) {}
  }

  function onPlayerReady(event) {
    autoPlayTriggered = true;
    kickAutoPlay();
    try {
      var dur = player.getDuration() || 0;
      if (window.AndroidYT) window.AndroidYT.onPlayerReady(dur);
    } catch (e) {}
  }

  function onPlayerStateChange(event) {
    if ((event.data === YT.PlayerState.CUED || event.data === YT.PlayerState.UNSTARTED) && !healthChecked) {
      kickAutoPlay();
    }
    if (event.data === YT.PlayerState.PLAYING && !healthChecked) {
      try {
        player.unMute();
        player.setVolume(100);
        playStartTime = player.getCurrentTime() || 0;
        if (healthInterval) clearInterval(healthInterval);
        var checks = 0;
        healthInterval = setInterval(function() {
          checks++;
          try {
            if (player.isMuted()) {
              player.unMute();
              player.setVolume(100);
            }
            var nowTime = player.getCurrentTime() || 0;
            var dur = player.getDuration() || 0;
            var vol = player.getVolume();
            if (dur > 900) {
              clearInterval(healthInterval);
              if (window.AndroidYT) window.AndroidYT.onSilentDetected("over_15_min");
              return;
            }
            if (dur > 1 && nowTime > playStartTime + 0.25 && !player.isMuted() && vol > 0) {
              healthChecked = true;
              clearInterval(healthInterval);
              if (window.AndroidYT) window.AndroidYT.onVideoHealthy();
            } else if (checks >= 6) {
              clearInterval(healthInterval);
              if (player.isMuted() || vol === 0 || dur <= 1) {
                if (window.AndroidYT) window.AndroidYT.onSilentDetected("silent");
              }
            }
          } catch (e) {}
        }, 650);
      } catch (e) {}
    }
  }

  function onPlayerError(event) {
    if (healthInterval) clearInterval(healthInterval);
    if (window.AndroidYT) {
      window.AndroidYT.onPlaybackError(event.data || -1);
    }
  }
</script>
</body>
</html>
""".trimIndent()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
