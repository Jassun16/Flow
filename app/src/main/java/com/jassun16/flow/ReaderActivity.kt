package com.jassun16.flow

import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jassun16.flow.ui.screens.ReaderScreen
import com.jassun16.flow.ui.theme.FlowTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ReaderActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ARTICLE_ID = "articleId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val articleId = intent.getLongExtra(EXTRA_ARTICLE_ID, -1L)
        if (articleId == -1L) return finish()

        setContent {
            FlowTheme {
                ReaderScreen(
                    articleId = articleId,
                    onBack    = { finish() }
                )
            }
        }
    }
}
