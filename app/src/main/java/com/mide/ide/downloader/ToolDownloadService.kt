package com.mide.ide.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ToolDownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val notifId = 1001
    private val channelId = "mide_tools"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(notifId, NotificationCompat.Builder(this, channelId)
            .setContentTitle("MIDE")
            .setContentText("Downloading build tools...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build())

        scope.launch {
            ToolDownloadManager(this@ToolDownloadService).checkAndDownloadRequiredTools { status ->
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(notifId, NotificationCompat.Builder(this@ToolDownloadService, channelId)
                    .setContentTitle("Downloading ${"$"}{status.toolName}")
                    .setContentText("${"$"}{status.progress}%")
                    .setProgress(100, status.progress, false)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setOngoing(status.status == ToolDownloadManager.Status.DOWNLOADING)
                    .build())
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channelId, "Tool Downloads", NotificationManager.IMPORTANCE_LOW)
        )
    }
}
