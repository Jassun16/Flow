package com.jassun16.flow.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities  = [Feed::class, Article::class],
    version   = 1,
    exportSchema = false   // set true later if you want schema version history files
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao

    companion object {
        @Volatile   // ensures all threads see the latest value immediately
        private var INSTANCE: AppDatabase? = null

        // Pre-loaded RSS feeds for first launch
        private val STARTER_FEEDS = listOf(
            Triple("Android Police",  "https://www.androidpolice.com/feed/",   "https://www.androidpolice.com"),
            Triple("9to5Google",      "https://9to5google.com/feed/",           "https://9to5google.com"),
            Triple("The Verge",       "https://www.theverge.com/rss/index.xml", "https://www.theverge.com"),
            Triple("TechCrunch",      "https://techcrunch.com/feed/",           "https://techcrunch.com")
        )

        fun getDatabase(context: Context): AppDatabase {
            // If instance exists, return it immediately (fast path)
            return INSTANCE ?: synchronized(this) {
                // Double-check inside lock (thread safety)
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "flow_database"
            )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Pre-populate starter feeds directly via SQL
                        // Bypasses the INSTANCE null timing issue entirely
                        STARTER_FEEDS.forEach { (title, rssUrl, websiteUrl) ->
                            val faviconUrl = "https://www.google.com/s2/favicons?domain=$websiteUrl&sz=64"
                            db.execSQL("""
                    INSERT OR IGNORE INTO feeds 
                    (title, rssUrl, websiteUrl, faviconUrl, unreadCount, addedAt)
                    VALUES ('$title', '$rssUrl', '$websiteUrl', '$faviconUrl', 0, ${System.currentTimeMillis()})
                """.trimIndent())
                        }
                    }
                })
                .build()
        }

    }
}
