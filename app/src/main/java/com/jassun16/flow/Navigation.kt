package com.jassun16.flow

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jassun16.flow.ui.screens.BookmarksScreen
import com.jassun16.flow.ui.screens.FeedsScreen
import com.jassun16.flow.ui.screens.HomeScreen
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween

object Routes {
    const val HOME      = "home"
    const val FEEDS     = "feeds"
    const val BOOKMARKS = "bookmarks"
    // READER route removed entirely
}

@Composable
fun FlowNavigation() {
    val navController = rememberNavController()
    val context       = LocalContext.current

    NavHost(
        navController    = navController,
        startDestination = Routes.HOME,
        enterTransition    = { fadeIn(animationSpec  = tween(300)) },
        exitTransition     = { fadeOut(animationSpec = tween(300)) },
        popEnterTransition = { fadeIn(animationSpec  = tween(300)) },
        popExitTransition  = { fadeOut(animationSpec = tween(300)) }
    ) {

        composable(Routes.HOME) {
            HomeScreen(
                onArticleClick = { articleId ->
                    val intent = Intent(context, ReaderActivity::class.java).apply {
                        putExtra(ReaderActivity.EXTRA_ARTICLE_ID, articleId)
                    }
                    context.startActivity(intent)
                },
                onFeedsClick    = { navController.navigate(Routes.FEEDS) },
                onBookmarksClick = { navController.navigate(Routes.BOOKMARKS) }
            )
        }

        composable(Routes.FEEDS) {
            FeedsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.BOOKMARKS) {
            BookmarksScreen(
                onBack         = { navController.popBackStack() },
                onArticleClick = { articleId ->
                    val intent = Intent(context, ReaderActivity::class.java).apply {
                        putExtra(ReaderActivity.EXTRA_ARTICLE_ID, articleId)
                    }
                    context.startActivity(intent)
                }
            )
        }
    }
}
