package com.wboelens.polarrecorder

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.managers.PermissionManager
import com.wboelens.polarrecorder.managers.PolarManager
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.managers.RecordingManager
import com.wboelens.polarrecorder.ui.components.LogMessageSnackbarHost
import com.wboelens.polarrecorder.ui.components.SnackbarMessageDisplayer
import com.wboelens.polarrecorder.ui.screens.DataSaverInitializationScreen
import com.wboelens.polarrecorder.ui.screens.DeviceConnectionScreen
import com.wboelens.polarrecorder.ui.screens.DeviceSelectionScreen
import com.wboelens.polarrecorder.ui.screens.DeviceSettingsScreen
import com.wboelens.polarrecorder.ui.screens.RecordingScreen
import com.wboelens.polarrecorder.ui.screens.RecordingSettingsScreen
import com.wboelens.polarrecorder.ui.theme.AppTheme
import com.wboelens.polarrecorder.viewModels.DeviceViewModel
import com.wboelens.polarrecorder.viewModels.FileSystemSettingsViewModel
import com.wboelens.polarrecorder.viewModels.LogViewModel
import kotlinx.coroutines.MainScope

class MainActivity : ComponentActivity() {
  private val deviceViewModel: DeviceViewModel by viewModels()
  private val logViewModel: LogViewModel by viewModels()
  private val fileSystemViewModel: FileSystemSettingsViewModel by viewModels()
  private lateinit var polarManager: PolarManager
  private lateinit var permissionManager: PermissionManager
  private lateinit var recordingManager: RecordingManager
  private lateinit var preferencesManager: PreferencesManager
  private lateinit var dataSavers: DataSavers

  companion object {
    private const val TAG = "PolarManager"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "onCreate: Initializing MainActivity")
    Log.d(TAG, "onCreate: Initializing MainActivity")

    // Load saved settings
    this.preferencesManager = PreferencesManager(applicationContext)

    // Init datasavers
    this.dataSavers = DataSavers(applicationContext, logViewModel, this.preferencesManager)

    permissionManager = PermissionManager(this)
    polarManager = PolarManager(applicationContext, deviceViewModel, logViewModel)
    recordingManager =
        RecordingManager(
            applicationContext,
            MainScope(),
            polarManager,
            logViewModel,
            deviceViewModel,
            preferencesManager,
            dataSavers)

    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK) {
        fileSystemViewModel.handleDirectoryResult(this, result.data?.data)
      }
    }

    setContent {
      AppTheme {
        val navController = rememberNavController()

        // Get the snackbarHostState from the ErrorHandler
        val (snackbarHostState, currentLogType) =
            SnackbarMessageDisplayer(logViewModel = logViewModel)

        LaunchedEffect(Unit) {
          permissionManager.checkAndRequestPermissions {
            Log.d(TAG, "Necessary permissions for scanning granted")
            if (navController.currentDestination?.route == "deviceSelection") {
              polarManager.startPeriodicScanning()
            }
          }
        }

        Scaffold(snackbarHost = { LogMessageSnackbarHost(snackbarHostState, currentLogType) }) {
            paddingValues ->
          NavHost(
              navController = navController,
              startDestination = "deviceSelection",
              modifier = Modifier.padding(paddingValues)) {
                composable("deviceSelection") {
                  DeviceSelectionScreen(
                      deviceViewModel = deviceViewModel,
                      polarManager = polarManager,
                      onContinue = { navController.navigate("deviceConnection") })
                }
                composable("deviceConnection") {
                  DeviceConnectionScreen(
                      deviceViewModel = deviceViewModel,
                      polarManager = polarManager,
                      onBackPressed = { navController.navigateUp() },
                      onContinue = { navController.navigate("deviceSettings") })
                }
                composable("deviceSettings") {
                  // skip device connection screen
                  val backAction = {
                    polarManager.disconnectAllDevices()
                    navController.navigate("deviceSelection") {
                      popUpTo("deviceSelection") { inclusive = true }
                    }
                  }

                  BackHandler(onBack = backAction)
                  DeviceSettingsScreen(
                      deviceViewModel = deviceViewModel,
                      polarManager = polarManager,
                      onBackPressed = backAction,
                      onContinue = { navController.navigate("recordingSettings") })
                }
                composable("recordingSettings") {
                  RecordingSettingsScreen(
                      deviceViewModel = deviceViewModel,
                      fileSystemSettingsViewModel = fileSystemViewModel,
                      recordingManager = recordingManager,
                      dataSavers = dataSavers,
                      preferencesManager = preferencesManager,
                      onBackPressed = { navController.navigateUp() },
                      onContinue = { navController.navigate("dataSaverInitialization") })
                }
                composable("dataSaverInitialization") {
                  DataSaverInitializationScreen(
                      dataSavers = dataSavers,
                      deviceViewModel = deviceViewModel,
                      recordingManager = recordingManager,
                      onBackPressed = { navController.navigateUp() },
                      onContinue = { navController.navigate("recording") })
                }
                composable("recording") {
                  // skip data saver initialisation screen
                  val backAction = {
                    recordingManager.stopRecording()
                    navController.navigate("recordingSettings") {
                      popUpTo("recordingSettings") { inclusive = true }
                    }
                  }

                  RecordingScreen(
                      deviceViewModel = deviceViewModel,
                      logViewModel = logViewModel,
                      recordingManager = recordingManager,
                      dataSavers = dataSavers,
                      onBackPressed = backAction)
                }
              }
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    polarManager.cleanup()
    recordingManager.cleanup()
  }
}
