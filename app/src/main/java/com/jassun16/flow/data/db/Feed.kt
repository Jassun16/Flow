package com.jassun16.flow.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feeds")
data class Feed(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,          // e.g. "TechCrunch"
    val rssUrl: String,         // e.g. "https://techcrunch.com/feed/"
    val websiteUrl: String,     // e.g. "https://techcrunch.com"
    val faviconUrl: String,     // fetched from Google favicon service
    val unreadCount: Int = 0,   // cached count shown as badge in drawer
    val addedAt: Long = System.currentTimeMillis()
)