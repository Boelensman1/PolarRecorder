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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.managers.PermissionManager
import com.wboelens.polarrecorder.managers.PolarManager
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.services.RecordingServiceConnection
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
import com.wboelens.polarrecorder.viewModels.ViewModelFactory

class MainActivity : ComponentActivity() {
  // Get Application instance for accessing Application-scoped state
  private val app: PolarRecorderApplication
    get() = application as PolarRecorderApplication

  // ViewModels use factory to inject Application-scoped state
  private val deviceViewModel: DeviceViewModel by viewModels {
    ViewModelFactory(app.deviceState, app.logState)
  }
  private val logViewModel: LogViewModel by viewModels {
    ViewModelFactory(app.deviceState, app.logState)
  }
  private val fileSystemViewModel: FileSystemSettingsViewModel by viewModels()

  // These are now retrieved from Application
  private lateinit var polarManager: PolarManager
  private lateinit var permissionManager: PermissionManager
  private lateinit var preferencesManager: PreferencesManager
  private lateinit var dataSavers: DataSavers

  // Service connection for recording control
  private lateinit var serviceConnection: RecordingServiceConnection

  companion object {
    private const val TAG = "MainActivity"
  }

  @Suppress("LongMethod")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "onCreate: Initializing MainActivity")

    // Get preferences from Application (always available)
    preferencesManager = app.preferencesManager

    // Initialize managers in Application (creates them if they don't exist)
    app.ensureManagersInitialized()

    // Get references to Application-scoped managers
    polarManager = app.polarManager!!
    dataSavers = app.dataSavers!!

    // Get service connection from Application
    serviceConnection = app.getServiceConnection()

    // Determine start destination based on recording state
    val startDestination = if (app.isRecordingActive) "recording" else "deviceSelection"

    permissionManager = PermissionManager(this)

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
              startDestination = startDestination,
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
                  serviceConnection = serviceConnection,
                  preferencesManager = preferencesManager,
                  onBackPressed = { navController.navigateUp() },
                  onContinue = { navController.navigate("recording") },
              )
            }
            composable("recording") {
              // Observe recording state from service
              val binder by serviceConnection.binder.collectAsState()
              val recordingState by
                  binder?.recordingState?.collectAsState()
                      ?: androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateOf(
                            com.wboelens.polarrecorder.services.RecordingState())
                      }

              // skip data saver initialisation screen
              val backAction = {
                if (recordingState.isRecording) {
                  serviceConnection.stopRecordingService()
                }
                navController.navigate("recordingSettings") {
                  popUpTo("recordingSettings") { inclusive = true }
                }
              }

              BackHandler(onBack = backAction)
              RecordingScreen(
                  deviceViewModel = deviceViewModel,
                  serviceConnection = serviceConnection,
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

  override fun onStart() {
    super.onStart()
    // Always bind to service to observe state
    serviceConnection.bind()
    Log.d(TAG, "Service bound")
  }

  override fun onStop() {
    super.onStop()
    // Unbind from service (service keeps running if recording)
    serviceConnection.unbind()
    Log.d(TAG, "Service unbound")
  }

  override fun onDestroy() {
    super.onDestroy()
    // Only cleanup managers if no recording is active
    // Managers will persist in Application scope if recording continues
    if (!app.isRecordingActive) {
      app.cleanupIfNotRecording()
    }
  }
}
