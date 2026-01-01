package com.wboelens.polarrecorder.viewModels

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import app.cash.turbine.test
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for FileSystemSettingsViewModel - verifies directory intent creation, permission
 * handling, and StateFlow updates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class FileSystemSettingsViewModelTest {

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
  fun `createDirectoryIntent includes read permission flag`() {
    val intent = viewModel.createDirectoryIntent()

    assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
  }

  @Test
  fun `createDirectoryIntent includes write permission flag`() {
    val intent = viewModel.createDirectoryIntent()

    assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
  }

  @Test
  fun `createDirectoryIntent includes persistable permission flag`() {
    val intent = viewModel.createDirectoryIntent()

    assertTrue(intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0)
  }

  @Test
  fun `createDirectoryIntent includes prefix permission flag`() {
    val intent = viewModel.createDirectoryIntent()

    assertTrue(intent.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION != 0)
  }

  @Test
  fun `createDirectoryIntent sets initial URI extra`() {
    val intent = viewModel.createDirectoryIntent()

    assertTrue(intent.hasExtra(DocumentsContract.EXTRA_INITIAL_URI))
  }

  @Test
  fun `handleDirectoryResult updates selectedDirectory`() = runTest {
    val testUri = mockk<Uri>()
    every { testUri.toString() } returns "content://test/directory"
    val contentResolver = mockk<ContentResolver>()
    every {
      contentResolver.takePersistableUriPermission(
          testUri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
      )
    } just runs
    val context = mockk<Context>()
    every { context.contentResolver } returns contentResolver

    viewModel.handleDirectoryResult(context, testUri)

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
    val testUri = mockk<Uri>()
    every { testUri.toString() } returns "content://test/directory"
    val contentResolver = mockk<ContentResolver>()
    every {
      contentResolver.takePersistableUriPermission(
          testUri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
      )
    } just runs
    val context = mockk<Context>()
    every { context.contentResolver } returns contentResolver

    viewModel.handleDirectoryResult(context, testUri)

    verify {
      contentResolver.takePersistableUriPermission(
          testUri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
      )
    }
  }

  @Test
  fun `selectedDirectory StateFlow emits updates`() = runTest {
    val testUri = mockk<Uri>()
    every { testUri.toString() } returns "content://new/directory"
    val contentResolver = mockk<ContentResolver>()
    every {
      contentResolver.takePersistableUriPermission(
          testUri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
      )
    } just runs
    val context = mockk<Context>()
    every { context.contentResolver } returns contentResolver

    viewModel.selectedDirectory.test {
      assertEquals("", awaitItem())

      viewModel.handleDirectoryResult(context, testUri)

      assertEquals("content://new/directory", awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
  }
}
