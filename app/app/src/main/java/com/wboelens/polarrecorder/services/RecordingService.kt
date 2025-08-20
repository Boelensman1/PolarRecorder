package com.wboelens.polarrecorder.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RecordingService : Service() {
  companion object {
    private const val NOTIFICATION_ID = 1
    private const val CHANNEL_ID = "RecordingServiceChannel"
  }

  private val executor = Executors.newSingleThreadScheduledExecutor()
  private var recordingStartTime = System.currentTimeMillis()

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    recordingStartTime = System.currentTimeMillis()
    val notification = createNotification()
    startForeground(NOTIFICATION_ID, notification)

    // Schedule periodic notification updates
    scheduleNotificationUpdates()

    return START_STICKY
  }

  private fun scheduleNotificationUpdates() {
    executor.scheduleWithFixedDelay(
        {
          val notification = createNotification()
          val notificationManager =
              getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
          notificationManager.notify(NOTIFICATION_ID, notification)
        },
        1,
        1,
        TimeUnit.MINUTES,
    )
  }

  private fun createNotificationChannel() {
    // Create notification channel only on Android 8.0 (API 26) and higher
    // as NotificationChannel was introduced in this version
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      val channel =
          NotificationChannel(
              CHANNEL_ID, "Recording Service Channel", NotificationManager.IMPORTANCE_LOW,
          )
      val manager = getSystemService(NotificationManager::class.java)
      manager.createNotificationChannel(channel)
    }
  }

  private fun createNotification(): Notification {
    // Calculate recording duration
    val durationMs = System.currentTimeMillis() - recordingStartTime
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val durationText =
        if (minutes == 1L) {
          "1 minute"
        } else {
          "$minutes minutes"
        }

    // Create a PendingIntent to open your app when notification is tapped
    val pendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE,
        )

    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Recording in progress")
        .setContentText("Recording for $durationText")
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setOngoing(true)
        .setContentIntent(pendingIntent)
        .build()
  }

  override fun onDestroy() {
    executor.shutdownNow()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
