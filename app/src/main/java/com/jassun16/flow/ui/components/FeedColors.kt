package com.jassun16.flow.ui.components

import androidx.compose.ui.graphics.Color

object FeedColors {
    private val palette = listOf(
        Color(0xFF4A90D9), // blue
        Color(0xFF66BB6A), // green
        Color(0xFFFF8A65), // orange
        Color(0xFFBA68C8), // purple
        Color(0xFF26C6DA), // cyan
        Color(0xFFFFCA28), // amber
        Color(0xFFF06292), // pink
        Color(0xFF4DB6AC)  // teal
    )

    fun forFeed(feedId: Long): Color = palette[(feedId % palette.size).toInt()]
}
