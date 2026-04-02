package com.wboelens.polarrecorder.recording

data class EventLogEntry(
    val index: Int,
    val timestamp: Long,
    val label: String,
)
