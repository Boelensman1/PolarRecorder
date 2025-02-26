package com.wboelens.polarrecorder.dataSavers

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.wboelens.polarrecorder.managers.DeviceInfoForDataSaver
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.viewModels.LogViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

data class FileSystemDataSaverConfig(val baseDirectory: String = "", val splitAtSizeMb: Int = 0)

class FileSystemDataSaver(
    private val context: Context,
    logViewModel: LogViewModel,
    preferencesManager: PreferencesManager
) : DataSaver(logViewModel, preferencesManager) {

  companion object {
    private const val FILE_ROTATION_CHECK_INTERVAL = 60000L // Check every minute
  }

  private var config: FileSystemDataSaverConfig = FileSystemDataSaverConfig()
  private var pickedDir: DocumentFile? = null
  var recordingDir: DocumentFile? = null
  private val outputStreams = mutableMapOf<String, Pair<DocumentFile, java.io.OutputStream>>()
  private val filePartNumbers = mutableMapOf<String, Int>()
  private val rotationLocks = mutableMapOf<String, Any>()
  private var rotationCheckJob: kotlinx.coroutines.Job? = null
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  override val isConfigured: Boolean
    get() = config.baseDirectory.isNotEmpty()

  private fun setEnabled(enable: Boolean) {
    _isEnabled.value = enable
    preferencesManager.fileSystemEnabled = enable
  }

  fun configure(config: FileSystemDataSaverConfig) {
    preferencesManager.fileSystemDataSaverConfig = config
    this.config = config
  }

  override fun enable() {
    if (config.baseDirectory.isEmpty()) {
      addErrorMessage("Base directory must be configured before starting")
      return
    }

    try {
      val selectedUri = Uri.parse(config.baseDirectory)
      val userPickedDir = DocumentFile.fromTreeUri(context, selectedUri)

      if (userPickedDir == null) {
        logViewModel.addLogMessage("pickedDir is null, could not construct uri")
        return
      }

      pickedDir = userPickedDir
      setEnabled(true)
      logViewModel.addLogMessage("File system recording enabled at: ${userPickedDir.uri}")
    } catch (e: SecurityException) {
      _isEnabled.value = false
      addErrorMessage("Permission denied accessing directory: ${e.message}")
    } catch (e: IllegalArgumentException) {
      _isEnabled.value = false
      addErrorMessage("Invalid directory URI: ${e.message}")
    }
  }

  override fun disable() {
    setEnabled(false)
  }

  private fun getNextFileName(deviceId: String, baseFileName: String, dataType: String): String {
    val key = "$deviceId/$dataType"
    val partNumber = filePartNumbers.getOrDefault(key, 1)
    return if (partNumber == 1) {
      baseFileName
    } else {
      baseFileName.replace(".jsonl", "_part${partNumber}.jsonl")
    }
  }

  private fun checkAndRotateFile(deviceId: String, dataType: String) {
    if (config.splitAtSizeMb <= 0) return

    val key = "$deviceId/$dataType"
    val lock = rotationLocks.getOrPut(key) { Any() }

    synchronized(lock) {
      val streamPair = outputStreams[key] ?: return
      val currentFile = streamPair.first

      @Suppress("MagicNumber")
      if (currentFile.length() > config.splitAtSizeMb * 1024 * 1024) {
        try {
          streamPair.second.close()
        } catch (e: IOException) {
          logViewModel.addLogError("Error closing old stream: ${e.message}")
        }

        filePartNumbers[key] = (filePartNumbers[key] ?: 1) + 1

        val fileName = getNextFileName(deviceId, "$dataType.jsonl", dataType)
        val currentRecordingDir = currentFile.parentFile

        try {
          val newFile = currentRecordingDir?.createFile("application/json", fileName)
          if (newFile != null) {
            val newStream = context.contentResolver.openOutputStream(newFile.uri, "wa")
            if (newStream != null) {
              outputStreams[key] = Pair(newFile, newStream)
              logViewModel.addLogMessage("Created new file part for $deviceId/$dataType: $fileName")
            }
          }
        } catch (e: SecurityException) {
          logViewModel.addLogError("Permission denied while creating new file part: ${e.message}")
          outputStreams.remove(key)
        } catch (e: IOException) {
          logViewModel.addLogError("I/O error while creating new file part: ${e.message}")
          outputStreams.remove(key)
        } catch (e: IllegalArgumentException) {
          logViewModel.addLogError("Invalid arguments while creating new file part: ${e.message}")
          outputStreams.remove(key)
        }
      }
    }
  }

  override fun saveData(
      phoneTimestamp: Long,
      deviceId: String,
      recordingName: String,
      dataType: String,
      payload: String
  ) {
    val key = "$deviceId/$dataType"
    val lock = rotationLocks.getOrPut(key) { Any() }

    synchronized(lock) {
      try {
        // Get fresh reference to stream after synchronization
        val streamPair = outputStreams[key]
        check(streamPair != null) {
          "No output stream initialized for data type: $deviceId/$dataType"
        }

        val payloadAsByteArray = (payload + "\n").toByteArray()
        streamPair.second.write(payloadAsByteArray)

        if (!firstMessageSaved[key]!!) {
          logViewModel.addLogMessage(
              "Successfully saved $dataType first data to: ${Uri.decode(streamPair.first.uri.toString())}")
          firstMessageSaved[key] = true
        }
      } catch (e: IOException) {
        // If stream was closed, try to rotate file immediately
        logViewModel.addLogError(
            "Failed to write data to file: ${e.message}. Attempting emergency rotation")
        checkAndRotateFile(deviceId, dataType)
      } catch (e: IllegalStateException) {
        logViewModel.addLogError("Failed to save data to file system: ${e.message}")
      }
    }
  }

  @Suppress("NestedBlockDepth", "ReturnCount")
  override fun initSaving(
      recordingName: String,
      deviceIdsWithInfo: Map<String, DeviceInfoForDataSaver>
  ) {
    super.initSaving(recordingName, deviceIdsWithInfo)
    filePartNumbers.clear()

    if (config.splitAtSizeMb > 0) {
      rotationCheckJob =
          scope.launch {
            while (true) {
              delay(FILE_ROTATION_CHECK_INTERVAL)
              outputStreams.keys.forEach { key ->
                val parts = key.split("/")
                if (parts.size == 2) {
                  checkAndRotateFile(parts[0], parts[1])
                } else {
                  logViewModel.addLogError("Invalid key format: $key, expected deviceId/dataType")
                }
              }
            }
          }
    }

    if (pickedDir == null) {
      logViewModel.addLogError("pickedDir is null")
      return
    }

    recordingDir = pickedDir?.createDirectory(recordingName)

    for ((deviceId, info) in deviceIdsWithInfo) {
      val currentRecordingDir = recordingDir?.createDirectory(info.deviceName)

      if (recordingDir == null) {
        logViewModel.addLogError("recordingDir is null")
        return
      }

      for (dataType in info.dataTypes) {
        val fileName = "$dataType.jsonl"
        val key = "$deviceId/$dataType"

        if (currentRecordingDir == null) {
          logViewModel.addLogError("currentRecordingDir is null")
          return
        }

        val file =
            currentRecordingDir.findFile(fileName)
                ?: currentRecordingDir.createFile("application/jsonl", fileName)

        if (file == null) {
          logViewModel.addLogError(
              "Failed to create or access file $fileName in ${Uri.decode(currentRecordingDir.uri.toString())}")
          return
        }

        val stream = context.contentResolver.openOutputStream(file.uri, "wa")
        if (stream == null) {
          logViewModel.addLogError("Failed to create or access stream ${file.uri}")
          return
        }
        val streamPair = Pair(file, stream)
        outputStreams[key] = streamPair
        firstMessageSaved[key] = false
      }
    }
  }

  override fun stopSaving() {
    rotationCheckJob?.cancel()
    rotationCheckJob = null

    outputStreams.values.forEach { (_, stream) ->
      try {
        stream.close()
      } catch (e: IOException) {
        logViewModel.addLogError("Failed to close output stream: ${e.message}")
      }
    }
    outputStreams.clear()
    rotationLocks.clear()
  }

  override fun cleanup() {
    stopSaving()
    scope.cancel()
  }
}
