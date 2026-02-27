package com.jassun16.flow.data.network

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object ArticleExtractor {

    // ── TIER 2: Generic wildcard selectors ──────────────────────────
    private val genericJunkSelectors = listOf(
        "[role='complementary']",
        "[role='navigation']",
        "[role='banner']",
        "[aria-label='Follow']",
        "[class*='follow']",
        "[class*='subscribe']",
        "[class*='newsletter']",
        "[class*='sidebar']",
        "[class*='rail']",
        "[class*='related']",
        "[class*='recommended']",
        "[class*='trending']",
        "[class*='signup']",
        "[class*='promo']",
        "[class*='sticky']",
        "[class*='paywall']",
        "[class*='ad-']",
        "[class*='-ad']",
        "[class*='advertisement']",
        "[id*='sidebar']",
        "[id*='related']",
        "[id*='newsletter']",
    )

    // ── TIER 3: Text-phrase junk detection ──────────────────────────
    private val junkPhrases = listOf(
        "follow topics",
        "follow authors",
        "sign up for",
        "subscribe to our",
        "get the newsletter",
        "more from this author",
        "you might also like",
        "most popular",
        "read more from",
        "don't miss",
    )

    fun cleanHtml(rawHtml: String, baseUrl: String = ""): String {
        val doc: Document = Jsoup.parse(rawHtml, baseUrl)

        // Tier 2 — remove by generic selectors
        genericJunkSelectors.forEach { selector ->
            try {
                doc.select(selector).remove()
            } catch (_: Exception) { }
        }

        // Tier 3 — remove by text content
        doc.select("div, aside, section, p, span").forEach { el ->
            val text = el.text().lowercase().trim()
            if (junkPhrases.any { text.startsWith(it) }) {
                el.remove()
            }
        }

        // Tier 2b — fix lazy-loaded images (data-src → src)
        doc.select("img[data-src], img[data-lazy-src], img[data-original]").forEach { img ->
            val lazySrc = img.attr("data-src").ifEmpty {
                img.attr("data-lazy-src").ifEmpty {
                    img.attr("data-original")
                }
            }
            if (lazySrc.startsWith("http")) {
                img.attr("src", lazySrc)
            }
        }

        // Remove imgs with no valid src
        doc.select("img").forEach { img ->
            val src = img.attr("src")
            if (src.isEmpty() || src.startsWith("data:image/gif") ||
                src.contains("placeholder") || src.contains("blank")) {
                img.remove()
            }
        }

        // Tier 2c — strip padding-bottom percentage from image containers
        doc.select("div[style*='padding-bottom']").forEach { el ->
            val style = el.attr("style")
            el.attr(
                "style",
                style.replace(Regex("padding-bottom\\s*:[^;]+;?"), "").trim()
            )
        }

        return doc.outerHtml()
    }
}
