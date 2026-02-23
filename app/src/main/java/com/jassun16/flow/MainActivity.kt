package com.jassun16.flow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.jassun16.flow.ui.theme.FlowTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {

        // SplashScreen must be installed BEFORE super.onCreate()
        // Shows your app icon on a clean background while Hilt + DB initialise
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()   // content draws behind status/nav bars

        setContent {
            FlowTheme {
                // Single entry point — Navigation handles everything from here
                FlowNavigation()
            }
        }
    }
}
