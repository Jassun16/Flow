package com.jassun16.flow.data.network

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import javax.inject.Inject
import javax.inject.Singleton

data class ReadableArticle(
    val title:   String,
    val content: String,  // cleaned HTML — safe to render in reader
    val author:  String?,
    val success: Boolean  // false = readability failed, show WebView instead
)

@Singleton
class ReadabilityFetcher @Inject constructor() {

    // Tags that are NEVER part of the article body — always removed
    private val NOISE_TAGS = setOf(
        "script", "style", "nav", "header", "footer",
        "aside", "advertisement", "figure.related",
        "div.related", "div.comments", "div.sidebar",
        "div.newsletter", "div.social-share"
    )

    fun fetch(url: String): ReadableArticle {
        return try {
            // Jsoup downloads the page and parses it into a DOM tree
            // userAgent mimics a real browser so sites don't block us
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36")
                .timeout(15_000)
                .get()

            val title  = extractTitle(doc)
            val author = extractAuthor(doc)
            val body   = extractMainContent(doc)

            if (body.isNullOrBlank() || body.length < 200) {
                // Content too short — readability failed
                ReadableArticle(title, "", author, success = false)
            } else {
                ReadableArticle(
                    title   = title,
                    content = cleanContent(body),
                    author  = author,
                    success = true
                )
            }

        } catch (e: Exception) {
            e.printStackTrace()
            ReadableArticle("", "", null, success = false)
        }
    }

    private fun extractTitle(doc: Document): String {
        // Try Open Graph title first (most accurate), fall back to <title> tag
        return doc.select("meta[property=og:title]").attr("content")
            .ifEmpty { doc.select("meta[name=twitter:title]").attr("content") }
            .ifEmpty { doc.title() }
            .trim()
    }

    private fun extractAuthor(doc: Document): String? {
        return doc.select("meta[name=author]").attr("content")
            .ifEmpty { doc.select("meta[property=article:author]").attr("content") }
            .ifEmpty { doc.select(".author, .byline, [rel=author]").firstOrNull()?.text() }
            .takeIf { !it.isNullOrBlank() }
    }

    // The core algorithm — finds the element most likely to be the article body
    private fun extractMainContent(doc: Document): String? {
        // Remove all noise elements first
        NOISE_TAGS.forEach { selector ->
            try { doc.select(selector).remove() } catch (_: Exception) { }
        }

        // Strategy 1: Look for semantic article tags (most modern sites use these)
        val semanticSelectors = listOf(
            "article",
            "[itemprop=articleBody]",
            ".article-body", ".article-content",
            ".post-body",    ".post-content",
            ".entry-content","#article-body",
            ".story-body",   ".story-content",
            "main"
        )
        semanticSelectors.forEach { selector ->
            val element = doc.select(selector).firstOrNull()
            if (element != null && element.text().length > 200) {
                return element.html()
            }
        }

        // Strategy 2: Score every <div> by how much paragraph text it contains
        // The div with the highest score is the article body
        val candidate = doc.select("div, section")
            .filter { el ->
                val paragraphs = el.select("p")
                val textLength = paragraphs.sumOf { it.text().length }
                textLength > 300
            }
            .maxByOrNull { el ->
                el.select("p").sumOf { it.text().length }
            }

        return candidate?.html()
    }

    // Converts raw HTML into clean, reader-friendly HTML
    private fun cleanContent(rawHtml: String): String {
        val doc = Jsoup.parseBodyFragment(rawHtml)

        // Remove remaining noise tags inside the content
        doc.select("script, style, iframe, form, button, input").remove()

        // Keep images but make them responsive
        doc.select("img").forEach { img ->
            // Replace srcset with src for simplicity
            if (img.attr("src").isEmpty() && img.hasAttr("data-src")) {
                img.attr("src", img.attr("data-src"))
            }
            // Remove fixed dimensions — we'll size via CSS in the reader
            img.removeAttr("width")
            img.removeAttr("height")
            img.removeAttr("style")
        }

        // Remove empty paragraphs and divs
        doc.select("p, div").filter { it.text().isBlank() && it.select("img").isEmpty() }
            .forEach { it.remove() }

        return doc.body().html()
    }
}
