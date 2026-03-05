package com.jassun16.flow.viewmodel

import androidx.compose.runtime.Immutable

@Immutable
data class FeedUiItem(
    val id: Long,
    val title: String,
    val faviconUrl: String,
    val unreadCount: Int
)

@Immutable
data class ArticleUiItem(
    val id: Long,
    val feedId: Long,
    val feedTitle: String,
    val feedFaviconUrl: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String?,
    val excerpt: String,
    val publishedAt: Long,
    val readingTimeMinutes: Int,
    val isRead: Boolean,
    val isBookmarked: Boolean,
    val scrollPosition: Int
)
