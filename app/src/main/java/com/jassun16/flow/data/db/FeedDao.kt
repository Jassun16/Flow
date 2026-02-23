package com.jassun16.flow.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {

    // Flow<> = live stream of data — UI auto-updates when feeds change
    @Query("SELECT * FROM feeds ORDER BY title ASC")
    fun getAllFeeds(): Flow<List<Feed>>

    @Query("SELECT * FROM feeds WHERE id = :feedId")
    suspend fun getFeedById(feedId: Long): Feed?

    // Insert returns the new row's ID — useful for pre-loading feeds
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFeed(feed: Feed): Long

    @Update
    suspend fun updateFeed(feed: Feed)

    @Delete
    suspend fun deleteFeed(feed: Feed)

    @Query("UPDATE feeds SET unreadCount = :count WHERE id = :feedId")
    suspend fun updateUnreadCount(feedId: Long, count: Int)

    @Query("SELECT COUNT(*) FROM articles WHERE feedId = :feedId AND isRead = 0")
    suspend fun getUnreadCount(feedId: Long): Int
}
