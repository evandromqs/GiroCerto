package com.girocerto.app

import android.content.Intent
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
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    companion object {
        val targetScreen = MutableStateFlow<String?>(null)
    }

    override fun onResume() {
        super.onResume()
        com.girocerto.overlay.OverlayController.syncCopilot(this)
        GiroCertoApp.instance.appUpdates.onResume()
        GiroCertoApp.instance.appUpdates.check()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("target_screen")?.let { target ->
            targetScreen.value = target
        }
        handleAuthIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra("target_screen")?.let { target ->
            targetScreen.value = target
        }
        handleAuthIntent(intent)
        enableEdgeToEdge()
        setContent {
            GiroCertoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    com.girocerto.onboarding.AppEntry()
                }
            }
        }
    }

    private fun handleAuthIntent(intent: Intent) {
        if (intent.data?.scheme == "girocerto" && intent.data?.host == "auth") {
            intent.data?.getQueryParameter("code")?.let { GiroCertoApp.instance.auth.exchange(it) }
        }
    }
}
