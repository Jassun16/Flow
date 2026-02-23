package com.jassun16.flow.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object ContentCleaner {

    // ── Patterns that indicate social/UI junk injected by Readability ────
    private val JUNK_EXACT = setOf(
        "follow", "followed", "unfollow",
        "subscribe", "subscribed", "unsubscribe",
        "sign up", "sign in", "log in", "login", "register",
        "advertisement", "sponsored",
        "share", "tweet", "like", "retweet",
        "comment", "comments", "leave a comment",
        "read more", "see more", "load more", "view more",
        "continue reading", "full article",
        "related", "trending now", "you may also like",
        "newsletter", "get our newsletter",
        "source", "via", "tags",
        "watch video", "play video", "play",
        "loading…", "loading...", "please wait"
    )

    // ── Clean HTML returned by Readability ───────────────────────────────
    fun clean(html: String): String {
        val doc = Jsoup.parse(html)

        // 1. Remove elements by common junk class/id names
        val junkSelectors = listOf(
            "[class*=social]",   "[id*=social]",
            "[class*=follow]",   "[id*=follow]",
            "[class*=share]",    "[id*=share]",
            "[class*=newsletter]","[id*=newsletter]",
            "[class*=subscribe]","[id*=subscribe]",
            "[class*=related]",  "[id*=related]",
            "[class*=promo]",    "[id*=promo]",
            "[class*=ad-]",      "[id*=ad-]",
            "[class*=widget]",
            ".sharedaddy", ".wp-block-buttons",
            ".post-footer", ".article-footer",
            ".author-bio",
            "button", "form",
            "input[type=text]", "input[type=email]", "input[type=submit]",
            "select", "textarea"
        )
        junkSelectors.forEach { selector ->
            try { doc.select(selector).remove() }
            catch (_: Exception) { }
        }

        // 2. Remove short orphan paragraphs matching junk text
        // (This catches "Follow", "Followed", "$299 $399 Save $100" etc.)
        doc.select("p, div > span, li").forEach { el ->
            // Only target elements with no child elements (pure text nodes)
            if (el.children().isEmpty()) {
                val text = el.text().trim()
                if (text.length < 80 && isJunkText(text)) {
                    el.remove()
                }
            }
        }

        // 3. Remove empty paragraphs left behind after removal
        doc.select("p:empty, div:empty").remove()

        // 4. Replace video iframes with thumbnails
        replaceVideos(doc)

        return doc.body().html()
    }

    // ── Junk text detector ───────────────────────────────────────────────
    private fun isJunkText(text: String): Boolean {
        if (text.isBlank()) return true
        val lower = text.lowercase().trim()

        // Exact match
        if (JUNK_EXACT.contains(lower)) return true

        // Starts with a junk word (e.g. "Follow us", "Subscribe now")
        if (JUNK_EXACT.any { lower.startsWith("$it ") }) return true

        // Ends with a junk word (e.g. "click to follow")
        if (JUNK_EXACT.any { lower.endsWith(" $it") }) return true

        // Price-like patterns: "$299", "€49.99", "₹2,999", "Save $100"
        if (lower.matches(Regex(".*[\\$€£₹¥]\\d[\\d,.]*.*"))) return true

        // Pure number or percentage strings
        if (lower.matches(Regex("[\\d,.%+\\-\\s]+"))) return true

        return false
    }

    // ── Replace <iframe> video embeds with thumbnails ────────────────────
    private fun replaceVideos(doc: org.jsoup.nodes.Document) {

        doc.select("iframe").forEach { iframe ->
            val src = iframe.attr("src")
                .ifEmpty { iframe.attr("data-src") }
                .ifEmpty { iframe.attr("data-lazy-src") }

            val replacement: Element? = when {
                // YouTube
                src.contains("youtube.com") || src.contains("youtu.be") -> {
                    val videoId = extractYouTubeId(src)
                    if (videoId != null) buildYouTubeThumbnail(videoId)
                    else buildGenericVideoLink(src, "Watch on YouTube")
                }
                // Vimeo
                src.contains("vimeo.com") -> {
                    buildGenericVideoLink(src, "▶ Watch on Vimeo")
                }
                // Other iframes with a src — show a link
                src.isNotEmpty() -> {
                    buildGenericVideoLink(src, "▶ Watch video")
                }
                // Empty src — just remove
                else -> null
            }

            if (replacement != null) {
                iframe.replaceWith(replacement)
            } else {
                iframe.remove()
            }
        }

        // Replace <video> tags
        doc.select("video").forEach { video ->
            val src = video.select("source").attr("src")
                .ifEmpty { video.attr("src") }
            if (src.isNotEmpty()) {
                video.replaceWith(buildGenericVideoLink(src, "▶ Watch video"))
            } else {
                video.remove()
            }
        }
    }

    private fun extractYouTubeId(url: String): String? {
        val patterns = listOf(
            Regex("""youtube\.com/embed/([a-zA-Z0-9_\-]{11})"""),
            Regex("""youtube\.com/watch\?v=([a-zA-Z0-9_\-]{11})"""),
            Regex("""youtu\.be/([a-zA-Z0-9_\-]{11})"""),
            Regex("""youtube\.com/shorts/([a-zA-Z0-9_\-]{11})""")
        )
        return patterns.firstNotNullOfOrNull { it.find(url)?.groupValues?.getOrNull(1) }
    }

    private fun buildYouTubeThumbnail(videoId: String): Element {
        val thumb   = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
        val ytUrl   = "https://www.youtube.com/watch?v=$videoId"
        val html    = """
            <a href="$ytUrl">
              <div style="position:relative;margin:20px 0;">
                <img src="$thumb" alt="YouTube video"
                     style="width:100%;border-radius:10px;display:block;"/>
                <div style="
                    position:absolute;top:50%;left:50%;
                    transform:translate(-50%,-50%);
                    background:rgba(0,0,0,0.72);
                    border-radius:50%;
                    width:60px;height:60px;
                    display:flex;align-items:center;justify-content:center;">
                  <div style="
                      width:0;height:0;
                      border-top:11px solid transparent;
                      border-bottom:11px solid transparent;
                      border-left:20px solid white;
                      margin-left:4px;">
                  </div>
                </div>
              </div>
            </a>
        """.trimIndent()
        return Jsoup.parse(html).body().child(0)
    }

    private fun buildGenericVideoLink(src: String, label: String): Element {
        val html = """<p><a href="$src">$label</a></p>"""
        return Jsoup.parse(html).body().child(0)
    }
}
