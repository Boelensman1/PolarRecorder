package com.wboelens.polarrecorder.recording

/** Interface for time operations, enabling testability. */
interface Clock {
  fun currentTimeMillis(): Long
}

/** Default implementation that uses the system clock. */
class SystemClock : Clock {
  override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
