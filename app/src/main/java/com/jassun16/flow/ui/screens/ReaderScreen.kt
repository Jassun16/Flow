package com.jassun16.flow.ui.screens

import android.app.Activity
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.jassun16.flow.ui.components.TimeUtils
import com.jassun16.flow.viewmodel.ReaderViewModel
import kotlin.math.abs

@Composable
fun ReaderScreen(
    articleId:  Long,
    onBack:     () -> Unit,
    onNavigate: (Long) -> Unit
) {
    val context   = LocalContext.current
    val activity  = context as? Activity
    val window    = activity?.window
    val viewModel = hiltViewModel<ReaderViewModel>()
    val uiState   by viewModel.uiState.collectAsState()
    val isDark    = isSystemInDarkTheme()

    var showBars          by remember { mutableStateOf(true) }
    var lastSavedPosition by remember { mutableIntStateOf(0) }
    var loadedHtml        by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        window?.let { w ->
            WindowCompat.setDecorFitsSystemWindows(w, false)
            WindowInsetsControllerCompat(w, w.decorView).apply {
                hide(
                    WindowInsetsCompat.Type.statusBars() or
                            WindowInsetsCompat.Type.navigationBars()
                )
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            window?.let { w ->
                WindowCompat.setDecorFitsSystemWindows(w, true)
                WindowInsetsControllerCompat(w, w.decorView)
                    .show(
                        WindowInsetsCompat.Type.statusBars() or
                                WindowInsetsCompat.Type.navigationBars()
                    )
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        when {

            uiState.isLoadingContent -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Loading article...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── CHANGED: Tier 1 via WebView — runs Readability.js on page ──
            uiState.readabilityFailed -> {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory  = { ctx ->
                        WebView(ctx).apply {
                            webViewClient              = WebViewClient()
                            settings.javaScriptEnabled = true
                            loadUrl(uiState.article?.url ?: "")
                        }
                    }
                )
            }

            // ── END CHANGE ──────────────────────────────────────────────────

            uiState.fullContent != null -> {
                val article = uiState.article
                val finalHtml = if (article != null) {
                    buildReaderHtml(
                        title       = article.title,
                        feedTitle   = article.feedTitle,
                        timeAgo     = TimeUtils.timeAgo(article.publishedAt),
                        readingTime = article.readingTimeMinutes,
                        summary     = uiState.summary,
                        content     = uiState.fullContent!!,
                        isDarkMode  = isDark
                    )
                } else ""

                val gestureDetector = remember {
                    GestureDetector(
                        context,
                        object : GestureDetector.SimpleOnGestureListener() {
                            override fun onSingleTapUp(e: MotionEvent): Boolean {
                                showBars = !showBars
                                return true
                            }
                        }
                    )
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory  = { ctx ->
                        WebView(ctx).apply {
                            isVerticalScrollBarEnabled   = false
                            isHorizontalScrollBarEnabled = false
                            settings.apply {
                                javaScriptEnabled    = false
                                loadWithOverviewMode = true
                                useWideViewPort      = true
                                setSupportZoom(false)
                                builtInZoomControls  = false
                                displayZoomControls  = false
                            }
                            setOnTouchListener { v, event ->
                                gestureDetector.onTouchEvent(event)
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                                v.onTouchEvent(event)
                                true
                            }
                        }
                    },
                    update = { webView ->
                        webView.setOnScrollChangeListener { _, _, newY, _, oldY ->
                            val delta = newY - oldY
                            when {
                                newY <= 60  -> showBars = true
                                delta > 12  -> showBars = false
                                delta < -12 -> showBars = true
                            }
                            if (abs(newY - lastSavedPosition) > 50) {
                                lastSavedPosition = newY
                                viewModel.saveScrollPosition(newY)
                            }
                        }

                        webView.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (uiState.scrollPosition > 0) {
                                    view?.scrollTo(0, uiState.scrollPosition)
                                }
                            }
                        }

                        if (finalHtml.isNotEmpty() && finalHtml != loadedHtml) {
                            loadedHtml = finalHtml
                            webView.loadDataWithBaseURL(
                                null, finalHtml, "text/html", "UTF-8", null
                            )
                        }
                    }
                )
            }
        }

        // ── Floating Top Bar ──────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showBars,
            modifier = Modifier.align(Alignment.TopCenter),
            enter    = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit     = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isDark) Color(0xE6000000)
                        else Color(0xE6FFFFFF)
                    )
                    .statusBarsPadding()
            ) {
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isDark) Color.White else Color.Black
                        )
                    }
                    Spacer(Modifier.weight(1f))

                    IconButton(onClick = { viewModel.toggleListen(context) }) {
                        Icon(
                            if (uiState.isListening) Icons.Default.StopCircle
                            else Icons.Default.PlayCircle,
                            contentDescription = "Listen",
                            tint = if (isDark) Color.White else Color.Black
                        )
                    }

                    IconButton(onClick = { viewModel.generateSummary() }) {
                        if (uiState.isSummarizing) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "Summarize",
                                tint = if (isDark) Color.White else Color.Black
                            )
                        }
                    }

                    IconButton(onClick = { viewModel.toggleBookmark() }) {
                        Icon(
                            if (uiState.isBookmarked) Icons.Default.Bookmark
                            else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (uiState.isBookmarked)
                                MaterialTheme.colorScheme.primary
                            else if (isDark) Color.White else Color.Black
                        )
                    }

                    IconButton(onClick = { viewModel.shareArticle(context) }) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share",
                            tint = if (isDark) Color.White else Color.Black
                        )
                    }
                }
            }
        }

        // ── Floating Bottom Bar ───────────────────────────────────────────
        if (uiState.prevArticleId != null || uiState.nextArticleId != null) {
            AnimatedVisibility(
                visible  = showBars,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter    = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit     = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isDark) Color(0xE6000000)
                            else Color(0xE6FFFFFF)
                        )
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick  = { uiState.prevArticleId?.let { onNavigate(it) } },
                            enabled  = uiState.prevArticleId != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Previous", style = MaterialTheme.typography.labelMedium)
                        }

                        VerticalDivider(
                            modifier  = Modifier.height(20.dp),
                            thickness = 0.5.dp,
                            color     = if (isDark) Color(0xFF3A3A3A) else Color(0xFFCCCCCC)
                        )

                        TextButton(
                            onClick  = { uiState.nextArticleId?.let { onNavigate(it) } },
                            enabled  = uiState.nextArticleId != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Next", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ── HTML / CSS Builder ────────────────────────────────────────────────────────
private fun buildReaderHtml(
    title:       String,
    feedTitle:   String,
    timeAgo:     String,
    readingTime: Int,
    summary:     String?,
    content:     String,
    isDarkMode:  Boolean
): String {
    val bgColor    = if (isDarkMode) "#0D0D0D" else "#FFFFFF"
    val textColor  = if (isDarkMode) "#D1D1D6" else "#1C1C1E"
    val headColor  = if (isDarkMode) "#F2F2F7" else "#000000"
    val linkColor  = if (isDarkMode) "#64A0E4" else "#0066CC"
    val metaColor  = if (isDarkMode) "#8E8E93" else "#8E8E93"
    val divColor   = if (isDarkMode) "#2C2C2E" else "#E5E5EA"
    val quoteBg    = if (isDarkMode) "#1C1C1E" else "#F2F2F7"
    val codeBg     = if (isDarkMode) "#1C1C1E" else "#F2F2F7"
    val summaryBg  = if (isDarkMode) "#0D2218" else "#F0FFF4"
    val summaryClr = if (isDarkMode) "#4ADE80" else "#166534"

    val summaryBlock = summary?.let {
        """
        <div class="summary-card">
            <div class="summary-label">✨ AI Summary</div>
            <div class="summary-body">$it</div>
        </div>
        """.trimIndent()
    } ?: ""

    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
        <style>
            *, *::before, *::after {
                box-sizing: border-box;
                -webkit-tap-highlight-color: transparent;
            }
            html, body {
                background: $bgColor;
                color: $textColor;
                margin: 0; padding: 0;
            }
            body {
                font-family: Georgia, 'Times New Roman', serif;
                font-size: 18px;
                line-height: 1.78;
                padding: 72px 22px 100px 22px;
                -webkit-text-size-adjust: none;
                word-break: break-word;
                overflow-wrap: break-word;
            }
            .article-title {
                font-family: -apple-system, system-ui, sans-serif;
                font-size: 23px;
                font-weight: 700;
                line-height: 1.3;
                letter-spacing: -0.4px;
                color: $headColor;
                margin: 0 0 10px 0;
            }
            .article-meta {
                font-family: -apple-system, system-ui, sans-serif;
                font-size: 12px;
                color: $metaColor;
                margin: 0 0 18px 0;
                line-height: 1.4;
            }
            .article-meta .source { color: $linkColor; font-weight: 600; }
            .section-divider { border: none; border-top: 1px solid $divColor; margin: 0 0 22px 0; }
            .summary-card { background: $summaryBg; border-radius: 12px; padding: 14px 16px; margin-bottom: 24px; }
            .summary-label { font-family: -apple-system, system-ui, sans-serif; font-size: 10px; font-weight: 700; letter-spacing: 0.6px; text-transform: uppercase; color: $summaryClr; margin-bottom: 7px; }
            .summary-body { font-family: -apple-system, system-ui, sans-serif; font-size: 15px; line-height: 1.6; color: $summaryClr; }
            p { margin: 0 0 20px 0; }
            h1, h2, h3, h4, h5, h6 { font-family: -apple-system, system-ui, sans-serif; color: $headColor; line-height: 1.3; letter-spacing: -0.2px; margin: 30px 0 10px 0; }
            h1 { font-size: 22px; font-weight: 700; }
            h2 { font-size: 19px; font-weight: 700; }
            h3 { font-size: 17px; font-weight: 600; }
            h4 { font-size: 15px; font-weight: 600; }
            img { display: block !important; width: 100% !important; max-width: 100% !important; height: auto !important; border-radius: 10px; margin: 20px auto !important; object-fit: cover; }
            img[width="1"], img[height="1"], img[width="0"], img[height="0"] { display: none !important; }
            figure { margin: 20px 0; padding: 0; }
            figcaption { font-family: -apple-system, system-ui, sans-serif; font-size: 13px; color: $metaColor; text-align: center; margin-top: 6px; font-style: italic; line-height: 1.4; }
            a { color: $linkColor; text-decoration: none; }
            blockquote { background: $quoteBg; border-left: 3px solid $linkColor; border-radius: 0 8px 8px 0; margin: 22px 0; padding: 10px 16px; font-style: italic; color: $metaColor; }
            blockquote p { margin: 0; }
            pre { background: $codeBg; border-radius: 8px; padding: 14px; overflow-x: auto; margin: 18px 0; font-size: 14px; line-height: 1.5; }
            code { background: $codeBg; border-radius: 4px; padding: 2px 5px; font-size: 0.88em; font-family: 'Courier New', monospace; }
            pre code { background: none; padding: 0; font-size: inherit; }
            table { width: 100%; border-collapse: collapse; margin: 20px 0; font-size: 15px; font-family: -apple-system, system-ui, sans-serif; }
            th, td { padding: 9px 12px; border-bottom: 1px solid $divColor; text-align: left; }
            th { font-weight: 600; color: $headColor; }
            ul, ol { padding-left: 22px; margin: 0 0 20px 0; }
            li { margin-bottom: 7px; }
            hr { border: none; border-top: 1px solid $divColor; margin: 28px 0; }
            strong, b { color: $headColor; }
            button, form, input[type="text"], input[type="email"], input[type="submit"], select, textarea { display: none !important; }
        </style>
        </head>
        <body>
            <h1 class="article-title">$title</h1>
            <div class="article-meta">
                <span class="source">$feedTitle</span>
                &nbsp;·&nbsp;$timeAgo
                &nbsp;·&nbsp;$readingTime min read
            </div>
            <hr class="section-divider"/>
            $summaryBlock
            $content
        </body>
        </html>
    """.trimIndent()
}
