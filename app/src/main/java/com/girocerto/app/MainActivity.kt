package com.girocerto.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.girocerto.app.theme.GiroCertoTheme
import com.girocerto.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        com.girocerto.overlay.OverlayController.syncCopilot(this)
        GiroCertoApp.instance.appUpdates.check()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GiroCertoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                    com.girocerto.update.AppUpdatePrompt()
                }
            }
        }
    }
}
