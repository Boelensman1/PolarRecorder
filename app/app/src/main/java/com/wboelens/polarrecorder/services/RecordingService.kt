package com.wboelens.polarrecorder.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class RecordingService : Service() {
  companion object {
    private const val NOTIFICATION_ID = 1
    private const val CHANNEL_ID = "RecordingServiceChannel"
  }

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val notification = createNotification()
    startForeground(NOTIFICATION_ID, notification)
    return START_STICKY
  }

  private fun createNotificationChannel() {
    val channel =
        NotificationChannel(
            CHANNEL_ID, "Recording Service Channel", NotificationManager.IMPORTANCE_LOW)
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(channel)
  }

  private fun createNotification(): Notification {
    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Recording in Progress")
        .setContentText("Recording data from Polar devices")
        .setSmallIcon(android.R.drawable.ic_media_play)
        .build()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
