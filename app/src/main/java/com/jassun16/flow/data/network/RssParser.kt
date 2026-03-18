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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.Buffer

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

    // Reads only up to </head> — og:image is always in <head>, avoids full page download
    private fun fetchOgImage(articleUrl: String): String? {
        return try {
            val request = Request.Builder()
                .url(articleUrl)
                .header("User-Agent", "Mozilla/5.0 (Android) Flow RSS Reader")
                .build()
            val response = client.newCall(request).execute()
            val source   = response.body?.source() ?: return null
            val sb       = StringBuilder()
            val buf      = Buffer()
            while (!source.exhausted()) {
                source.read(buf, 4096)
                sb.append(buf.readUtf8())
                if (sb.contains("</head>", ignoreCase = true)) break
            }
            response.body?.close()
            val head    = sb.toString()
            val regex1  = Regex("""<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val regex2  = Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']""", RegexOption.IGNORE_CASE)
            (regex1.find(head) ?: regex2.find(head))?.groupValues?.get(1)
                ?.takeIf { it.startsWith("http") }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetches OG images for a list of article URLs in parallel,
     * capped at [concurrency] simultaneous requests.
     * Returns a map of articleUrl → ogImageUrl for successful hits only.
     */
    suspend fun fetchOgImagesBatched(
        urls: List<String>,
        concurrency: Int = 6
    ): Map<String, String> {
        if (urls.isEmpty()) return emptyMap()
        val semaphore = Semaphore(concurrency)
        return coroutineScope {
            urls.map { url ->
                async {
                    semaphore.withPermit {
                        val ogUrl = fetchOgImage(url)
                        if (ogUrl != null) url to ogUrl else null
                    }
                }
            }.awaitAll()
                .filterNotNull()
                .toMap()
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

                                val cleanDescription   = cleanText(description)
                                val cleanExcerpt       = cleanDescription.take(250)
                                val textForReadingTime = if (contentEncoded.isNotEmpty()) contentEncoded else description
                                val wordCount          = cleanText(textForReadingTime)
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
                                            ?: extractImageFromHtml(description),
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

            // 1. Already a feed — XML/RSS/Atom response
            if (contentType.contains("xml") || contentType.contains("rss") ||
                contentType.contains("atom") || trimmed.startsWith("<?xml") ||
                trimmed.startsWith("<rss") || trimmed.startsWith("<feed")
            ) {
                Log.d("RssParser", "discoverFeedUrl: already a feed → $url")
                return url
            }

            // 2. HTML autodiscovery — <link rel="alternate" type="application/rss+xml">
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

            if (discoveredHref != null) {
                val resolved = resolveUrl(url, discoveredHref)
                Log.d("RssParser", "discoverFeedUrl: autodiscovered → $resolved")
                return resolved
            }

            // 3. Fallback — probe common WordPress / standard feed paths
            val proto  = url.substringBefore("://")
            val domain = url.substringAfter("://").substringBefore("/")
            val base   = "$proto://$domain"

            val commonPaths = listOf(
                "/feed/", "/feed", "/rss/", "/rss", "/atom.xml",
                "/feed.xml", "/rss.xml", "/index.xml", "/blog/feed/"
            )
            for (path in commonPaths) {
                val candidate = "$base$path"
                try {
                    val probeResponse = client.newCall(
                        Request.Builder()
                            .url(candidate)
                            .header("User-Agent", "Mozilla/5.0 (Android) Flow RSS Reader")
                            .build()
                    ).execute()
                    val probeType = probeResponse.header("Content-Type") ?: ""
                    val probeBody = probeResponse.body?.string()?.trimStart() ?: continue
                    if (probeResponse.isSuccessful &&
                        (probeType.contains("xml") || probeType.contains("rss") ||
                                probeType.contains("atom") || probeBody.startsWith("<?xml") ||
                                probeBody.startsWith("<rss") || probeBody.startsWith("<feed"))
                    ) {
                        Log.d("RssParser", "discoverFeedUrl: probed → $candidate")
                        return candidate
                    }
                } catch (_: Exception) { }
            }

            Log.e("RssParser", "discoverFeedUrl: no feed found at $url")
            null

        } catch (e: Exception) {
            Log.e("RssParser", "discoverFeedUrl error for $url: ${e.message}")
            null
        }
    }

    // Resolves relative hrefs against the base URL
    private fun resolveUrl(base: String, href: String): String = when {
        href.startsWith("http") -> href
        href.startsWith("//")   -> "https:$href"
        href.startsWith("/")    -> {
            val proto  = base.substringBefore("://")
            val domain = base.substringAfter("://").substringBefore("/")
            "$proto://$domain$href"
        }
        else -> "${base.trimEnd('/')}/$href"
    }

    /** Fetches a feed URL and extracts the channel-level <title> — not article titles */
    fun extractFeedTitle(url: String): String? {
        return try {
            val xml = downloadFeed(url) ?: return null
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))

            var currentTag = ""
            var inItem     = false
            var eventType  = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name?.lowercase() ?: ""
                        currentTag = tag
                        if (tag == "item" || tag == "entry") inItem = true
                    }
                    XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                        // Only capture title BEFORE the first item/entry = channel-level title
                        if (!inItem && currentTag == "title") {
                            val text = parser.text?.trim()
                            if (!text.isNullOrEmpty()) return text
                        }
                    }
                    XmlPullParser.END_TAG -> currentTag = ""
                }
                eventType = parser.next()
            }
            null
        } catch (e: Exception) {
            Log.e("RssParser", "extractFeedTitle error for $url: ${e.message}")
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
