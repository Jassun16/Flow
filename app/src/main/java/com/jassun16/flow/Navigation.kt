package com.jassun16.flow

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jassun16.flow.ui.screens.BookmarksScreen
import com.jassun16.flow.ui.screens.FeedsScreen
import com.jassun16.flow.ui.screens.HomeScreen
import com.jassun16.flow.ui.screens.ReaderScreen
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween


// ── Route Definitions ──────────────────────────────────────────────────────
// Centralising route strings here means if you rename a route,
// you only change it in ONE place — not scattered across 10 files

object Routes {
    const val HOME      = "home"
    const val READER    = "reader/{articleId}"
    const val FEEDS     = "feeds"
    const val BOOKMARKS = "bookmarks"

    // Helper to build reader route with actual ID
    // Usage: Routes.reader(123L) → "reader/123"
    fun reader(articleId: Long) = "reader/$articleId"
}

@Composable
fun FlowNavigation() {
    // NavController is the GPS unit — remembers back stack, handles navigation
    val navController = rememberNavController()

    // NavHost watches the NavController and renders the correct screen
    NavHost(
        navController    = navController,
        startDestination = Routes.HOME,
        enterTransition    = { fadeIn(animationSpec  = tween(300)) },
        exitTransition     = { fadeOut(animationSpec = tween(300)) },
        popEnterTransition = { fadeIn(animationSpec  = tween(300)) },
        popExitTransition  = { fadeOut(animationSpec = tween(300)) }
    ) {

        // ── Home Screen ────────────────────────────────────────────────────
        composable(Routes.HOME) {
            HomeScreen(
                onArticleClick  = { articleId ->
                    // Navigate to reader, passing article ID in the route
                    navController.navigate(Routes.reader(articleId))
                },
                onFeedsClick    = {
                    navController.navigate(Routes.FEEDS)
                },
                onBookmarksClick = {
                    navController.navigate(Routes.BOOKMARKS)
                }
            )
        }

        // ── Reader Screen ──────────────────────────────────────────────────
        composable(
            route     = Routes.READER,
            arguments = listOf(
                navArgument("articleId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val articleId = backStackEntry.arguments?.getLong("articleId") ?: 0L
            ReaderScreen(
                articleId = articleId,
                onBack    = { navController.popBackStack() }
            )
        }


        // ── Feeds Management Screen ────────────────────────────────────────
        composable(Routes.FEEDS) {
            FeedsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // ── Bookmarks Screen ───────────────────────────────────────────────
        composable(Routes.BOOKMARKS) {
            BookmarksScreen(
                onBack         = { navController.popBackStack() },
                onArticleClick = { articleId ->
                    navController.navigate(Routes.reader(articleId))
                }
            )
        }
    }
}
