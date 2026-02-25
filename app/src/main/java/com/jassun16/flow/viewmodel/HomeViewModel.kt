package com.jassun16.flow.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jassun16.flow.data.db.Article
import com.jassun16.flow.data.db.Feed
import com.jassun16.flow.data.repository.FlowRepository
import com.jassun16.flow.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val feeds: List<FeedUiItem> = emptyList(),
    val articles: List<ArticleUiItem> = emptyList(),
    val isRefreshing: Boolean = false,
    val snackbarMessage: String? = null,
    val shouldScrollToTop: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: FlowRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Collect feeds — auto-updates drawer badges whenever DB changes
        viewModelScope.launch {
            repository.getAllFeeds().collect { feeds ->
                _uiState.update { it.copy(feeds = feeds.map { f -> f.toUiItem() }) }
            }
        }
        // Collect articles — auto-updates list whenever DB changes
        viewModelScope.launch {
            repository.getAllArticles().collect { articles ->
                _uiState.update { it.copy(articles = articles.map { a -> a.toUiItem() }) }
            }
        }
    }

    // ── Actions ────────────────────────────────────────────────────────────

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            when (val result = repository.refreshAllFeeds()) {
                is Result.Success -> {
                    val count = result.data
                    _uiState.update {
                        it.copy(
                            isRefreshing     = false,
                            snackbarMessage  = if (count > 0) "$count new articles fetched"
                            else "Already up to date",
                            shouldScrollToTop = count > 0   // only scroll if genuinely new articles arrived
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(isRefreshing = false, snackbarMessage = result.message)
                    }
                }
                else -> _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun clearScrollToTop() {
        _uiState.update { it.copy(shouldScrollToTop = false) }
    }


    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun markAllAsRead() {
        viewModelScope.launch { repository.markAllAsReadGlobal() }
    }

    fun markFeedAsRead(feedId: Long) {
        viewModelScope.launch { repository.markAllAsRead(feedId) }
    }

    fun deleteFeed(feedItem: FeedUiItem) {
        viewModelScope.launch {
            val feed = Feed(
                id         = feedItem.id,
                title      = feedItem.title,
                rssUrl     = "",
                websiteUrl = "",
                faviconUrl = feedItem.faviconUrl
            )
            repository.deleteFeed(feed)
        }
    }

    fun addFeed(url: String) {
        viewModelScope.launch {
            when (val result = repository.addFeed(url)) {
                is Result.Error   -> _uiState.update { it.copy(snackbarMessage = result.message) }
                is Result.Success -> _uiState.update { it.copy(snackbarMessage = "Feed added successfully") }
                else -> {}
            }
        }
    }

    // ── Converters ─────────────────────────────────────────────────────────

    private fun Feed.toUiItem() = FeedUiItem(
        id          = id,
        title       = title,
        faviconUrl  = faviconUrl,
        unreadCount = unreadCount
    )

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
