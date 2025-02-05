package com.wboelens.polarrecorder

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.managers.PermissionManager
import com.wboelens.polarrecorder.managers.PolarManager
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.managers.RecordingManager
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
            applicationContext, polarManager, logViewModel, deviceViewModel, dataSavers)

    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK) {
        fileSystemViewModel.handleDirectoryResult(this, result.data?.data)
      }
    }

    setContent {
      AppTheme {
        val navController = rememberNavController()

        LaunchedEffect(Unit) {
          permissionManager.checkAndRequestPermissions {
            Log.d(TAG, "Necessary permissions for scanning granted")
            if (navController.currentDestination?.route == "deviceSelection") {
              polarManager.startPeriodicScanning()
            }
          }
        }

        NavHost(navController = navController, startDestination = "deviceSelection") {
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
                onContinue = { navController.navigate("recording") })
          }
          composable("recording") {
            RecordingScreen(
                deviceViewModel = deviceViewModel,
                logViewModel = logViewModel,
                recordingManager = recordingManager,
                dataSavers = dataSavers,
                onBackPressed = { navController.navigateUp() })
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
