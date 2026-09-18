package com.ytdroid.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.ytdroid.app.data.AppSettings
import java.io.File
import java.util.concurrent.TimeUnit

fun Context.toast(msg: String) {
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

fun defaultDownloadDir(context: Context): File {
    val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
    return File(base, "ytdroid")
}

/**
 * 解析最终下载目录：用户自定义目录需要「所有文件访问」权限；
 * 否则回退到应用私有下载目录（无需任何权限）。
 */
fun resolveDownloadDir(context: Context, settings: AppSettings): File {
    val custom = settings.downloadDir.trim()
    if (custom.isNotEmpty()) {
        val f = File(custom)
        val hasAllFiles = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
        if (f.isAbsolute && hasAllFiles) {
            f.mkdirs()
            return f
        }
    }
    val d = defaultDownloadDir(context)
    d.mkdirs()
    return d
}

/** 运行 ffmpeg -version 取首行版本信息；未安装返回 null。 */
fun ffmpegVersion(bin: File): String? {
    val ff = File(bin, "ffmpeg")
    if (!ff.exists()) return null
    return try {
        val p = ProcessBuilder(ff.absolutePath, "-version").redirectErrorStream(true).start()
        val first = p.inputStream.bufferedReader().use { it.readLine() }
        p.waitFor(5, TimeUnit.SECONDS)
        first?.substringBefore(" Copyright")
    } catch (e: Exception) {
        null
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.size - 1) {
        v /= 1024
        i++
    }
    return if (i == 0) "$bytes B" else String.format("%.2f %s", v, units[i])
}

fun formatDuration(seconds: Double?): String {
    if (seconds == null || seconds <= 0) return ""
    val total = seconds.toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

fun formatCount(n: Long?): String {
    if (n == null || n <= 0) return ""
    return when {
        n >= 100_000_000 -> String.format("%.1f亿", n / 100_000_000.0)
        n >= 10_000 -> String.format("%.1f万", n / 10_000.0)
        else -> n.toString()
    }
}

fun openFile(context: Context, file: File): Boolean {
    return try {
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeTypeOf(file.name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "打开文件"))
        true
    } catch (e: ActivityNotFoundException) {
        context.toast("没有可打开此文件的应用")
        false
    } catch (e: Exception) {
        context.toast("打开失败: ${e.message}")
        false
    }
}

fun mimeTypeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "mp4" -> "video/mp4"
    "mkv" -> "video/x-matroska"
    "webm" -> "video/webm"
    "mov" -> "video/quicktime"
    "mp3" -> "audio/mpeg"
    "m4a", "m4b" -> "audio/mp4"
    "opus" -> "audio/opus"
    "ogg" -> "audio/ogg"
    "flac" -> "audio/flac"
    "wav" -> "audio/wav"
    "aac" -> "audio/aac"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "srt" -> "application/x-subrip"
    "vtt" -> "text/vtt"
    "json" -> "application/json"
    "txt" -> "text/plain"
    else -> "application/octet-stream"
}

/** 比较 yt-dlp 形如 2026.08.19 的版本号，new 更新则返回 true。 */
fun isVersionNewer(new: String, old: String): Boolean {
    val n = parseVersion(new) ?: return false
    val o = parseVersion(old) ?: return true
    for (i in 0..2) {
        val c = n[i].compareTo(o[i])
        if (c != 0) return c > 0
    }
    return false
}

private fun parseVersion(v: String): List<Int>? {
    val s = v.trim().removePrefix("v").trim()
    val m = Regex("""(\d{4})\.(\d{2})\.(\d{2})""").find(s) ?: return null
    return m.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
}

/**
 * 简易 shell 风格分词：支持双引号 / 单引号分组，其余按空白切分。
 * 用于命令行运行器的自由命令输入。
 */
fun parseShellArgs(line: String): List<String> {
    val out = mutableListOf<String>()
    val cur = StringBuilder()
    var quote: Char? = null
    var has = false
    for (c in line) {
        when {
            quote != null -> {
                if (c == quote) quote = null else cur.append(c)
                has = true
            }
            c == '"' || c == '\'' -> {
                quote = c
                has = true
            }
            c.isWhitespace() -> {
                if (has) {
                    out += cur.toString()
                    cur.clear()
                    has = false
                }
            }
            else -> {
                cur.append(c)
                has = true
            }
        }
    }
    if (has) out += cur.toString()
    return out
}
