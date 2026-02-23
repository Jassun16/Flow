package com.jassun16.flow.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {

    // ── Fetch Queries ──────────────────────────────────────────────────────

    // All articles across all feeds, newest first
    @Query("SELECT * FROM articles ORDER BY publishedAt DESC")
    fun getAllArticles(): Flow<List<Article>>

    // Articles for a specific feed only
    @Query("SELECT * FROM articles WHERE feedId = :feedId ORDER BY publishedAt DESC")
    fun getArticlesByFeed(feedId: Long): Flow<List<Article>>

    // Bookmarked articles only
    @Query("SELECT * FROM articles WHERE isBookmarked = 1 ORDER BY publishedAt DESC")
    fun getBookmarkedArticles(): Flow<List<Article>>

    // Single article by ID (for opening reader)
    @Query("SELECT * FROM articles WHERE id = :articleId")
    suspend fun getArticleById(articleId: Long): Article?

    // ── Insert / Update ────────────────────────────────────────────────────

    // IGNORE = skip if article with same URL already exists (no duplicates on refresh)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticles(articles: List<Article>)

    @Update
    suspend fun updateArticle(article: Article)

    // ── Read / Unread ──────────────────────────────────────────────────────

    @Query("UPDATE articles SET isRead = 1 WHERE id = :articleId")
    suspend fun markAsRead(articleId: Long)

    @Query("UPDATE articles SET isRead = 0 WHERE id = :articleId")
    suspend fun markAsUnread(articleId: Long)

    // Mark ALL articles in a feed as read (hamburger menu option)
    @Query("UPDATE articles SET isRead = 1 WHERE feedId = :feedId")
    suspend fun markAllAsRead(feedId: Long)

    // Mark ALL articles across all feeds as read
    @Query("UPDATE articles SET isRead = 1")
    suspend fun markAllAsReadGlobal()

    // ── Bookmarks ──────────────────────────────────────────────────────────

    @Query("UPDATE articles SET isBookmarked = :isBookmarked WHERE id = :articleId")
    suspend fun updateBookmark(articleId: Long, isBookmarked: Boolean)

    // ── Scroll Position Memory ─────────────────────────────────────────────

    @Query("UPDATE articles SET scrollPosition = :position WHERE id = :articleId")
    suspend fun saveScrollPosition(articleId: Long, position: Int)

    // ── Full Content (Readability) ─────────────────────────────────────────

    @Query("UPDATE articles SET fullContent = :content WHERE id = :articleId")
    suspend fun saveFullContent(articleId: Long, content: String)

    // ── Cleanup ────────────────────────────────────────────────────────────

    // Delete articles older than 30 days BUT preserve bookmarks forever
    @Query("""
        DELETE FROM articles 
        WHERE fetchedAt < :cutoffTimestamp 
        AND isBookmarked = 0
    """)
    suspend fun deleteOldArticles(cutoffTimestamp: Long)

    // ── Count ──────────────────────────────────────────────────────────────

    @Query("SELECT COUNT(*) FROM articles WHERE feedId = :feedId AND isRead = 0")
    suspend fun getUnreadCountForFeed(feedId: Long): Int
}