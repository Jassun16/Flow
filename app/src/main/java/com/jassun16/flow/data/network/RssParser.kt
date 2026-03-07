package com.jassun16.flow.data.network

import android.util.Log
import android.util.Xml
import com.jassun16.flow.data.db.Article
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
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
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z",   Locale.ENGLISH),
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ",        Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'",      Locale.ENGLISH)
    )

    suspend fun parseFeed(
        feedId: Long,
        feedTitle: String,
        feedFaviconUrl: String,
        rssUrl: String
    ): List<Article> {
        return try {
            val xml = downloadFeed(rssUrl) ?: return emptyList()
            parseXml(xml, feedId, feedTitle, feedFaviconUrl)
        } catch (e: Exception) {
            Log.e("RssParser", "parseFeed error for $feedTitle: ${e.message}", e)
            emptyList()
        }
    }

    private fun downloadFeed(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android) Flow RSS Reader")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) response.body?.string()
            else {
                Log.e("RssParser", "Download failed for $url: HTTP ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e("RssParser", "Download exception for $url: ${e.message}")
            null
        }
    }

    private fun fetchOgImage(articleUrl: String): String? {
        return try {
            val request  = Request.Builder()
                .url(articleUrl)
                .header("User-Agent", "Mozilla/5.0 (Android) Flow RSS Reader")
                .build()
            val html     = client.newCall(request).execute().body?.string() ?: return null
            val ogRegex  = Regex("""<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val ogRegex2 = Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']""", RegexOption.IGNORE_CASE)
            (ogRegex.find(html) ?: ogRegex2.find(html))?.groupValues?.get(1)
                ?.takeIf { it.startsWith("http") }
        } catch (e: Exception) {
            null
        }
    }


    private fun parseXml(
        xml: String,
        feedId: Long,
        feedTitle: String,
        feedFaviconUrl: String
    ): List<Article> {
        Log.d("RssParser", "Parsing feed: $feedTitle | xml length: ${xml.length}")
        val articles = mutableListOf<Article>()

        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))

            var currentTag     = ""
            var inItem         = false
            var title          = ""
            var link           = ""
            var description    = ""
            var contentEncoded = ""
            var pubDate        = ""
            var author         = ""
            var thumbnail: String? = null

            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {

                    XmlPullParser.START_TAG -> {
                        val tag = parser.name ?: ""
                        currentTag = tag.lowercase()

                        when (currentTag) {
                            "item", "entry" -> {
                                inItem         = true
                                title          = ""
                                link           = ""
                                description    = ""
                                contentEncoded = ""
                                pubDate        = ""
                                author         = ""
                                thumbnail      = null
                            }
                            "link" -> {
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
                        if (!inItem) { eventType = parser.next(); continue }
                        val text = parser.text?.trim() ?: ""
                        if (text.isEmpty()) { eventType = parser.next(); continue }

                        when (currentTag) {
                            "title"           -> if (title.isEmpty())          title          = text
                            "link"            -> if (link.isEmpty())           link           = text
                            "description",
                            "summary",
                            "content"         -> if (description.isEmpty())    description    = text
                            "content:encoded" -> if (contentEncoded.isEmpty()) contentEncoded = text
                            "pubdate",
                            "published",
                            "updated",
                            "dc:date"         -> if (pubDate.isEmpty())        pubDate        = text
                            "author",
                            "dc:creator",
                            "name"            -> if (author.isEmpty())         author         = text
                        }
                    }

                    XmlPullParser.CDSECT -> {
                        if (!inItem) { eventType = parser.next(); continue }
                        val text = parser.text?.trim() ?: ""
                        when (currentTag) {
                            "title"           -> if (title.isEmpty())          title          = text
                            "description",
                            "summary"         -> if (description.isEmpty())    description    = text
                            "content:encoded" -> if (contentEncoded.isEmpty()) contentEncoded = text
                            "author",
                            "dc:creator"      -> if (author.isEmpty())         author         = text
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        val tag = parser.name?.lowercase() ?: ""

                        if ((tag == "item" || tag == "entry") && inItem) {
                            inItem     = false
                            currentTag = ""

                            if (title.isNotEmpty() && link.isNotEmpty()) {

                                // ── Debug logs ────────────────────────────────────────
                                Log.d("RssParser", "=== ARTICLE ===")
                                Log.d("RssParser", "Title: $title")
                                Log.d("RssParser", "Thumbnail (tag): $thumbnail")
                                Log.d("RssParser", "ContentEncoded (300): ${contentEncoded.take(300)}")
                                Log.d("RssParser", "Description (300): ${description.take(300)}")
                                Log.d("RssParser", "Extracted from contentEncoded: ${extractImageFromHtml(contentEncoded)}")
                                Log.d("RssParser", "Extracted from description: ${extractImageFromHtml(description)}")
                                // ──────────────────────────────────────────────────────

                                val cleanDescription = cleanText(description)
                                val cleanExcerpt     = cleanDescription.take(250)
                                val wordCount        = cleanDescription
                                    .trim()
                                    .split(Regex("\\s+"))
                                    .filter { it.isNotEmpty() }
                                    .size

                                articles.add(
                                    Article(
                                        feedId             = feedId,
                                        feedTitle          = feedTitle,
                                        feedFaviconUrl     = feedFaviconUrl,
                                        title              = cleanText(title),
                                        url                = link.trim(),
                                        thumbnailUrl       = thumbnail
                                            ?: extractImageFromHtml(contentEncoded)
                                            ?: extractImageFromHtml(description)
                                            ?: fetchOgImage(link.trim()),
                                        excerpt            = cleanExcerpt,
                                        fullContent        = null,
                                        author             = author.takeIf { it.isNotEmpty() },
                                        publishedAt        = parseDate(pubDate),
                                        readingTimeMinutes = ReadingTimeCalculator.calculate(wordCount)
                                    )
                                )
                            }
                        } else {
                            currentTag = ""
                        }
                    }
                }
                eventType = parser.next()
            }

        } catch (e: Exception) {
            Log.e("RssParser", "Parse exception in $feedTitle: ${e.message}", e)
        }

        Log.d("RssParser", "Done parsing $feedTitle — ${articles.size} articles")
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
                return ZonedDateTime.parse(dateStr, format).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) { }
        }
        return System.currentTimeMillis()
    }
    /**
     * Given any URL (domain, website, or direct RSS link), returns the actual RSS/Atom feed URL.
     * 1. Prepares the URL, fetches it
     * 2. If response is already XML → return as-is
     * 3. If HTML → parse <link rel="alternate"> to find the feed href
     * 4. Resolves relative hrefs against the base URL
     */
    fun discoverFeedUrl(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android) Flow RSS Reader")
                .build()
            val response    = client.newCall(request).execute()
            val contentType = response.header("Content-Type") ?: ""
            val body        = response.body?.string() ?: return null
            val trimmed     = body.trimStart()

            // Already a feed — XML/RSS/Atom content type or body starts with XML tags
            if (contentType.contains("xml") || contentType.contains("rss") ||
                contentType.contains("atom") || trimmed.startsWith("<?xml") ||
                trimmed.startsWith("<rss") || trimmed.startsWith("<feed")
            ) {
                Log.d("RssParser", "discoverFeedUrl: already a feed → $url")
                return url
            }

            // HTML page — look for <link type="application/rss+xml" href="...">
            // Handles both attribute orderings
            val typeFirst = Regex(
                """<link[^>]+type=["'](application/rss\+xml|application/atom\+xml)["'][^>]+href=["']([^"']+)["']""",
                RegexOption.IGNORE_CASE
            )
            val hrefFirst = Regex(
                """<link[^>]+href=["']([^"']+)["'][^>]+type=["'](application/rss\+xml|application/atom\+xml)["']""",
                RegexOption.IGNORE_CASE
            )

            val discoveredHref = typeFirst.find(body)?.groupValues?.get(2)
                ?: hrefFirst.find(body)?.groupValues?.get(1)

            if (discoveredHref == null) {
                Log.e("RssParser", "discoverFeedUrl: no feed link found in HTML at $url")
                return null
            }

            // Resolve relative URLs against the base
            val resolved = when {
                discoveredHref.startsWith("http") -> discoveredHref
                discoveredHref.startsWith("//")   -> "https:$discoveredHref"
                discoveredHref.startsWith("/")    -> {
                    val proto  = url.substringBefore("://")
                    val domain = url.substringAfter("://").substringBefore("/")
                    "$proto://$domain$discoveredHref"
                }
                else -> "${url.trimEnd('/')}/$discoveredHref"
            }

            Log.d("RssParser", "discoverFeedUrl: discovered → $resolved")
            resolved

        } catch (e: Exception) {
            Log.e("RssParser", "discoverFeedUrl error for $url: ${e.message}")
            null
        }
    }
}

private fun extractImageFromHtml(html: String): String? {
    val imgRegex = Regex("""<img[^>]+src\s*=\s*["']([^"']{10,})["']""", RegexOption.IGNORE_CASE)
    return imgRegex.find(html)?.groupValues?.get(1)
        ?.takeIf { it.startsWith("http") }
        ?.replace("&#038;", "&")
        ?.replace("&amp;",  "&")
}
