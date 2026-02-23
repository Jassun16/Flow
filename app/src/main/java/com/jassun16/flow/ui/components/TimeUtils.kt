package com.jassun16.flow.ui.components

object TimeUtils {
    fun timeAgo(timestampMs: Long): String {
        val diff    = System.currentTimeMillis() - timestampMs
        val minutes = diff / 60_000
        val hours   = diff / 3_600_000
        val days    = diff / 86_400_000
        return when {
            minutes < 1  -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours   < 24 -> "${hours}h ago"
            days    < 7  -> "${days}d ago"
            else         -> "${days / 7}w ago"
        }
    }
}
