package com.jassun16.flow.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jassun16.flow.data.db.Article
import com.jassun16.flow.data.repository.FlowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    repository: FlowRepository
) : ViewModel() {

    val bookmarkedArticles: StateFlow<List<ArticleUiItem>> =
        repository.getBookmarkedArticles()
            .map { articles -> articles.map { it.toUiItem() } }
            .stateIn(
                scope        = viewModelScope,
                started      = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
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
