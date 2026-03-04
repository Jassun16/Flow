package com.jassun16.flow.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities  = [Feed::class, Article::class],
    version   = 3,                // ← bumped from 2 to 3
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao

    companion object {

        // ── Migration 1 → 2: add unique index on article URL ─────────
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                            "index_articles_url ON articles(url)"
                )
            }
        }

        // ── Migration 2 → 3: add performance indices ──────────────────
        // These speed up the three most frequent queries:
        //   getAllArticles()         — ORDER BY publishedAt DESC
        //   getBookmarkedArticles()  — WHERE isBookmarked = 1
        //   getUnreadCountForFeed()  — WHERE feedId = ? AND isRead = 0
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "index_articles_publishedAt ON articles(publishedAt)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "index_articles_isBookmarked ON articles(isBookmarked)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "index_articles_feedId_isRead ON articles(feedId, isRead)"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val STARTER_FEEDS = listOf(
            Triple("Android Police",  "https://www.androidpolice.com/feed/",   "https://www.androidpolice.com"),
            Triple("9to5Google",      "https://9to5google.com/feed/",           "https://9to5google.com"),
            Triple("The Verge",       "https://www.theverge.com/rss/index.xml", "https://www.theverge.com"),
            Triple("TechCrunch",      "https://techcrunch.com/feed/",           "https://techcrunch.com")
        )

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "flow_database"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)   // ← added MIGRATION_2_3
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
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
                .also { INSTANCE = it }
        }
    }
}
