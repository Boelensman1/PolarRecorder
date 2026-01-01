package com.wboelens.polarrecorder.dataSavers

import android.content.Context
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.state.LogState
import com.wboelens.polarrecorder.testutil.BaseRobolectricTest
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.RuntimeEnvironment

/**
 * Unit tests for FileSystemDataSaver - verifies configuration, enable/disable lifecycle, directory
 * creation, and file operations.
 */
class FileSystemDataSaverTest : BaseRobolectricTest() {

  private lateinit var context: Context
  private lateinit var logState: LogState
  private lateinit var preferencesManager: PreferencesManager
  private lateinit var fileSystemDataSaver: FileSystemDataSaver

  @Before
  fun setup() {
    context = RuntimeEnvironment.getApplication()
    logState = mockk(relaxed = true)
    preferencesManager = mockk {
      every { fileSystemDataSaverConfig } returns FileSystemDataSaverConfig()
      every { fileSystemEnabled } returns false
      every { fileSystemDataSaverConfig = any() } just runs
      every { fileSystemEnabled = any() } just runs
    }
    fileSystemDataSaver = FileSystemDataSaver(context, logState, preferencesManager)
  }

  @After
  fun tearDown() {
    unmockkAll()
    fileSystemDataSaver.cleanup()
  }

  // ==================== Configuration Tests ====================

  @Test
  fun `isConfigured false when baseDirectory empty`() {
    fileSystemDataSaver.configure(FileSystemDataSaverConfig(baseDirectory = ""))

    assertFalse(fileSystemDataSaver.isConfigured)
  }

  @Test
  fun `isConfigured true when baseDirectory set`() {
    fileSystemDataSaver.configure(FileSystemDataSaverConfig(baseDirectory = "content://test/dir"))

    assertTrue(fileSystemDataSaver.isConfigured)
  }

  @Test
  fun `configure stores config in preferences`() {
    val config = FileSystemDataSaverConfig(baseDirectory = "content://test/dir", splitAtSizeMb = 50)

    fileSystemDataSaver.configure(config)

    verify { preferencesManager.fileSystemDataSaverConfig = config }
  }

  // ==================== Enable/Disable Tests ====================

  @Test
  fun `enable fails when baseDirectory empty`() {
    fileSystemDataSaver.configure(FileSystemDataSaverConfig(baseDirectory = ""))

    fileSystemDataSaver.enable()

    assertFalse(fileSystemDataSaver.isEnabled.value)
    verify { logState.addLogError(match { it.contains("must be configured") }) }
  }

  @Test
  fun `disable sets isEnabled false`() {
    fileSystemDataSaver.disable()

    assertFalse(fileSystemDataSaver.isEnabled.value)
  }

  @Test
  fun `disable persists disabled state`() {
    fileSystemDataSaver.disable()

    verify { preferencesManager.fileSystemEnabled = false }
  }

  // ==================== Init Saving Tests ====================

  @Test
  fun `initSaving fails when pickedDir is null`() {
    fileSystemDataSaver.configure(FileSystemDataSaverConfig(baseDirectory = "content://test/dir"))

    fileSystemDataSaver.initSaving("TestRecording", emptyMap())

    assertEquals(InitializationState.FAILED, fileSystemDataSaver.isInitialized.value)
    verify { logState.addLogError(match { it.contains("pickedDir is null") }) }
  }

  // ==================== Stop Saving Tests ====================

  @Test
  fun `stopSaving resets initialization state`() {
    fileSystemDataSaver.stopSaving()

    assertEquals(InitializationState.NOT_STARTED, fileSystemDataSaver.isInitialized.value)
  }
}
