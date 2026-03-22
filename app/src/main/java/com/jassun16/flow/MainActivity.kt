package com.jassun16.flow

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.jassun16.flow.ui.theme.FlowTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        var keepSplash = true
        splashScreen.setKeepOnScreenCondition { keepSplash }

        Handler(Looper.getMainLooper()).postDelayed({
            keepSplash = false
        }, 2300)

        // ── Request All Files Access if not granted ────────────
        if (!Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${packageName}")
            }
            startActivity(intent)
        }

        setContent {
            FlowTheme {
                FlowNavigation()
            }
        }
    }
}