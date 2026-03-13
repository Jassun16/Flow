package com.jassun16.flow.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jassun16.flow.ui.components.ArticleCard
import com.jassun16.flow.ui.components.DrawerContent
import com.jassun16.flow.util.HapticUtils
import com.jassun16.flow.viewmodel.HomeViewModel
import kotlinx.coroutines.flow.collectLatest
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
    val listState = rememberLazyListState()
    val pullRefreshState = rememberPullToRefreshState()

    // ── Hide/show nav bars ───────────────────────────────────────────────────
    DisposableEffect(Unit) {
        activity.window?.let { w ->
            androidx.core.view.WindowInsetsControllerCompat(w, w.decorView).apply {
                show(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        }
        onDispose { }
    }

    // ── Search state ─────────────────────────────────────────────────────────
    var isSearchActive by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        snapshotFlow { searchText }
            .collect { viewModel.setSearchQuery(it) }
    }

    // ── Snackbar ─────────────────────────────────────────────────────────────
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbar()
        }
    }

    // ── Pull-to-refresh haptic ────────────────────────────────────────────────
    var thresholdCrossed by remember { mutableStateOf(false) }
    LaunchedEffect(pullRefreshState) {
        snapshotFlow { pullRefreshState.distanceFraction }
            .collectLatest { fraction ->
                if (fraction >= 1f && !thresholdCrossed && !uiState.isRefreshing) {
                    thresholdCrossed = true
                    HapticUtils.heavyClick(activity)
                } else if (fraction < 1f) {
                    thresholdCrossed = false
                }
            }
    }

    // ── Scroll to top + haptic when refresh completes ────────────────────────
    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isRefreshing) {
        if (wasRefreshing && !uiState.isRefreshing) {
            HapticUtils.hardStop(activity)
            listState.animateScrollToItem(0)
        }
        wasRefreshing = uiState.isRefreshing
    }

    // ── Drawer + Scaffold ────────────────────────────────────────────────────
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                feeds              = uiState.feeds,
                selectedFeedId     = uiState.selectedFeedId,
                onAllArticlesClick = {
                    viewModel.selectFeed(null)
                    scope.launch { drawerState.close() }
                },
                onFeedClick = { feed ->
                    viewModel.selectFeed(feed.id)
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
                Column {
                    if (isSearchActive) {
                        // ── Search mode TopAppBar ────────────────────────────
                        TopAppBar(
                            navigationIcon = {
                                IconButton(onClick = {
                                    isSearchActive = false
                                    searchText = ""
                                    viewModel.setSearchQuery("")
                                }) {
                                    Icon(
                                        Icons.Filled.ArrowBack,
                                        contentDescription = "Close Search"
                                    )
                                }
                            },
                            title = {
                                OutlinedTextField(
                                    value = searchText,
                                    onValueChange = { searchText = it },
                                    placeholder = { Text("Search articles...") },
                                    trailingIcon = {
                                        if (searchText.isNotEmpty()) {
                                            IconButton(onClick = { searchText = "" }) {
                                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    } else {
                        // ── Normal TopAppBar ─────────────────────────────────
                        TopAppBar(
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Filled.Menu, contentDescription = "Menu")
                                }
                            },
                            title = {
                                Text(
                                    text = if (uiState.selectedFeedId == null) "Flow"
                                    else uiState.feeds.find { it.id == uiState.selectedFeedId }?.title
                                        ?: "Flow",
                                    modifier = Modifier.clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) {
                                        scope.launch {
                                            HapticUtils.tick(activity)
                                            listState.animateScrollToItem(0)
                                            HapticUtils.hardStop(activity)
                                        }
                                    }
                                )
                            },
                            actions = {
                                IconButton(onClick = { isSearchActive = true }) {
                                    Icon(Icons.Filled.Search, contentDescription = "Search")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }

                    // ── Offline prefetch progress indicator ──────────────────
                    AnimatedVisibility(visible = uiState.isPrefetching) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Saving for offline reading…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${uiState.prefetchProgress}/${uiState.prefetchTotal}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = {
                                    if (uiState.prefetchTotal > 0)
                                        uiState.prefetchProgress.toFloat() / uiState.prefetchTotal.toFloat()
                                    else 0f
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                    // ─────────────────────────────────────────────────────────
                }
            }
        ) { paddingValues ->
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = {
                    HapticUtils.thud(activity)
                    viewModel.refresh()
                },
                state = pullRefreshState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when {
                    // ── Search results ───────────────────────────────────────
                    isSearchActive && searchText.isNotBlank() -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            if (searchResults.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No results for \"$searchText\"",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                items(searchResults, key = { it.url }) { article ->
                                    ArticleCard(
                                        article = article,
                                        onClick = { onArticleClick(article.id) }
                                    )
                                }
                            }
                        }
                    }

                    // ── Empty state ──────────────────────────────────────────
                    uiState.filteredArticles.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "No articles yet",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Pull down to refresh\nor add feeds from the menu",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(onClick = { viewModel.refresh() }) {
                                    Text("Refresh Now")
                                }
                            }
                        }
                    }

                    // ── Normal article feed ──────────────────────────────────
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(
                                items = uiState.filteredArticles,
                                key = { it.id },
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
