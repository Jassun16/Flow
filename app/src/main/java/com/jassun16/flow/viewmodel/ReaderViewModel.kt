package com.jassun16.flow.viewmodel

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jassun16.flow.data.db.Article
import com.jassun16.flow.data.network.ArticleExtractor
import com.jassun16.flow.data.repository.FlowRepository
import com.jassun16.flow.data.repository.Result
import com.jassun16.flow.util.ContentCleaner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dankito.readability4j.Readability4J
import javax.inject.Inject
import com.jassun16.flow.data.network.ReadingTimeCalculator
import com.jassun16.flow.util.GeminiNanoSummarizer
import com.jassun16.flow.util.SummarizerReadyState


data class ReaderUiState(
    val article: ArticleUiItem? = null,
    val fullContent: String? = null,
    val isLoadingContent: Boolean = false,
    val readabilityFailed: Boolean = false,
    val scrollPosition: Int = 0,
    val isBookmarked: Boolean = false,
    val summary: String? = null,
    val isSummarizing: Boolean = false,
    val snackbarMessage: String? = null
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repository: FlowRepository,
    private val geminiNanoSummarizer: GeminiNanoSummarizer,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val articleId: Long = savedStateHandle["articleId"] ?: 0L

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var rawArticle: Article? = null

    init {
        loadArticle()
    }

    // ── Load Article ───────────────────────────────────────────────────────

    private fun loadArticle() {
        viewModelScope.launch {
            val article = repository.getArticleById(articleId) ?: return@launch
            rawArticle = article

            _uiState.update {
                it.copy(
                    article        = article.toUiItem(),
                    scrollPosition = article.scrollPosition,
                    isBookmarked   = article.isBookmarked
                )
            }

            loadFullContent(article)
        }
    }

    // ── Load + clean full content ──────────────────────────────────────────

    private suspend fun loadFullContent(article: Article) {
        _uiState.update { it.copy(isLoadingContent = true) }

        when (val result = repository.getFullContent(article)) {
            is Result.Success -> {
                val rawHtml = result.data

                val finalHtml = if (rawHtml.length < 500) {
                    fetchFullPageContent(article.url) ?: rawHtml
                } else {
                    rawHtml
                }

                val tier23      = ArticleExtractor.cleanHtml(finalHtml, article.url)
                val cleanedHtml = ContentCleaner.clean(tier23)

                // ── TEMP DEBUG — remove after diagnosis ──
               // android.util.Log.d("FlowImageDebug", cleanedHtml.take(8000))
                // ─────────────────────────────────────────

                // ── ADD THIS: recalculate reading time from actual clean content ──
                val plainText       = cleanedHtml.replace(Regex("<[^>]+>"), " ").trim()
                val accurateMinutes = ReadingTimeCalculator.calculateFromText(plainText)
                repository.updateReadingTime(articleId, accurateMinutes)
                // ─────────────────────────────────────────────────────────────────


                _uiState.update {
                    it.copy(
                        fullContent       = cleanedHtml,
                        isLoadingContent  = false,
                        readabilityFailed = false,
                        article           = _uiState.value.article?.copy(readingTimeMinutes = accurateMinutes) // ← updates card instantly
                    )
                }
                repository.markAsRead(article.id, article.feedId)
            }

            is Result.Error -> {
                val fallback = fetchFullPageContent(article.url)
                if (fallback != null) {
                    val tier23      = ArticleExtractor.cleanHtml(fallback, article.url)
                    val cleanedHtml = ContentCleaner.clean(tier23)

                    // ── ADD THIS: same recalculation for the fallback path ──
                    val plainText       = cleanedHtml.replace(Regex("<[^>]+>"), " ").trim()
                    val accurateMinutes = ReadingTimeCalculator.calculateFromText(plainText)
                    repository.updateReadingTime(articleId, accurateMinutes)
                    // ───────────────────────────────────────────────────────

                    _uiState.update {
                        it.copy(
                            fullContent       = cleanedHtml,
                            isLoadingContent  = false,
                            readabilityFailed = false,
                            article           = _uiState.value.article?.copy(readingTimeMinutes = accurateMinutes) // ← updates card instantly
                        )
                    }
                    repository.markAsRead(article.id, article.feedId)
                } else {
                    _uiState.update {
                        it.copy(
                            isLoadingContent  = false,
                            readabilityFailed = true
                        )
                    }
                }
            }

            else -> _uiState.update { it.copy(isLoadingContent = false) }
        }
    }


    // ── Fetch full webpage and run Readability4J (Tier 1) ─────────────────

    private suspend fun fetchFullPageContent(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val connection = java.net.URL(url).openConnection()
                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/120.0.0.0 Mobile Safari/537.36"
                )
                connection.connectTimeout = 10_000
                connection.readTimeout    = 15_000
                val pageHtml = connection.getInputStream()
                    .bufferedReader(Charsets.UTF_8)
                    .readText()

                val parsed = Readability4J(url, pageHtml).parse()
                val extractedHtml = parsed.contentWithUtf8Encoding
                    ?: parsed.content
                    ?: return@withContext null

                ContentCleaner.clean(extractedHtml)

            } catch (e: Exception) {
                Log.w("ReaderViewModel", "fetchFullPageContent failed for $url", e)
                null
            }
        }
    }


    // ── Actions ───────────────────────────────────────────────────────────

    fun toggleBookmark() {
        viewModelScope.launch {
            rawArticle?.let { article ->
                repository.toggleBookmark(article)
                val newState = !_uiState.value.isBookmarked
                _uiState.update {
                    it.copy(
                        isBookmarked    = newState,
                        snackbarMessage = if (newState) "Saved to Bookmarks" else "Removed from Bookmarks"
                    )
                }
                rawArticle = repository.getArticleById(articleId)
            }
        }
    }

    fun saveScrollPosition(position: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(scrollPosition = position) }
            repository.saveScrollPosition(articleId, position)
        }
    }

    fun shareArticle(context: Context) {
        val article = _uiState.value.article ?: return
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type   = "text/plain"
            putExtra(Intent.EXTRA_TITLE, article.title)
            putExtra(Intent.EXTRA_TEXT,  "${article.title}\n${article.url}")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share article"))
    }

    fun generateSummary() {
        val content = _uiState.value.fullContent ?: return
        if (_uiState.value.isSummarizing) return   // prevent double-tap

        viewModelScope.launch {
            _uiState.update { it.copy(isSummarizing = true, summary = null) }

            // Strip HTML → plain text
            val plainText = content
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            if (plainText.length < 400) {
                _uiState.update {
                    it.copy(
                        isSummarizing   = false,
                        snackbarMessage = "Article is too short to summarize"
                    )
                }
                return@launch
            }

            when (val state = geminiNanoSummarizer.checkAndPrepare()) {
                is SummarizerReadyState.Unavailable -> {
                    _uiState.update {
                        it.copy(
                            isSummarizing   = false,
                            snackbarMessage = "Gemini Nano is not supported on this device"
                        )
                    }
                    return@launch
                }
                is SummarizerReadyState.Downloading -> {
                    _uiState.update {
                        it.copy(snackbarMessage = "Downloading AI model (~300 MB), please wait…")
                    }
                    // isSummarizing stays true — spinner remains while download completes
                    return@launch
                }
                is SummarizerReadyState.Error -> {
                    val fullError = buildString {
                        append(state.message)
                    }
                    val userMessage = when {
                        fullError.contains("FEATURE_NOT_FOUND", ignoreCase = true) ||
                                fullError.contains("606", ignoreCase = true) ->
                            "Gemini Nano is setting up. Connect to WiFi, plug in to charge, and try again in 30 minutes."
                        fullError.contains("UNAVAILABLE", ignoreCase = true) ->
                            "Gemini Nano is not available on this device."
                        fullError.contains("PREPARATION_ERROR", ignoreCase = true) ->
                            "Gemini Nano is not ready yet. Try again in a few minutes."
                        else ->
                            "Could not start summarization. Please try again."
                    }
                    _uiState.update {
                        it.copy(
                            isSummarizing   = false,
                            snackbarMessage = userMessage
                        )
                    }
                    return@launch
                }
                is SummarizerReadyState.Ready -> Unit   // fall through to inference
            }

            // Stream tokens into summary — user sees bullets build live
            try {
                geminiNanoSummarizer.summarize(plainText).collect { token ->
                    val current = _uiState.value.summary ?: ""
                    _uiState.update { it.copy(summary = current + token) }
                }
                _uiState.update { it.copy(isSummarizing = false) }
            } catch (e: Exception) {
                val fullError = buildString {
                    append(e.message ?: "")
                    append(e.cause?.message ?: "")
                    append(e.cause?.cause?.message ?: "")
                }
                val userMessage = when {
                    fullError.contains("FEATURE_NOT_FOUND", ignoreCase = true) ||
                            fullError.contains("606", ignoreCase = true) ->
                        "Gemini Nano is setting up. Connect to WiFi, plug in to charge, and try again in 30 minutes."
                    fullError.contains("UNAVAILABLE", ignoreCase = true) ->
                        "Gemini Nano is not available on this device."
                    fullError.contains("PREPARATION_ERROR", ignoreCase = true) ->
                        "Gemini Nano is not ready yet. Try again in a few minutes."
                    else ->
                        "Summarization failed. Please try again."
                }
                _uiState.update {
                    it.copy(
                        isSummarizing   = false,
                        snackbarMessage = userMessage
                    )
                }
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        geminiNanoSummarizer.close()
    }

    private fun Article.toUiItem() = ArticleUiItem(
        id                 = id,
        feedId             = feedId,
        feedTitle          = feedTitle,
        feedFaviconUrl     = feedFaviconUrl,
        title              = title,
        url                = url,
        thumbnailUrl       = thumbnailUrl,
        excerpt            = excerpt,
        publishedAt        = publishedAt,
        readingTimeMinutes = readingTimeMinutes,
        isRead             = isRead,
        isBookmarked       = isBookmarked,
        scrollPosition     = scrollPosition
    )
}
