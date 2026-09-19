package com.ytdroid.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

object Net {

    /** GitHub 加速镜像（中国大陆可直连），按优先级依次回退。 */
    private val MIRRORS = listOf(
        "https://ghfast.top/",
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
        "https://mirror.ghproxy.com/",
    )

    /** 生成候选下载地址：原地址优先，失败自动换镜像。 */
    fun candidateUrls(url: String): List<String> {
        val base = url.trim()
        if (base.isEmpty()) return emptyList()
        if (!base.startsWith("https://github.com/")) return listOf(base)
        val list = mutableListOf(base)
        for (m in MIRRORS) {
            val u = m + base
            if (!list.contains(u)) list.add(u)
        }
        return list
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun getText(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).header("User-Agent", "ytDroid/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 流式下载到文件。
     * @return null 表示成功；否则返回可展示的错误描述。
     */
    suspend fun downloadToFile(
        url: String,
        dest: File,
        onProgress: ((done: Long, total: Long) -> Unit)? = null,
    ): String? = withContext(Dispatchers.IO) {
        try {
            dest.parentFile?.mkdirs()
            val tmp = File(dest.parentFile, dest.name + ".part")
            val req = Request.Builder().url(url).header("User-Agent", "ytDroid/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext "HTTP ${resp.code}（${resp.message}）"
                }
                val body = resp.body ?: return@withContext "响应为空"
                val total = body.contentLength()
                val input = body.byteStream()
                val output = tmp.outputStream()
                val buf = ByteArray(64 * 1024)
                var read: Int
                var done = 0L
                try {
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        done += read
                        onProgress?.invoke(done, total)
                    }
                } finally {
                    output.close()
                    input.close()
                }
            }
            if (tmp.exists() && tmp.length() > 0) {
                if (dest.exists()) dest.delete()
                tmp.renameTo(dest)
                null
            } else {
                tmp.delete()
                "下载内容为空"
            }
        } catch (e: SocketTimeoutException) {
            tmpCleanup(dest)
            "连接超时（网络慢或源不可达）"
        } catch (e: UnknownHostException) {
            tmpCleanup(dest)
            "无法解析域名，请检查网络连接"
        } catch (e: ConnectException) {
            tmpCleanup(dest)
            "连接被拒绝，源可能不可用"
        } catch (e: Exception) {
            tmpCleanup(dest)
            e.message ?: "未知网络错误"
        }
    }

    private fun tmpCleanup(dest: File) {
        try {
            File(dest.parentFile, dest.name + ".part").delete()
        } catch (e: Exception) {
        }
    }
}
