package com.ytdroid.app.engine

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.ytdroid.app.data.SettingsRepository
import com.ytdroid.app.service.DownloadService
import com.ytdroid.app.util.Logs
import com.ytdroid.app.util.mimeTypeOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * 下载队列管理器：限流并行、进度流转、取消/重试/删除、完成落库。
 */
object DownloadManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    val activeCount: StateFlow<Int> = _tasks
        .map { list ->
            list.count { it.status == DownloadTask.Status.RUNNING || it.status == DownloadTask.Status.QUEUED }
        }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    private val queue = Channel<Long>(Channel.UNLIMITED)
    private val idGen = AtomicLong(0)
    private var workers = 0

    fun enqueue(context: Context, url: String, reuseId: Long? = null) {
        val appCtx = context.applicationContext
        val id = reuseId ?: idGen.incrementAndGet()
        if (reuseId == null) {
            _tasks.update { it + DownloadTask(id = id, url = url) }
        }
        try {
            ContextCompat.startForegroundService(appCtx, Intent(appCtx, DownloadService::class.java))
        } catch (e: Exception) {
            // 后台启动前台服务受限时忽略：下载仍会在应用进程内继续
        }
        scope.launch {
            val maxParallel = SettingsRepository.current(appCtx).maxParallel.coerceIn(1, 4)
            queue.send(id)
            ensureWorker(appCtx, maxParallel)
        }
    }

    @Synchronized
    private fun ensureWorker(context: Context, maxParallel: Int) {
        if (workers >= maxParallel) return
        workers++
        scope.launch {
            try {
                while (true) {
                    val id = queue.receive()
                    runOne(context, id)
                }
            } finally {
                workers--
            }
        }
    }

    private suspend fun runOne(context: Context, id: Long) {
        val appCtx = context.applicationContext
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        val settings = SettingsRepository.current(appCtx)

        mark(task.copy(status = DownloadTask.Status.RUNNING, startTime = System.currentTimeMillis()))
        Logs.append(appCtx, "download", "开始下载 #${task.id}: ${task.url.take(200)}")

        val ready = YtDlpEngine.ensureReady(appCtx, settings)
        if (!ready) {
            Logs.append(appCtx, "download", "#${task.id} 失败：引擎未就绪")
            mark(task.copy(status = DownloadTask.Status.FAILED, error = "引擎未就绪，请查看「环境」页"))
            return
        }

        val rc = try {
            YtDlpEngine.runTask(appCtx, task, settings) { u -> mark(u) }
        } catch (e: Exception) {
            Logs.append(appCtx, "download", "#${task.id} 异常: ${e.message}")
            mark(task.copy(status = DownloadTask.Status.FAILED, error = e.message ?: "运行失败"))
            return
        }

        val latest = _tasks.value.firstOrNull { it.id == id } ?: task
        val cancelled = latest.cancelled
        val status = when {
            cancelled -> DownloadTask.Status.CANCELLED
            rc == 0 -> DownloadTask.Status.DONE
            else -> DownloadTask.Status.FAILED
        }
        val final = latest.copy(
            status = status,
            finishTime = System.currentTimeMillis(),
            progress = if (status == DownloadTask.Status.DONE) 1f else latest.progress,
            error = if (status == DownloadTask.Status.FAILED) latest.error.ifBlank { "退出码 $rc" } else latest.error,
        )
        mark(final)
        Logs.append(
            appCtx, "download",
            "#${task.id} ${when (status) {
                DownloadTask.Status.DONE -> "完成"
                DownloadTask.Status.CANCELLED -> "已取消"
                else -> "失败（退出码 $rc）: ${final.error}"
            }}"
        )

        if (status == DownloadTask.Status.DONE && settings.saveToMediaStore) {
            saveOutputsToMediaStore(appCtx, final.outputs)
        }
    }

    /** 合并任务更新：保留列表中的 cancelled 标记。 */
    private fun mark(updated: DownloadTask) {
        _tasks.update { list ->
            list.map { if (it.id == updated.id) updated.copy(cancelled = it.cancelled) else it }
        }
    }

    fun cancel(context: Context, id: Long) {
        val appCtx = context.applicationContext
        _tasks.update { list ->
            list.map { if (it.id == id) it.copy(cancelled = true) else it }
        }
        val ident = File(YtDlpEngine.runDir(appCtx), "task-$id.ident")
        PythonBridge.cancel(ident, YtDlpEngine.ytdlpDir(appCtx))
    }

    fun retry(context: Context, id: Long) {
        val appCtx = context.applicationContext
        val t = _tasks.value.firstOrNull { it.id == id } ?: return
        if (t.status == DownloadTask.Status.RUNNING || t.status == DownloadTask.Status.QUEUED) return
        val fresh = t.copy(
            status = DownloadTask.Status.QUEUED,
            progress = 0f, speed = "", eta = "", stage = "", fileName = "",
            outputs = emptyList(), error = "", cancelled = false,
            startTime = 0L, finishTime = 0L,
        )
        _tasks.update { list -> list.map { if (it.id == id) fresh else it } }
        enqueue(appCtx, t.url, reuseId = id)
    }

    fun delete(id: Long) {
        val t = _tasks.value.firstOrNull { it.id == id } ?: return
        if (t.status == DownloadTask.Status.RUNNING || t.status == DownloadTask.Status.QUEUED) return
        _tasks.update { list -> list.filterNot { it.id == id } }
    }

    fun clearFinished() {
        _tasks.update { list ->
            list.filterNot {
                it.status == DownloadTask.Status.DONE ||
                    it.status == DownloadTask.Status.FAILED ||
                    it.status == DownloadTask.Status.CANCELLED
            }
        }
    }

    private fun saveOutputsToMediaStore(context: Context, outputs: List<String>) {
        if (Build.VERSION.SDK_INT < 29) return
        try {
            val resolver = context.contentResolver
            for (path in outputs) {
                val f = File(path)
                if (!f.exists() || f.length() == 0L) continue
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, f.name)
                    put(MediaStore.Downloads.MIME_TYPE, mimeTypeOf(f.name))
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ytDroid")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: continue
                try {
                    resolver.openOutputStream(uri)?.use { os -> f.inputStream().use { it.copyTo(os) } }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } catch (e: Exception) {
                    runCatching { resolver.delete(uri, null, null) }
                }
            }
        } catch (e: Exception) {
            // 静默失败：复制到公共目录属于可选增强
        }
    }
}
