package com.jassun16.flow.data.network

import android.content.Context
import android.webkit.WebView
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

    /**
     * TIER 2 + 3: Jsoup-based cleaning.
     * Call this on raw HTML string before rendering.
     */
    fun cleanHtml(rawHtml: String, baseUrl: String = ""): String {
        val doc: Document = Jsoup.parse(rawHtml, baseUrl)

        // Tier 2 — remove by generic selectors
        genericJunkSelectors.forEach { selector ->
            try {
                doc.select(selector).remove()
            } catch (_: Exception) { /* invalid selector on older Jsoup, skip */ }
        }

        // Tier 3 — remove by text content
        doc.select("div, aside, section, p, span").forEach { el ->
            val text = el.text().lowercase().trim()
            if (junkPhrases.any { text.startsWith(it) }) {
                el.remove()
            }
        }

        return doc.outerHtml()
    }
}