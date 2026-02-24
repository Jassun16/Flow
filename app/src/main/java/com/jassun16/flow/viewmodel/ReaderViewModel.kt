package com.jassun16.flow.viewmodel

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dankito.readability4j.Readability4J
import javax.inject.Inject

data class ReaderUiState(
    val article: ArticleUiItem? = null,
    val fullContent: String? = null,
    val isLoadingContent: Boolean = false,
    val readabilityFailed: Boolean = false,
    val scrollPosition: Int = 0,
    val prevArticleId: Long? = null,
    val nextArticleId: Long? = null,
    val isBookmarked: Boolean = false,
    val summary: String? = null,
    val isSummarizing: Boolean = false,
    val isListening: Boolean = false,
    val snackbarMessage: String? = null
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repository: FlowRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val articleId: Long = savedStateHandle["articleId"] ?: 0L

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var tts: TextToSpeech? = null
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

            val allArticles = repository.getAllArticles().first()
            val index = allArticles.indexOfFirst { it.id == articleId }
            _uiState.update {
                it.copy(
                    prevArticleId = if (index > 0) allArticles[index - 1].id else null,
                    nextArticleId = if (index < allArticles.lastIndex) allArticles[index + 1].id else null
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

                // ── Tier 2+3 → then ContentCleaner ────────────────────
                val tier23      = ArticleExtractor.cleanHtml(finalHtml, article.url)
                val cleanedHtml = ContentCleaner.clean(tier23)
                // ──────────────────────────────────────────────────────

                _uiState.update {
                    it.copy(
                        fullContent       = cleanedHtml,
                        isLoadingContent  = false,
                        readabilityFailed = false
                    )
                }
                repository.markAsRead(article.id, article.feedId)
            }

            is Result.Error -> {
                val fallback = fetchFullPageContent(article.url)
                if (fallback != null) {
                    // ── Tier 2+3 → then ContentCleaner ────────────────
                    val tier23 = ArticleExtractor.cleanHtml(fallback, article.url)
                    _uiState.update {
                        it.copy(
                            fullContent       = ContentCleaner.clean(tier23),
                            isLoadingContent  = false,
                            readabilityFailed = false
                        )
                    }
                    // ──────────────────────────────────────────────────
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
        viewModelScope.launch {
            _uiState.update { it.copy(isSummarizing = true, summary = null) }
            try {
                val plainText = content.replace(Regex("<[^>]+>"), "")
                val wordCount = plainText.split(" ").size
                _uiState.update {
                    it.copy(
                        summary       = "Gemini Nano summary will be wired in Step 10 ($wordCount words in article)",
                        isSummarizing = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        summary       = "Summary unavailable. Ensure AICore app is updated.",
                        isSummarizing = false
                    )
                }
            }
        }
    }

    fun toggleListen(context: Context) {
        if (_uiState.value.isListening) stopTts()
        else startTts(context)
    }

    private fun startTts(context: Context) {
        val content = _uiState.value.fullContent ?: return
        val plainText = content.replace(Regex("<[^>]+>"), " ").trim()
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.speak(plainText, TextToSpeech.QUEUE_FLUSH, null, "flow_tts")
                _uiState.update { it.copy(isListening = true) }
            }
        }
    }

    private fun stopTts() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _uiState.update { it.copy(isListening = false) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
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
