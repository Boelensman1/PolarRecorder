package com.wboelens.polarrecorder.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for RecordingState data class - verifies default values and data class behavior. */
class RecordingStateTest {

  @Test
  fun `default values are correct`() {
    val state = RecordingState()

    assertFalse(state.isRecording)
    assertEquals("", state.currentRecordingName)
    assertEquals(0L, state.recordingStartTime)
  }

  @Test
  fun `data class copy works correctly`() {
    val original =
        RecordingState(
            isRecording = true, currentRecordingName = "Test", recordingStartTime = 12345L)

    val copy = original.copy(isRecording = false)

    assertFalse(copy.isRecording)
    assertEquals("Test", copy.currentRecordingName)
    assertEquals(12345L, copy.recordingStartTime)
  }

  @Test
  fun `data class equals works correctly`() {
    val state1 =
        RecordingState(
            isRecording = true, currentRecordingName = "Test", recordingStartTime = 12345L)
    val state2 =
        RecordingState(
            isRecording = true, currentRecordingName = "Test", recordingStartTime = 12345L)
    val state3 =
        RecordingState(
            isRecording = false, currentRecordingName = "Test", recordingStartTime = 12345L)

    assertEquals(state1, state2)
    assertNotEquals(state1, state3)
  }

  @Test
  fun `data class hashCode works correctly`() {
    val state1 =
        RecordingState(
            isRecording = true, currentRecordingName = "Test", recordingStartTime = 12345L)
    val state2 =
        RecordingState(
            isRecording = true, currentRecordingName = "Test", recordingStartTime = 12345L)

    assertEquals(state1.hashCode(), state2.hashCode())
  }

  @Test
  fun `can create state with all parameters`() {
    val state =
        RecordingState(
            isRecording = true,
            currentRecordingName = "MyRecording",
            recordingStartTime = 9876543210L)

    assertTrue(state.isRecording)
    assertEquals("MyRecording", state.currentRecordingName)
    assertEquals(9876543210L, state.recordingStartTime)
  }

  @Test
  fun `copy with single field change preserves other fields`() {
    val original =
        RecordingState(
            isRecording = true, currentRecordingName = "Original", recordingStartTime = 11111L)

    val copiedName = original.copy(currentRecordingName = "Changed")
    assertEquals("Changed", copiedName.currentRecordingName)
    assertTrue(copiedName.isRecording)
    assertEquals(11111L, copiedName.recordingStartTime)

    val copiedTime = original.copy(recordingStartTime = 22222L)
    assertEquals("Original", copiedTime.currentRecordingName)
    assertTrue(copiedTime.isRecording)
    assertEquals(22222L, copiedTime.recordingStartTime)
  }
}
