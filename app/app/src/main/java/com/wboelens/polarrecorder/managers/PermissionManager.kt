package com.wboelens.polarrecorder.managers

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class PermissionManager(private val activity: Activity) {
  private val requiredPermissions: Array<String>
    get() =
        when {
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.POST_NOTIFICATIONS)
          }
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.FOREGROUND_SERVICE)
          }
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.FOREGROUND_SERVICE)
          }
          else -> {
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN)
          }
        }

  private val permissionLauncher =
      (activity as ComponentActivity).registerForActivityResult(
          ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.all { it.value }
            if (allGranted) {
              pendingPermissionCallback?.invoke()
            } else {
              // TODO: show message explaining why we need these permissions
            }
          }

  private var pendingPermissionCallback: (() -> Unit)? = null

  fun checkAndRequestPermissions(onPermissionsGranted: () -> Unit) {
    val missingPermissions =
        requiredPermissions
            .filter { activity.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            .toTypedArray()

    if (missingPermissions.isEmpty()) {
      onPermissionsGranted()
    } else {
      missingPermissions.forEach { Log.d("PermissionManager", "Requesting permission: $it") }
      pendingPermissionCallback = onPermissionsGranted
      permissionLauncher.launch(missingPermissions)
    }
  }
}
