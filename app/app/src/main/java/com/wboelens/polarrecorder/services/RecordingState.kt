package com.wboelens.polarrecorder.services

data class RecordingState(
    val isRecording: Boolean = false,
    val currentRecordingName: String = "",
    val recordingStartTime: Long = 0L,
)
