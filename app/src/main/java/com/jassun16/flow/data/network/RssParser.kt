package com.jassun16.flow.data.network

import android.util.Xml
import com.jassun16.flow.data.db.Article
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RssParser @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val dateFormats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z",   Locale.ENGLISH),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ",        Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",      Locale.ENGLISH)
    )

    suspend fun parseFeed(
        feedId: Long,
        feedTitle: String,
        feedFaviconUrl: String,
        rssUrl: String
    ): List<Article> {
        return try {
            val xml = downloadFeed(rssUrl) ?: return emptyList()
            android.util.Log.d("FlowDebug", "Downloaded XML length: ${xml.length} for $rssUrl")
            parseXml(xml, feedId, feedTitle, feedFaviconUrl)
        } catch (e: Exception) {
            android.util.Log.e("FlowDebug", "parseFeed error: ${e.message}", e)
            emptyList()
        }
    }

    private fun downloadFeed(url: String): String? {
        return try {
            val request  = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android) Flow RSS Reader")
                .build()
            val response = client.newCall(request).execute()
            android.util.Log.d("FlowDebug", "HTTP ${response.code} for $url")
            if (response.isSuccessful) response.body?.string() else null
        } catch (e: Exception) {
            android.util.Log.e("FlowDebug", "downloadFeed error: ${e.message}")
            null
        }
    }

    private fun parseXml(
        xml: String,
        feedId: Long,
        feedTitle: String,
        feedFaviconUrl: String
    ): List<Article> {
        val articles = mutableListOf<Article>()

        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))

            // ── Mutable state per article ──────────────────────────────
            var currentTag   = ""
            var inItem       = false
            var title        = ""
            var link         = ""
            var description  = ""
            var pubDate      = ""
            var author       = ""
            var thumbnail: String? = null

            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {

                    XmlPullParser.START_TAG -> {
                        // Strip namespace prefix e.g. "media:thumbnail" → "media:thumbnail"
                        val tag = parser.name ?: ""
                        currentTag = tag.lowercase()

                        when (currentTag) {
                            "item", "entry" -> {
                                // Start of a new article — reset everything
                                inItem      = true
                                title       = ""
                                link        = ""
                                description = ""
                                pubDate     = ""
                                author      = ""
                                thumbnail   = null
                            }
                            "link" -> {
                                // Atom feeds: <link href="url"/> (attribute, not text)
                                val href = parser.getAttributeValue(null, "href")
                                if (inItem && href != null && link.isEmpty()) {
                                    link = href
                                }
                            }
                            "media:thumbnail", "media:content", "enclosure" -> {
                                val url = parser.getAttributeValue(null, "url")
                                if (inItem && url != null && thumbnail == null) {
                                    thumbnail = url
                                }
                            }
                        }
                    }

                    XmlPullParser.TEXT -> {
                        if (!inItem) {
                            eventType = parser.next()
                            continue
                        }
                        val text = parser.text?.trim() ?: ""
                        if (text.isEmpty()) {
                            eventType = parser.next()
                            continue
                        }

                        // currentTag tells us which field this text belongs to
                        when (currentTag) {
                            "title"            -> if (title.isEmpty())       title       = text
                            "link"             -> if (link.isEmpty())        link        = text
                            "description",
                            "summary",
                            "content",
                            "content:encoded"  -> if (description.isEmpty()) description = text
                            "pubdate",
                            "published",
                            "updated",
                            "dc:date"          -> if (pubDate.isEmpty())     pubDate     = text
                            "author",
                            "dc:creator",
                            "name"             -> if (author.isEmpty())      author      = text
                        }
                    }

                    XmlPullParser.CDSECT -> {
                        // CDATA sections — some feeds wrap content in <![CDATA[...]]>
                        if (!inItem) {
                            eventType = parser.next()
                            continue
                        }
                        val text = parser.text?.trim() ?: ""
                        when (currentTag) {
                            "title"            -> if (title.isEmpty())       title       = text
                            "description",
                            "summary",
                            "content:encoded"  -> if (description.isEmpty()) description = text
                            "author",
                            "dc:creator"       -> if (author.isEmpty())      author      = text
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        val tag = parser.name?.lowercase() ?: ""

                        if ((tag == "item" || tag == "entry") && inItem) {
                            inItem     = false
                            currentTag = ""

                            if (title.isNotEmpty() && link.isNotEmpty()) {
                                val cleanExcerpt = cleanText(description).take(250)
                                val wordCount    = cleanExcerpt.split(" ").size

                                articles.add(
                                    Article(
                                        feedId             = feedId,
                                        feedTitle          = feedTitle,
                                        feedFaviconUrl     = feedFaviconUrl,
                                        title              = cleanText(title),
                                        url                = link.trim(),
                                        thumbnailUrl       = thumbnail,
                                        excerpt            = cleanExcerpt,
                                        fullContent        = null,
                                        author             = author.takeIf { it.isNotEmpty() },
                                        publishedAt        = parseDate(pubDate),
                                        readingTimeMinutes = ReadingTimeCalculator.calculate(wordCount)
                                    )
                                )
                            } else {
                                android.util.Log.d("FlowDebug",
                                    "Skipped article — title:'$title' link:'$link'")
                            }
                        } else {
                            currentTag = ""
                        }
                    }
                }
                eventType = parser.next()
            }

            android.util.Log.d("FlowDebug", "Parsed ${articles.size} articles from XML")

        } catch (e: Exception) {
            android.util.Log.e("FlowDebug", "parseXml error: ${e.message}", e)
        }

        return articles
    }

    private fun cleanText(raw: String): String {
        return raw
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;",  "&")
            .replace("&lt;",   "<")
            .replace("&gt;",   ">")
            .replace("&quot;", "\"")
            .replace("&#39;",  "'")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun parseDate(dateStr: String): Long {
        if (dateStr.isEmpty()) return System.currentTimeMillis()
        dateFormats.forEach { format ->
            try {
                return format.parse(dateStr)?.time ?: System.currentTimeMillis()
            } catch (_: Exception) { }
        }
        return System.currentTimeMillis()
    }
}
