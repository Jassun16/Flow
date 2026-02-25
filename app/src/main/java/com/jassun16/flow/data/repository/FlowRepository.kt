package com.jassun16.flow.data.repository

import com.jassun16.flow.data.db.AppDatabase
import com.jassun16.flow.data.db.Article
import com.jassun16.flow.data.db.Feed
import com.jassun16.flow.data.network.ReadabilityFetcher
import com.jassun16.flow.data.network.ReadingTimeCalculator
import com.jassun16.flow.data.network.RssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first          // ← THIS was missing before
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

@Singleton
class FlowRepository @Inject constructor(
    private val database:           AppDatabase,
    private val rssParser:          RssParser,
    private val readabilityFetcher: ReadabilityFetcher
) {
    private val feedDao    = database.feedDao()
    private val articleDao = database.articleDao()

    // ── Feed Operations ────────────────────────────────────────────────────

    fun getAllFeeds(): Flow<List<Feed>> = feedDao.getAllFeeds()

    suspend fun addFeed(rssUrl: String): Result<Feed> {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUrl = rssUrl.trim()
                if (!cleanUrl.startsWith("http")) {
                    return@withContext Result.Error("Please enter a valid URL starting with http")
                }
                val domain     = extractDomain(cleanUrl)
                val faviconUrl = "https://www.google.com/s2/favicons?domain=$domain&sz=64"
                val testArticles = rssParser.parseFeed(0L, "", "", cleanUrl)
                val feedTitle    = if (testArticles.isNotEmpty()) {
                    testArticles.first().feedTitle.ifEmpty { domain }
                } else domain

                val feed = Feed(
                    title      = feedTitle,
                    rssUrl     = cleanUrl,
                    websiteUrl = "https://$domain",
                    faviconUrl = faviconUrl
                )
                val id = feedDao.insertFeed(feed)
                if (id == -1L) Result.Error("This feed is already added")
                else Result.Success(feed.copy(id = id))

            } catch (e: Exception) {
                Result.Error("Could not add feed: ${e.message}")
            }
        }
    }

    suspend fun deleteFeed(feed: Feed) {
        withContext(Dispatchers.IO) { feedDao.deleteFeed(feed) }
    }

    // ── Refresh ────────────────────────────────────────────────────────────

    suspend fun refreshAllFeeds(): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val feeds = feedDao.getAllFeeds().first()
                android.util.Log.d("FlowDebug", "feeds in DB: ${feeds.size}")

                if (feeds.isEmpty()) {
                    android.util.Log.d("FlowDebug", "NO FEEDS FOUND — seed data missing")
                    return@withContext Result.Success(0)
                }

                feeds.forEach { feed ->
                    android.util.Log.d("FlowDebug", "Feed: ${feed.title} | URL: ${feed.rssUrl}")
                }

                val deferredResults = feeds.map { feed ->
                    async {
                        android.util.Log.d("FlowDebug", "Fetching: ${feed.rssUrl}")
                        val articles = rssParser.parseFeed(
                            feedId         = feed.id,
                            feedTitle      = feed.title,
                            feedFaviconUrl = feed.faviconUrl,
                            rssUrl         = feed.rssUrl
                        )
                        android.util.Log.d("FlowDebug", "Got ${articles.size} articles from ${feed.title}")
                        articles
                    }
                }

                val allArticles = deferredResults.awaitAll().flatten()
                android.util.Log.d("FlowDebug", "Total articles fetched: ${allArticles.size}")

                val insertedIds = articleDao.insertArticles(allArticles)
                val newCount = insertedIds.count { it != -1L }

                feeds.forEach { feed ->
                    val count = articleDao.getUnreadCountForFeed(feed.id)
                    feedDao.updateUnreadCount(feed.id, count)
                }

                val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                articleDao.deleteOldArticles(thirtyDaysAgo)

                Result.Success(newCount)

            } catch (e: Exception) {
                android.util.Log.e("FlowDebug", "Refresh error: ${e.message}", e)
                Result.Error("Refresh failed: ${e.message}")
            }
        }
    }


    // ── Article Operations ─────────────────────────────────────────────────

    fun getAllArticles(): Flow<List<Article>> = articleDao.getAllArticles()

    fun getArticlesByFeed(feedId: Long): Flow<List<Article>> =
        articleDao.getArticlesByFeed(feedId)

    fun getBookmarkedArticles(): Flow<List<Article>> =
        articleDao.getBookmarkedArticles()

    suspend fun getArticleById(id: Long): Article? =
        withContext(Dispatchers.IO) { articleDao.getArticleById(id) }

    // ── Readability ────────────────────────────────────────────────────────

    suspend fun getFullContent(article: Article): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (!article.fullContent.isNullOrBlank()) {
                    return@withContext Result.Success(article.fullContent)
                }
                val readable = readabilityFetcher.fetch(article.url)
                if (readable.success) {
                    articleDao.saveFullContent(article.id, readable.content)
                    val updatedReadTime = ReadingTimeCalculator
                        .calculateFromText(readable.content)
                    articleDao.updateArticle(
                        article.copy(
                            fullContent        = readable.content,
                            readingTimeMinutes = updatedReadTime
                        )
                    )
                    Result.Success(readable.content)
                } else {
                    Result.Error("Could not load reader mode. Tap to open in browser view")
                }
            } catch (e: Exception) {
                Result.Error("Could not load reader mode. Tap to open in browser view")
            }
        }
    }

    // ── Read / Unread ──────────────────────────────────────────────────────

    suspend fun markAsRead(articleId: Long, feedId: Long) {
        withContext(Dispatchers.IO) {
            articleDao.markAsRead(articleId)
            refreshUnreadCount(feedId)
        }
    }

    suspend fun markAsUnread(articleId: Long, feedId: Long) {
        withContext(Dispatchers.IO) {
            articleDao.markAsUnread(articleId)
            refreshUnreadCount(feedId)
        }
    }

    suspend fun markAllAsRead(feedId: Long) {
        withContext(Dispatchers.IO) {
            articleDao.markAllAsRead(feedId)
            feedDao.updateUnreadCount(feedId, 0)
        }
    }

    suspend fun markAllAsReadGlobal() {
        withContext(Dispatchers.IO) {
            articleDao.markAllAsReadGlobal()
            // ✅ FIXED: .first() called directly inside coroutine body
            val feeds = feedDao.getAllFeeds().first()
            feeds.forEach { feedDao.updateUnreadCount(it.id, 0) }
        }
    }

    // ── Bookmarks ──────────────────────────────────────────────────────────

    suspend fun toggleBookmark(article: Article) {
        withContext(Dispatchers.IO) {
            articleDao.updateBookmark(article.id, !article.isBookmarked)
        }
    }

    // ── Scroll Position ────────────────────────────────────────────────────

    suspend fun saveScrollPosition(articleId: Long, position: Int) {
        withContext(Dispatchers.IO) {
            articleDao.saveScrollPosition(articleId, position)
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private suspend fun refreshUnreadCount(feedId: Long) {
        val count = articleDao.getUnreadCountForFeed(feedId)
        feedDao.updateUnreadCount(feedId, count)
    }

    private fun extractDomain(url: String): String {
        return try {
            val withoutProtocol = url.removePrefix("https://").removePrefix("http://")
            withoutProtocol.split("/").first().removePrefix("www.")
        } catch (_: Exception) { url }
    }
}
