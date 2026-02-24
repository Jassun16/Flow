package com.jassun16.flow.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object ContentCleaner {

    private val JUNK_EXACT = setOf(
        "follow topics and authors",
        "follow topics & authors",
        "topics and authors",
        "follow on",
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

        // ── Step 0: Whitelist — extract only main article body ────────────
        // Try known main content containers in order of specificity.
        // If found, replace doc body with just that content.
        // This eliminates author bios, sidebars, related articles
        // from ANY website in one shot — no site-specific selectors needed.
        val mainContent = doc.select(
            // ── Semantic / standard ────────────────────────────────
            "article, " +
                    "#article-body, " +
                    ".article-body, " +
                    "[itemprop=articleBody], " +
                    ".post-content, " +
                    ".entry-content, " +
                    ".story-body, " +
                    ".content-body, " +

                    // ── The Verge (Chorus) — only the body, not the lede ──
                    "#zephr-anchor, " +

                    "main"
        ).firstOrNull()


        // Only use the extracted content if it's substantial
        // (avoids replacing with an empty or tiny element)
        if (mainContent != null && mainContent.text().length > 200) {
            doc.body().empty()
            doc.body().appendChild(mainContent)
        }
        // If nothing matched — fall through to existing blacklist approach below

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
            // ── The Verge — "Follow topics and authors" footer block ──────────
            ".tly2fw0",
            ".duet--layout--rail",   // right sidebar with more articles



            // ── The Verge (Chorus/Vox Media platform) ─────────────────────────
            ".duet--ledes--standard-lede-bottom",  // author photo + bio card
            ".duet--article--article-byline",      // "by Jay Peters" line
            "[class*=duet--article--lede]",        // entire lede header block
            "[class*=duet--layout--entry-sidebar]",// sidebar content

            // ── TechCrunch (also Chorus platform) ─────────────────────────────
            "[class*=duet--article--article-body-component-container]",
            "[class*=tc-events]",
            "[class*=event-card]",
            "[class*=promo-card]",

            // ── The Verge (Chorus/Vox Media) ──────────────────────────────────
            ".duet--ledes--standard-lede-bottom",
            ".duet--article--article-byline",
            "[class*=duet--article--lede]",
            "[class*=duet--layout--entry-sidebar]",

// ── TechCrunch ─────────────────────────────────────────────────────
            ".wp-block-techcrunch-inline-cta",  // event promo blocks mid-article
            ".inline-cta__wrapper",             // fallback if above misses

            // ── The Verge — Follow author/topic buttons ────────────────────────
            "[id^=follow-author]",       // follow author button containers
            "[id^=follow-topic]",        // follow topic button containers
            "[class*=gnx4pm]",           // Verge's follow pill component
            ".duet--ledes--standard-lede-bottom",  // already there — keeps author card gone
            "[class*=duet--article--related]",     // related articles row
            "[class*=duet--article--tags]",        // tags/topics row at bottom
            "[class*=duet--article--comments]",    // comments section
            "[class*=duet--universal--box]",       // generic promo boxes



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
                if (text.length < 120 && isJunkText(text)) el.remove()
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
