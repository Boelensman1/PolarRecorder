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

class MainActivity : ComponentActivity() {
  private val deviceViewModel: DeviceViewModel by viewModels()
  private val logViewModel: LogViewModel by viewModels()
  private val fileSystemViewModel: FileSystemSettingsViewModel by viewModels()

  private val polarRecorderApplication: PolarRecorderApplication
    get() = application as PolarRecorderApplication

  private val polarManager: PolarManager
    get() = polarRecorderApplication.polarManager
  private lateinit var permissionManager: PermissionManager

  private val recordingManager: RecordingManager
    get() = polarRecorderApplication.recordingManager
  private val preferencesManager: PreferencesManager
    get() = polarRecorderApplication.preferencesManager
  private val dataSavers: DataSavers
    get() = polarRecorderApplication.dataSavers

  companion object {
    private const val TAG = "PolarManager"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "onCreate: Initializing MainActivity")

    permissionManager = PermissionManager(this)

    deviceViewModel.setup(polarRecorderApplication.polarRepository)
    logViewModel.setup(polarRecorderApplication.logRepository)

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

        Scaffold(
            snackbarHost = {
              LogMessageSnackbarHost(
                  snackbarHostState,
                  currentLogType,
              )
            },
        ) { paddingValues ->
          NavHost(
              navController = navController,
              startDestination = "deviceSelection",
              modifier = Modifier.padding(paddingValues),
          ) {
            composable("deviceSelection") {
              DeviceSelectionScreen(
                  deviceViewModel = deviceViewModel,
                  polarManager = polarManager,
                  onContinue = { navController.navigate("deviceConnection") },
              )
            }
            composable("deviceConnection") {
              DeviceConnectionScreen(
                  deviceViewModel = deviceViewModel,
                  polarManager = polarManager,
                  onBackPressed = { navController.navigateUp() },
                  onContinue = { navController.navigate("deviceSettings") },
              )
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
                  onContinue = { navController.navigate("recordingSettings") },
              )
            }
            composable("recordingSettings") {
              RecordingSettingsScreen(
                  deviceViewModel = deviceViewModel,
                  fileSystemSettingsViewModel = fileSystemViewModel,
                  dataSavers = dataSavers,
                  preferencesManager = preferencesManager,
                  onBackPressed = { navController.navigateUp() },
                  onContinue = { navController.navigate("dataSaverInitialization") },
              )
            }
            composable("dataSaverInitialization") {
              DataSaverInitializationScreen(
                  dataSavers = dataSavers,
                  deviceViewModel = deviceViewModel,
                  recordingManager = recordingManager,
                  preferencesManager = preferencesManager,
                  onBackPressed = { navController.navigateUp() },
                  onContinue = { navController.navigate("recording") },
              )
            }
            composable("recording") {
              // skip data saver initialisation screen
              val backAction = {
                if (recordingManager.isRecording.value) {
                  recordingManager.stopRecording()
                }
                navController.navigate("recordingSettings") {
                  popUpTo("recordingSettings") { inclusive = true }
                }
              }

              BackHandler(onBack = backAction)
              RecordingScreen(
                  deviceViewModel = deviceViewModel,
                  recordingManager = recordingManager,
                  dataSavers = dataSavers,
                  onBackPressed = backAction,
                  onRestartRecording = { navController.navigate("dataSaverInitialization") },
              )
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
