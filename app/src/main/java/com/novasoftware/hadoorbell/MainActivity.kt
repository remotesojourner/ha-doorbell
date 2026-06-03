package com.novasoftware.hadoorbell

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.novasoftware.hadoorbell.ui.Navigation
import com.novasoftware.hadoorbell.ui.settings.SettingsScreen
import com.novasoftware.hadoorbell.ui.stream.StreamScreen
import com.novasoftware.hadoorbell.ui.theme.HaDoorbellTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
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
                    val mainViewModel: MainViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
                    val startDestination by mainViewModel.startDestination.collectAsState()

                    startDestination?.let { startDest ->
                        val navController = rememberNavController()
                        NavHost(navController = navController, startDestination = startDest) {
                            composable(Navigation.ROUTE_SETTINGS) {
                                val settingsViewModel: com.novasoftware.hadoorbell.ui.settings.SettingsViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
                                SettingsScreen(
                                    onSave = {
                                        // If we came from stream, pop back. Else navigate to stream.
                                        if (navController.previousBackStackEntry != null) {
                                            navController.popBackStack()
                                        } else {
                                            navController.navigate(Navigation.ROUTE_STREAM) {
                                                popUpTo(Navigation.ROUTE_SETTINGS) { inclusive = true }
                                            }
                                        }
                                    },
                                    onCancel = {
                                        navController.popBackStack()
                                    },
                                    canCancel = navController.previousBackStackEntry != null,
                                    viewModel = settingsViewModel
                                )
                            }
                            composable(Navigation.ROUTE_STREAM) {
                                val streamViewModel: com.novasoftware.hadoorbell.ui.stream.StreamViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
                                StreamScreen(
                                    onNavigateToSettings = {
                                        navController.navigate(Navigation.ROUTE_SETTINGS)
                                    },
                                    onExit = {
                                        finish() // Exit the app
                                    },
                                    viewModel = streamViewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
