package com.wboelens.polarrecorder.testutil

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.polar.sdk.api.model.PolarDeviceInfo
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs

/**
 * Shared mock factory methods for test utilities. Provides reusable mock creation for common test
 * objects like PolarDeviceInfo and Android Context.
 */
object MockFactories {

  /**
   * Creates a mock PolarDeviceInfo with configurable properties.
   *
   * @param deviceId The device identifier
   * @param name The device name (default: "Test Device")
   * @param isConnectable Whether the device is connectable (default: true)
   * @return A mocked PolarDeviceInfo instance
   */
  fun createMockDevice(
      deviceId: String,
      name: String = "Test Device",
      isConnectable: Boolean = true,
  ): PolarDeviceInfo = mockk {
    every { this@mockk.deviceId } returns deviceId
    every { this@mockk.name } returns name
    every { this@mockk.isConnectable } returns isConnectable
  }

  /**
   * Creates a mock Context with ContentResolver configured for URI permission handling. Uses real
   * Uri.parse() for Robolectric compatibility.
   *
   * @param uriString The URI string to use
   * @return A Triple of (Context, ContentResolver, Uri) for testing
   */
  fun createMockContextWithUri(uriString: String): Triple<Context, ContentResolver, Uri> {
    val uri = Uri.parse(uriString)

    val contentResolver =
        mockk<ContentResolver> {
          every {
            takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
          } just runs
        }

    val context =
        mockk<Context> {
          every { this@mockk.contentResolver } returns contentResolver
          every { applicationContext } returns this
        }

    return Triple(context, contentResolver, uri)
  }
}
