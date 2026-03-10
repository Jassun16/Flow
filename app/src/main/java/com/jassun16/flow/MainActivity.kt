package com.jassun16.flow

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

        // Single source of truth for splash dismissal
        var keepSplash = true
        splashScreen.setKeepOnScreenCondition { keepSplash }

        // Dismiss after full animation completes (star1 + star2 + star3 + lines + hold)
        Handler(Looper.getMainLooper()).postDelayed({
            keepSplash = false
        }, 2300)

        setContent {
            FlowTheme {
                FlowNavigation()
            }
        }
    }
}