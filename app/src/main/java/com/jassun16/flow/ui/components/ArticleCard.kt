package com.jassun16.flow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jassun16.flow.viewmodel.ArticleUiItem

@Composable
fun ArticleCard(
    article: ArticleUiItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cardAlpha = if (article.isRead) 0.45f else 1.0f

    Column {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(120.dp)
                .alpha(cardAlpha)
                .clickable(onClick = onClick),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── Left column — fills available height so we can anchor read time to bottom ──
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // ── Metadata row: favicon · feed name · time ago ──
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(article.feedFaviconUrl)
                                .crossfade(false)
                                .size(28)
                                .build(),
                            contentDescription = "Feed icon",
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text     = article.feedTitle,
                            style    = MaterialTheme.typography.labelMedium,
                            color    = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                        Text(
                            text  = "  •  ${TimeUtils.timeAgo(article.publishedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // ── Title ──
                    Text(
                        text     = article.title,
                        style    = MaterialTheme.typography.titleMedium,
                        color    = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // ── Excerpt ──
                    if (article.excerpt.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text     = article.excerpt,
                            style    = MaterialTheme.typography.bodyMedium,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // ── Push read time to the bottom of the card ──
                    Spacer(modifier = Modifier.weight(1f))

                    // ── Read time row — anchored to bottom ──
                    if (article.readingTimeMinutes > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector     = Icons.Outlined.Schedule,
                                contentDescription = null,
                                modifier        = Modifier.size(11.dp),
                                tint            = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text  = "${article.readingTimeMinutes} min read",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // ── Thumbnail ──
                Box(
                    modifier = Modifier
                        .size(width = 90.dp, height = 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (!article.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(article.thumbnailUrl)
                                .crossfade(true)
                                .size(180, 160)
                                .build(),
                            contentDescription = "Article thumbnail",
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            modifier  = Modifier.padding(horizontal = 16.dp),
            thickness = 0.5.dp,
            color     = MaterialTheme.colorScheme.outlineVariant
        )
    }
}
