package io.github.demiterhugo.debuglens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.demiterhugo.debuglens.ui.home.HomeRoute
import io.github.demiterhugo.debuglens.ui.theme.DebugLensTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DebugLensTheme {
                HomeRoute()
            }
        }
    }
}
