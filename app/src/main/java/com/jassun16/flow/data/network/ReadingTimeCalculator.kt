package com.jassun16.flow.data.network

object ReadingTimeCalculator {

    private const val WORDS_PER_MINUTE = 200

    // wordCount comes from the RSS excerpt or full article text
    fun calculate(wordCount: Int): Int {
        val minutes = wordCount / WORDS_PER_MINUTE
        return maxOf(1, minutes)   // minimum 1 minute — never shows "0 min read"
    }

    // Overload for full article text strings
    fun calculateFromText(text: String): Int {
        val wordCount = text.trim().split(Regex("\\s+")).size
        return calculate(wordCount)
    }
}
