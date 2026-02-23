package com.jassun16.flow.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.jassun16.flow.viewmodel.FeedUiItem
import com.jassun16.flow.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedsScreen(onBack: () -> Unit) {
    val viewModel: HomeViewModel = hiltViewModel()
    val uiState  by viewModel.uiState.collectAsState()
    var showAddDialog  by remember { mutableStateOf(false) }
    var newFeedUrl     by remember { mutableStateOf("") }
    var feedToDelete   by remember { mutableStateOf<FeedUiItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Feeds") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Feed")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier       = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(uiState.feeds, key = { it.id }) { feed ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = feed.faviconUrl,
                            contentDescription = feed.title,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text  = feed.title,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text  = "${feed.unreadCount} unread",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { feedToDelete = feed }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove feed",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Add Feed Dialog ────────────────────────────────────────────────────
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false; newFeedUrl = "" },
            title   = { Text("Add RSS Feed") },
            text    = {
                Column {
                    Text(
                        "Enter the RSS feed URL",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value           = newFeedUrl,
                        onValueChange   = { newFeedUrl = it },
                        label           = { Text("Feed URL") },
                        placeholder     = { Text("https://techcrunch.com/feed/") },
                        singleLine      = true,
                        modifier        = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFeedUrl.isNotBlank()) {
                            viewModel.addFeed(newFeedUrl.trim())
                            showAddDialog = false
                            newFeedUrl    = ""
                        }
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; newFeedUrl = "" }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Delete Confirmation Dialog ─────────────────────────────────────────
    feedToDelete?.let { feed ->
        AlertDialog(
            onDismissRequest = { feedToDelete = null },
            title   = { Text("Remove Feed") },
            text    = { Text("Remove \"${feed.title}\"? All its articles will be deleted too.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFeed(feed)
                        feedToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { feedToDelete = null }) { Text("Cancel") }
            }
        )
    }
}
