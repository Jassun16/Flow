package com.jassun16.flow.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jassun16.flow.ui.components.ArticleCard
import com.jassun16.flow.ui.components.DrawerContent
import com.jassun16.flow.viewmodel.FeedUiItem
import com.jassun16.flow.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onArticleClick: (Long) -> Unit,    // navigates to ReaderScreen
    onFeedsClick: () -> Unit,          // navigates to FeedsScreen
    onBookmarksClick: () -> Unit       // navigates to BookmarksScreen
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedFeedId by remember { mutableStateOf<Long?>(null) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = rememberLazyListState()

    val activity = LocalContext.current as? ComponentActivity
    DisposableEffect(Unit) {
        activity?.window?.let { w ->
            WindowInsetsControllerCompat(w, w.decorView).apply {
                show(WindowInsetsCompat.Type.statusBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        }
        onDispose { }
    }


    // Filter articles by selected feed or show all
    val displayedArticles = remember(uiState.articles, selectedFeedId) {
        if (selectedFeedId == null) uiState.articles
        else uiState.articles.filter { it.feedId == selectedFeedId }
    }

    // Snackbar host
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(uiState.shouldScrollToTop) {
        if (uiState.shouldScrollToTop) {
            listState.animateScrollToItem(0)
            viewModel.clearScrollToTop()
        }
    }

    ModalNavigationDrawer(
        drawerState   = drawerState,
        drawerContent = {
            DrawerContent(
                feeds           = uiState.feeds,
                selectedFeedId  = selectedFeedId,
                onAllArticlesClick = {
                    selectedFeedId = null
                    scope.launch { drawerState.close() }
                },
                onFeedClick     = { feed ->
                    selectedFeedId = feed.id
                    scope.launch { drawerState.close() }
                },
                onBookmarksClick = {
                    scope.launch { drawerState.close() }
                    onBookmarksClick()
                },
                onFeedsSettingsClick = {
                    scope.launch { drawerState.close() }
                    onFeedsClick()
                },
                onMarkAllReadClick = { viewModel.markAllAsRead() }
            )
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (selectedFeedId == null) "Flow"
                            else uiState.feeds.find { it.id == selectedFeedId }?.title ?: "Flow"
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        // Refresh button
                        IconButton(
                            onClick  = { viewModel.refresh() },
                            enabled  = !uiState.isRefreshing
                        ) {
                            if (uiState.isRefreshing) {
                                CircularProgressIndicator(
                                    modifier  = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
        ) { paddingValues ->

            if (displayedArticles.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text      = "No articles yet",
                            style     = MaterialTheme.typography.headlineSmall,
                            color     = MaterialTheme.colorScheme.onSurfaceVariant
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
            } else {
                // Article list — LazyColumn only renders visible items
                LazyColumn(
                    state             = listState,
                    modifier          = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding    = PaddingValues(vertical = 4.dp)
                ) {
                    items(
                        items = displayedArticles,
                        key   = { it.id }   // stable keys = smoother animations
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
