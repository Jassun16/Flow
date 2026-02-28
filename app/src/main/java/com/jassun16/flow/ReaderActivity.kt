package com.jassun16.flow

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.jassun16.flow.ui.screens.ReaderScreen
import com.jassun16.flow.ui.theme.FlowTheme
import dagger.hilt.android.AndroidEntryPoint
import android.view.Window


@AndroidEntryPoint
class ReaderActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ARTICLE_ID = "articleId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)  // ← replaces supportActionBar?.hide()
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        super.onCreate(savedInstanceState)

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
