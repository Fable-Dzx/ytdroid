package com.ytdroid.app.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用运行日志：记录引擎初始化、组件更新、下载任务、命令行执行等关键事件，
 * 供「环境 → 日志」查看 / 导出 / 清空，便于排查问题。
 */
object Logs {

    private val lock = Any()
    private const val MAX_BYTES = 512L * 1024
    private const val KEEP_LINES = 1000

    fun file(context: Context): File = File(context.applicationContext.filesDir, "logs/app.log")

    fun append(context: Context, tag: String, message: String) {
        try {
            synchronized(lock) {
                val f = file(context)
                f.parentFile?.mkdirs()
                f.appendText("[${ts()}] [$tag] $message\n")
                trimIfNeeded(f)
            }
        } catch (_: Exception) {
        }
    }

    fun read(context: Context, maxLines: Int = 1200): List<String> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return try {
            synchronized(lock) { f.readLines().takeLast(maxLines) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context) {
        try {
            synchronized(lock) { file(context).delete() }
        } catch (_: Exception) {
        }
    }

    private fun ts(): String =
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())

    private fun trimIfNeeded(f: File) {
        if (f.length() <= MAX_BYTES) return
        val lines = f.readLines()
        if (lines.size <= KEEP_LINES) return
        f.writeText(lines.takeLast(KEEP_LINES).joinToString("\n") + "\n")
    }
}
