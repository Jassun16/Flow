package com.jassun16.flow.ui.components

import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jassun16.flow.viewmodel.ArticleUiItem
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

@Composable
fun ArticleCard(
    article: ArticleUiItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val cardBackground = if (article.isRead)
        MaterialTheme.colorScheme.surfaceVariant
    else
        MaterialTheme.colorScheme.surface

    val titleColor = if (article.isRead)
        MaterialTheme.colorScheme.onSurfaceVariant
    else
        MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)  // ← fixed height, all cards identical
            .clickable(onClick = onClick),
        color = cardBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: text content (always same space)
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
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
                    Spacer(modifier = Modifier.width(8.dp))
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

                Text(
                    text     = article.title,
                    style    = MaterialTheme.typography.titleMedium,
                    color    = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (article.excerpt.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text     = article.excerpt,
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right: thumbnail (fixed space, shows placeholder if missing)
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(article.thumbnailUrl)
                    .crossfade(false)
                    .size(90, 80)
                    .build(),
                contentDescription = "Article thumbnail",
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = android.R.color.transparent),  // ← transparent until loaded
                modifier = Modifier
                    .size(width = 90.dp, height = 80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)  // ← gray background fills empty space
            )
        }
    }

    HorizontalDivider(
                modifier  = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color     = MaterialTheme.colorScheme.outlineVariant
            )
        }