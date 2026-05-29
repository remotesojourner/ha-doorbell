package com.novasoftware.hadoorbell

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.first
import com.novasoftware.hadoorbell.ui.theme.HaDoorbellTheme
import com.novasoftware.hadoorbell.data.AppPreferences
import com.novasoftware.hadoorbell.ui.screens.SettingsScreen
import com.novasoftware.hadoorbell.ui.screens.StreamScreen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text

class MainActivity : FragmentActivity() {
    private var permissionGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            permissionGranted = isGranted
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            permissionGranted = true
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        val appPreferences = AppPreferences(applicationContext)
        
        enableEdgeToEdge()

        setContent {
            if (!permissionGranted) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Please grant microphone permission to continue.")
                }
                return@setContent
            }

            HaDoorbellTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var startDestination by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(Unit) {
                        val url = appPreferences.haUrlFlow.first()
                        val token = appPreferences.haTokenFlow.first()
                        val source = appPreferences.streamSourceFlow.first()

                        startDestination = if (!url.isNullOrBlank() && !token.isNullOrBlank() && !source.isNullOrBlank()) {
                            "stream"
                        } else {
                            "settings"
                        }
                    }

                    startDestination?.let { startDest ->
                        val navController = rememberNavController()
                        NavHost(navController = navController, startDestination = startDest) {
                            composable("settings") {
                                SettingsScreen(
                                    appPreferences = appPreferences,
                                    onSave = {
                                        // If we came from stream, pop back. Else navigate to stream.
                                        if (navController.previousBackStackEntry != null) {
                                            navController.popBackStack()
                                        } else {
                                            navController.navigate("stream") {
                                                popUpTo("settings") { inclusive = true }
                                            }
                                        }
                                    },
                                    onCancel = {
                                        navController.popBackStack()
                                    },
                                    canCancel = navController.previousBackStackEntry != null
                                )
                            }
                            composable("stream") {
                                StreamScreen(appPreferences, 
                                    onNavigateToSettings = {
                                        navController.navigate("settings")
                                    },
                                    onExit = {
                                        finish() // Exit the app
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
