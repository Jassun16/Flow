package com.jassun16.flow.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "articles",
    foreignKeys = [
        ForeignKey(
            entity        = Feed::class,
            parentColumns = ["id"],
            childColumns  = ["feedId"],
            // if a feed is deleted, delete all its articles too
            onDelete      = ForeignKey.CASCADE
        )
    ],
    indices = [Index("feedId")]   // speeds up queries filtering by feedId
)
data class Article(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val feedId: Long,               // which feed this belongs to
    val feedTitle: String,          // e.g. "TechCrunch" — stored here for fast display
    val feedFaviconUrl: String,     // stored here so card renders without joining tables

    val title: String,
    val url: String,                // original article URL
    val thumbnailUrl: String?,      // nullable — not all articles have images
    val excerpt: String,            // short description from RSS feed
    val fullContent: String?,       // full article text fetched via Readability (nullable until fetched)
    val author: String?,            // nullable — RSS doesn't always provide author
    val publishedAt: Long,          // stored as timestamp (milliseconds since 1970)
    val readingTimeMinutes: Int,    // estimated reading time calculated from word count

    val isRead: Boolean = false,
    val isBookmarked: Boolean = false,
    val scrollPosition: Int = 0,    // pixels scrolled — restored when re-opening article

    val fetchedAt: Long = System.currentTimeMillis() // when we downloaded this article
)