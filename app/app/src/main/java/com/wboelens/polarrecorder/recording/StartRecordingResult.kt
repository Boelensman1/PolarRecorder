package com.wboelens.polarrecorder.recording

/** Result type for startRecording operations, enabling type-safe error handling. */
sealed class StartRecordingResult {
  data object Success : StartRecordingResult()

  data class EmptyRecordingName(val message: String) : StartRecordingResult()

  data class AlreadyRecording(val message: String) : StartRecordingResult()

  data class NoDevicesSelected(val message: String) : StartRecordingResult()

  data class DevicesNotConnected(val disconnectedNames: String) : StartRecordingResult()

  data class NoDataSaversEnabled(val message: String) : StartRecordingResult()

  data class DataSaversNotInitialized(val message: String) : StartRecordingResult()
}
