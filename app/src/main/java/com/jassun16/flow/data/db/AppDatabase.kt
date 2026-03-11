package com.jassun16.flow.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jassun16.flow.BuildConfig

@Database(
    entities     = [Feed::class, Article::class],
    version      = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao

    companion object {

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                            "index_articles_url ON articles(url)"
                )
            }
        }

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE feeds ADD COLUMN lastFetched INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val STARTER_FEEDS = listOf(
            Triple("Android Police", "https://www.androidpolice.com/feed/",   "https://www.androidpolice.com"),
            Triple("9to5Google",     "https://9to5google.com/feed/",           "https://9to5google.com"),
            Triple("The Verge",      "https://www.theverge.com/rss/index.xml", "https://www.theverge.com"),
            Triple("TechCrunch",     "https://techcrunch.com/feed/",           "https://techcrunch.com")
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .apply {
                    if (BuildConfig.DEBUG) {
                        fallbackToDestructiveMigration(true)
                    }
                }
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        STARTER_FEEDS.forEach { (title, rssUrl, websiteUrl) ->
                            val faviconUrl = "https://www.google.com/s2/favicons?domain=$websiteUrl&sz=64"
                            db.execSQL("""
                                INSERT OR IGNORE INTO feeds
                                (title, rssUrl, websiteUrl, faviconUrl, unreadCount, addedAt)
                                VALUES ('$title', '$rssUrl', '$websiteUrl', '$faviconUrl', 0, 0)
                            """.trimIndent())
                        }
                    }
                })
                .build()
                .also { INSTANCE = it }
        }
    }
}
