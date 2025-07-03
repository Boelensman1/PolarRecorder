package com.wboelens.polarrecorder.viewModels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FileSystemSettingsViewModel : ViewModel() {
  private val _selectedDirectory = MutableStateFlow<String>("")
  val selectedDirectory: StateFlow<String> = _selectedDirectory.asStateFlow()

  fun createDirectoryIntent(): Intent {
    return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
      addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
      addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)

      // Set initial directory to Downloads folder
      val downloadsDir =
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
      val initialUri = Uri.fromFile(downloadsDir)
      putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
    }
  }

  fun handleDirectoryResult(context: Context, uri: Uri?) {
    uri?.let { selectedUri ->
      // Persist permissions
      context.contentResolver.takePersistableUriPermission(
          selectedUri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
      )

      _selectedDirectory.value = selectedUri.toString()
    }
  }
}
