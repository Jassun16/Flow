package com.jassun16.flow.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jassun16.flow.data.db.Article
import com.jassun16.flow.data.db.Feed
import com.jassun16.flow.data.repository.FlowRepository
import com.jassun16.flow.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val feeds: ImmutableList<FeedUiItem> = persistentListOf(),
    val articles: ImmutableList<ArticleUiItem> = persistentListOf(),
    val filteredArticles: ImmutableList<ArticleUiItem> = persistentListOf(),
    val selectedFeedId: Long? = null,
    val isRefreshing: Boolean = false,
    val snackbarMessage: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: FlowRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _isAppReady = MutableStateFlow(false)
    val isAppReady: StateFlow<Boolean> = _isAppReady.asStateFlow()


    init {
        viewModelScope.launch {
            repository.getAllFeeds().collect { feeds ->
                _uiState.update { it.copy(feeds = feeds.map { f -> f.toUiItem() }.toImmutableList()) }
            }
        }
        viewModelScope.launch {
            repository.getAllArticles().collect { articles ->
                val uiArticles = articles.map { a -> a.toUiItem() }.toImmutableList()
                _uiState.update { it ->
                    val filtered = if (it.selectedFeedId == null) uiArticles
                    else uiArticles.filter { a -> a.feedId == it.selectedFeedId }.toImmutableList()
                    it.copy(
                        articles         = uiArticles,
                        filteredArticles = filtered,
                    )
                }
                _isAppReady.value = true
            }
        }
    }

    fun selectFeed(feedId: Long?) {
        _uiState.update { it ->
            val filtered = if (feedId == null) it.articles
            else it.articles.filter { a -> a.feedId == feedId }.toImmutableList()
            it.copy(selectedFeedId = feedId, filteredArticles = filtered)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            when (val result = repository.refreshAllFeeds()) {
                is Result.Success -> {
                    val count = result.data
                    _uiState.update {
                        it.copy(
                            isRefreshing    = false,
                            snackbarMessage = if (count > 0) "$count new articles fetched"
                            else "Already up to date"
                        )
                    }
                    viewModelScope.launch { repository.prefetchRecentArticles() }
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

    fun clearSnackbar()    { _uiState.update { it.copy(snackbarMessage = null) } }
    fun markAllAsRead()    { viewModelScope.launch { repository.markAllAsReadGlobal() } }
    fun markFeedAsRead(feedId: Long) { viewModelScope.launch { repository.markAllAsRead(feedId) } }

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
