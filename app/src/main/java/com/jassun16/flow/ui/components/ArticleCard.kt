package com.jassun16.flow.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jassun16.flow.viewmodel.ArticleUiItem

@Composable
fun ArticleCard(
    article: ArticleUiItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Read articles appear dimmed — clear visual read/unread distinction
    val cardAlpha = if (article.isRead) 0.55f else 1f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .alpha(cardAlpha)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top
            ) {
                // ── Left: Text content ─────────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = if (article.thumbnailUrl != null) 10.dp else 0.dp)
                ) {
                    // Source row: favicon + feed name + timestamp
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        AsyncImage(
                            model             = article.feedFaviconUrl,
                            contentDescription = "Feed icon",
                            modifier          = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text  = article.feedTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                        Text(
                            text  = "  •  ${TimeUtils.timeAgo(article.publishedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Article title
                    Text(
                        text     = article.title,
                        style    = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    // Excerpt
                    if (article.excerpt.isNotBlank()) {
                        Text(
                            text     = article.excerpt,
                            style    = MaterialTheme.typography.bodyMedium,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Reading time chip
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text  = "⏱ ${article.readingTimeMinutes} min read",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // ── Right: Thumbnail ───────────────────────────────────
                article.thumbnailUrl?.let { imageUrl ->
                    AsyncImage(
                        model              = imageUrl,
                        contentDescription = "Article thumbnail",
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier
                            .size(width = 90.dp, height = 80.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }

            // Divider between cards
            HorizontalDivider(
                modifier  = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color     = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}
