package com.ytdroid.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.ytdroid.app.MainActivity
import com.ytdroid.app.engine.DownloadManager
import com.ytdroid.app.engine.DownloadTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** 前台服务：保活下载进程并持续刷新进度通知。 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotify = 0L

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ytdroid:download")
            .apply { acquire(8 * 60 * 60 * 1000L) }

        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(emptyList()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        scope.launch {
            DownloadManager.tasks.collect { tasks ->
                updateNotification(tasks)
                val active = tasks.any {
                    it.status == DownloadTask.Status.RUNNING || it.status == DownloadTask.Status.QUEUED
                }
                if (!active) stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "下载任务", NotificationManager.IMPORTANCE_LOW).apply {
            description = "显示 yt-dlp 下载进度"
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun updateNotification(tasks: List<DownloadTask>) {
        val now = System.currentTimeMillis()
        if (now - lastNotify < 300) return
        lastNotify = now
        val running = tasks.filter { it.status == DownloadTask.Status.RUNNING }
        val queued = tasks.count { it.status == DownloadTask.Status.QUEUED }
        NotificationManagerCompat.from(this).notify(NOTIF_ID, buildNotification(running, queued))
    }

    private fun buildNotification(running: List<DownloadTask>, queued: Int = 0): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("ytDroid 下载中")
            .setOngoing(true)
            .setContentIntent(contentIntent)

        val first = running.firstOrNull()
        if (first != null) {
            val pct = (first.progress * 100).toInt()
            val title = first.title.ifBlank { first.url }
            builder.setContentText("$title · $pct% · ${first.speed} · ETA ${first.eta}")
            builder.setProgress(100, pct, false)
            if (running.size > 1 || queued > 0) {
                val extra = buildList {
                    if (running.size > 1) add("${running.size - 1} 个并行")
                    if (queued > 0) add("$queued 个排队")
                }
                builder.setSubText(extra.joinToString("，"))
            }
        } else if (queued > 0) {
            builder.setContentText("等待中…（$queued 个排队）")
            builder.setProgress(0, 0, true)
        } else {
            builder.setContentText("准备中…")
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIF_ID = 1001
    }
}
