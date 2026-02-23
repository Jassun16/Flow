package com.jassun16.flow.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jassun16.flow.viewmodel.FeedUiItem

@Composable
fun DrawerContent(
    feeds: List<FeedUiItem>,
    selectedFeedId: Long?,           // null = "All Articles" selected
    onAllArticlesClick: () -> Unit,
    onFeedClick: (FeedUiItem) -> Unit,
    onBookmarksClick: () -> Unit,
    onFeedsSettingsClick: () -> Unit,
    onMarkAllReadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(modifier = modifier) {
        // App name header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 24.dp)
        ) {
            Text(
                text  = "Flow",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn {
            // ── All Articles ───────────────────────────────────────────
            item {
                NavigationDrawerItem(
                    // ✅ Correct
                    icon = {
                        Icon(Icons.Default.DynamicFeed,
                            contentDescription = null)
                    },

                    label = { Text("All Articles") },
                    selected = selectedFeedId == null,
                    onClick  = onAllArticlesClick,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            // ── Bookmarks ──────────────────────────────────────────────
            item {
                NavigationDrawerItem(
                    icon     = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                    label    = { Text("Bookmarks") },
                    selected = false,
                    onClick  = onBookmarksClick,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            // ── Feeds Header ───────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text  = "MY FEEDS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Mark all read button
                    TextButton(onClick = onMarkAllReadClick) {
                        Text("Mark all read",
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // ── Individual Feeds ───────────────────────────────────────
            items(feeds, key = { it.id }) { feed ->
                NavigationDrawerItem(
                    icon = {
                        AsyncImage(
                            model = feed.faviconUrl,
                            contentDescription = feed.title,
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                        )
                    },
                    label = { Text(feed.title, maxLines = 1) },
                    badge = {
                        // Only show badge if there are unread articles
                        if (feed.unreadCount > 0) {
                            Badge {
                                Text(
                                    text  = if (feed.unreadCount > 99) "99+" else feed.unreadCount.toString(),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    },
                    selected = selectedFeedId == feed.id,
                    onClick  = { onFeedClick(feed) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            // ── Manage Feeds ───────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                NavigationDrawerItem(
                    icon     = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label    = { Text("Manage Feeds") },
                    selected = false,
                    onClick  = onFeedsSettingsClick,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}
