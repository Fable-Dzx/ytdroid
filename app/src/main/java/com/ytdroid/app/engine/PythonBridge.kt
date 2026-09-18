package com.ytdroid.app.engine

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File

/**
 * Chaquopy（内嵌 Python 3.13）桥接。
 * 所有 yt-dlp 调用都通过 runner.py 在 Python 进程内执行，
 * stdout/stderr 重定向到文件，由 Java 侧轮询解析进度。
 */
object PythonBridge {

    @Volatile
    private var started = false

    @Synchronized
    fun ensureStarted(context: Context) {
        if (started) return
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
        }
        started = true
    }

    private fun withPath(ytdlpDir: File, block: (Python) -> Unit) {
        val py = Python.getInstance()
        val sys = py.getModule("sys")
        sys.get("path").callAttr("insert", 0, ytdlpDir.absolutePath)
        block(py)
    }

    /** 读取当前生效的 yt-dlp 版本号。 */
    fun currentVersion(ytdlpDir: File): String? = try {
        withPath(ytdlpDir) { py ->
            py.getModule("yt_dlp.version").getAttr("__version__").toString()
        }
    } catch (e: Exception) {
        null
    }

    /** 阻塞式执行 runner（在调用线程上运行 Python，直到 yt-dlp 退出）。 */
    fun run(argfile: File, ytdlpDir: File) {
        withPath(ytdlpDir) { py ->
            py.getModule("runner").callAttr("run", argfile.absolutePath)
        }
    }

    /** 请求取消：向运行中的 yt-dlp 线程注入 KeyboardInterrupt。 */
    fun cancel(identfile: File, ytdlpDir: File) {
        try {
            withPath(ytdlpDir) { py ->
                py.getModule("runner").callAttr("cancel", identfile.absolutePath)
            }
        } catch (e: Exception) {
            // ctypes 不可用时静默失败：任务仍标记为取消，底层下载可能继续到自然结束
        }
    }
}
