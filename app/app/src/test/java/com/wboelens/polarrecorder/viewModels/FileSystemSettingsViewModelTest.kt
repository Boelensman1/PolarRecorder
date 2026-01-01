package com.wboelens.polarrecorder.viewModels

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import app.cash.turbine.test
import com.wboelens.polarrecorder.testutil.BaseRobolectricTest
import com.wboelens.polarrecorder.testutil.MockFactories
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for FileSystemSettingsViewModel - verifies directory intent creation, permission
 * handling, and StateFlow updates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FileSystemSettingsViewModelTest : BaseRobolectricTest() {

  private lateinit var viewModel: FileSystemSettingsViewModel

  @Before
  fun setup() {
    viewModel = FileSystemSettingsViewModel()
  }

  @Test
  fun `selectedDirectory initial value is empty`() {
    assertEquals("", viewModel.selectedDirectory.value)
  }

  @Test
  fun `createDirectoryIntent returns ACTION_OPEN_DOCUMENT_TREE intent`() {
    val intent = viewModel.createDirectoryIntent()

    assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, intent.action)
  }

  @Test
  fun `createDirectoryIntent includes required permission flags`() {
    val intent = viewModel.createDirectoryIntent()

    assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
    assertTrue(intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0)
    assertTrue(intent.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION != 0)
  }

  @Test
  fun `createDirectoryIntent sets initial URI extra`() {
    val intent = viewModel.createDirectoryIntent()

    assertTrue(intent.hasExtra(DocumentsContract.EXTRA_INITIAL_URI))
  }

  @Test
  fun `handleDirectoryResult updates selectedDirectory`() = runTest {
    val (context, _, uri) = MockFactories.createMockContextWithUri("content://test/directory")

    viewModel.handleDirectoryResult(context, uri)

    assertEquals("content://test/directory", viewModel.selectedDirectory.value)
  }

  @Test
  fun `handleDirectoryResult with null uri does nothing`() = runTest {
    val context = mockk<Context>()

    viewModel.handleDirectoryResult(context, null)

    assertEquals("", viewModel.selectedDirectory.value)
  }

  @Test
  fun `handleDirectoryResult takes persistable permission`() {
    val (context, contentResolver, uri) =
        MockFactories.createMockContextWithUri("content://test/directory")

    viewModel.handleDirectoryResult(context, uri)

    verify {
      contentResolver.takePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
      )
    }
  }

  @Test
  fun `selectedDirectory StateFlow emits updates`() = runTest {
    val (context, _, uri) = MockFactories.createMockContextWithUri("content://new/directory")

    viewModel.selectedDirectory.test {
      assertEquals("", awaitItem())

      viewModel.handleDirectoryResult(context, uri)

      assertEquals("content://new/directory", awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
  }
}
