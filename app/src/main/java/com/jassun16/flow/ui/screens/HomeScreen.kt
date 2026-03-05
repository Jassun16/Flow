package com.jassun16.flow.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.jassun16.flow.ui.components.ArticleCard
import com.jassun16.flow.ui.components.DrawerContent
import com.jassun16.flow.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onArticleClick: (Long) -> Unit,
    onFeedsClick: () -> Unit,
    onBookmarksClick: () -> Unit
) {
    val activity = LocalContext.current as ComponentActivity
    val viewModel: HomeViewModel = hiltViewModel(activity)
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        activity.window?.let { w ->
            WindowInsetsControllerCompat(w, w.decorView).apply {
                show(WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        }
        onDispose { }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbar()
        }
    }

    ModalNavigationDrawer(
        drawerState   = drawerState,
        drawerContent = {
            DrawerContent(
                feeds               = uiState.feeds,
                selectedFeedId      = uiState.selectedFeedId,
                onAllArticlesClick  = {
                    viewModel.selectFeed(null)
                    scope.launch { drawerState.close() }
                },
                onFeedClick         = { feed ->
                    viewModel.selectFeed(feed.id)
                    scope.launch { drawerState.close() }
                },
                onBookmarksClick    = {
                    scope.launch { drawerState.close() }
                    onBookmarksClick()
                },
                onFeedsSettingsClick = {
                    scope.launch { drawerState.close() }
                    onFeedsClick()
                },
                onMarkAllReadClick  = { viewModel.markAllAsRead() }
            )
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (uiState.selectedFeedId == null) "Flow"
                            else uiState.feeds.find { it.id == uiState.selectedFeedId }?.title ?: "Flow"
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.refresh() },
                            enabled = !uiState.isRefreshing
                        ) {
                            if (uiState.isRefreshing) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
            // ← no nestedScroll modifier, no scrollBehavior
        ) { paddingValues ->
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh    = { viewModel.refresh() },
                modifier     = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when {
                    uiState.isInitialLoad -> {
                        Box(modifier = Modifier.fillMaxSize())
                    }

                    uiState.filteredArticles.isEmpty() -> {
                        Box(
                            modifier         = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text  = "No articles yet",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text      = "Pull down to refresh\nor add feeds from the menu",
                                    style     = MaterialTheme.typography.bodyMedium,
                                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(onClick = { viewModel.refresh() }) {
                                    Text("Refresh Now")
                                }
                            }
                        }
                    }

                    else -> {
                            items(
                                items       = uiState.filteredArticles,
                                key         = { it.id },
                                contentType = { "article_card" }
                            ) { article ->
                                ArticleCard(
                                    article = article,
                                    onClick = { onArticleClick(article.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
