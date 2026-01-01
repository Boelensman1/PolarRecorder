package com.wboelens.polarrecorder.recording

import android.content.Context
import android.os.Build

/** Interface for app and device information, enabling testability. */
interface AppInfoProvider {
  val versionName: String
  val versionCode: Long
  val androidVersion: String
  val deviceInfo: String
}

/** Default implementation that uses the Android APIs. */
class AndroidAppInfoProvider(private val context: Context) : AppInfoProvider {
  override val versionName: String
    get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"

  override val versionCode: Long
    get() {
      val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
      return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
      } else {
        @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
      }
    }

  override val androidVersion: String
    get() = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

  override val deviceInfo: String
    get() = "${Build.MANUFACTURER} ${Build.MODEL}"
}
