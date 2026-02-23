package com.jassun16.flow.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object ContentCleaner {

    private val JUNK_EXACT = setOf(
        "follow", "followed", "unfollow",
        "subscribe", "subscribed", "unsubscribe",
        "sign up", "sign in", "log in", "login", "register",
        "advertisement", "sponsored",
        "share", "tweet", "like", "retweet",
        "read more", "see more", "load more", "view more",
        "continue reading", "full article",
        "trending now", "you may also like",
        "newsletter", "get our newsletter",
        "loading…", "loading...", "please wait"
    )

    fun clean(html: String): String {
        val doc = Jsoup.parse(html)

        // Step 1: Remove junk elements by class/id selectors
        val junkSelectors = listOf(
            // ── Android Police ─────────────────────────────────────────────
            ".w-article-header-comp",
            ".with-excerpt",
            ".w-author",
            ".w-article-header-author-img",
            ".display-card",
            ".w-follow-btn",
            ".follow-container",
            ".w-header-user-box",
            "[id=login-button-article-sidebar]",
            ".w-display-card-rate",
            ".display-item-price",
            ".w-rating",
            ".tag-interaction-popup-menu",
            ".article-header-data",
            ".w-tag-interaction-popup-menu",

            // ── Generic ────────────────────────────────────────────────────
            "[class*=social]",    "[id*=social]",
            "[class*=follow]",    "[id*=follow]",
            "[class*=share]",     "[id*=share]",
            "[class*=newsletter]","[id*=newsletter]",
            "[class*=subscribe]", "[id*=subscribe]",
            "[class*=related]",   "[id*=related]",
            "[class*=promo]",     "[id*=promo]",
            "[class*=ad-]",       "[id*=ad-]",
            "[class*=widget]",
            "[class*=author]",    "[id*=author]",
            "[class*=byline]",    "[id*=byline]",
            ".sharedaddy", ".wp-block-buttons",
            ".post-footer", ".article-footer",
            "button", "form",
            "input[type=text]", "input[type=email]", "input[type=submit]",
            "select", "textarea"
        )
        junkSelectors.forEach { selector ->
            try { doc.select(selector).remove() } catch (_: Exception) { }
        }

        // Step 2: Remove short orphan text nodes matching junk patterns
        doc.select("p, div > span, li").forEach { el ->
            if (el.children().isEmpty()) {
                val text = el.text().trim()
                if (text.length < 80 && isJunkText(text)) el.remove()
            }
        }

        // Step 3: Remove leftover empty tags
        doc.select("p:empty, div:empty").remove()

        // Step 4: Replace video iframes with thumbnails
        replaceVideos(doc)

        return doc.body().html()
    }

    private fun isJunkText(text: String): Boolean {
        if (text.isBlank()) return true
        val lower = text.lowercase().trim()
        if (JUNK_EXACT.contains(lower)) return true
        if (JUNK_EXACT.any { lower.startsWith("$it ") }) return true
        if (JUNK_EXACT.any { lower.endsWith(" $it") }) return true
        // Price patterns: $299, €49, ₹2,999, Save $100
        if (lower.matches(Regex(".*[\\$€£₹¥]\\d[\\d,.]*.*"))) return true
        // Pure numbers/percentages
        if (lower.matches(Regex("[\\d,.%+\\-\\s]+"))) return true
        return false
    }

    private fun replaceVideos(doc: org.jsoup.nodes.Document) {
        doc.select("iframe").forEach { iframe ->
            val src = iframe.attr("src")
                .ifEmpty { iframe.attr("data-src") }
                .ifEmpty { iframe.attr("data-lazy-src") }

            val replacement: Element? = when {
                src.contains("youtube.com") || src.contains("youtu.be") -> {
                    val videoId = extractYouTubeId(src)
                    if (videoId != null) buildYouTubeThumbnail(videoId)
                    else buildGenericVideoLink(src, "▶ Watch on YouTube")
                }
                src.contains("vimeo.com") -> buildGenericVideoLink(src, "▶ Watch on Vimeo")
                src.isNotEmpty()          -> buildGenericVideoLink(src, "▶ Watch video")
                else                      -> null
            }
            if (replacement != null) iframe.replaceWith(replacement) else iframe.remove()
        }

        doc.select("video").forEach { video ->
            val src = video.select("source").attr("src").ifEmpty { video.attr("src") }
            if (src.isNotEmpty()) video.replaceWith(buildGenericVideoLink(src, "▶ Watch video"))
            else video.remove()
        }
    }

    private fun extractYouTubeId(url: String): String? {
        return listOf(
            Regex("""youtube\.com/embed/([a-zA-Z0-9_\-]{11})"""),
            Regex("""youtube\.com/watch\?v=([a-zA-Z0-9_\-]{11})"""),
            Regex("""youtu\.be/([a-zA-Z0-9_\-]{11})"""),
            Regex("""youtube\.com/shorts/([a-zA-Z0-9_\-]{11})""")
        ).firstNotNullOfOrNull { it.find(url)?.groupValues?.getOrNull(1) }
    }

    private fun buildYouTubeThumbnail(videoId: String): Element {
        val html = """
            <a href="https://www.youtube.com/watch?v=$videoId">
              <div style="position:relative;margin:20px 0;">
                <img src="https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                     alt="YouTube video"
                     style="width:100%;border-radius:10px;display:block;"/>
                <div style="position:absolute;top:50%;left:50%;
                            transform:translate(-50%,-50%);
                            background:rgba(0,0,0,0.72);border-radius:50%;
                            width:60px;height:60px;
                            display:flex;align-items:center;justify-content:center;">
                  <div style="width:0;height:0;
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

    private fun buildGenericVideoLink(src: String, label: String): Element =
        Jsoup.parse("""<p><a href="$src">$label</a></p>""").body().child(0)
}
