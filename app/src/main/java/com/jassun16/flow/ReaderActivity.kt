package com.jassun16.flow

import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.jassun16.flow.ui.screens.ReaderScreen
import com.jassun16.flow.ui.theme.FlowTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

@AndroidEntryPoint
class ReaderActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ARTICLE_ID = "articleId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        enableEdgeToEdge(                                    // ← BEFORE super
            statusBarStyle = SystemBarStyle.light(
                scrim     = android.graphics.Color.BLACK,
                darkScrim = android.graphics.Color.BLACK
            )
        )
        super.onCreate(savedInstanceState)

        val articleId = intent.getLongExtra(EXTRA_ARTICLE_ID, -1L)
        if (articleId == -1L) return finish()

        setContent {
            FlowTheme {
                ReaderScreen(articleId = articleId, onBack = { finish() })
            }
        }
    }
}
