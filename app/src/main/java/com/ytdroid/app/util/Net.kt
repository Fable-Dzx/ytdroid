package com.ytdroid.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object Net {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
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

    /** 流式下载到文件，返回是否成功。 */
    suspend fun downloadToFile(
        url: String,
        dest: File,
        onProgress: ((done: Long, total: Long) -> Unit)? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            dest.parentFile?.mkdirs()
            val tmp = File(dest.parentFile, dest.name + ".part")
            val req = Request.Builder().url(url).header("User-Agent", "ytDroid/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                val body = resp.body ?: return@withContext false
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
                true
            } else {
                tmp.delete()
                false
            }
        } catch (e: Exception) {
            tmpCleanup(dest)
            false
        }
    }

    private fun tmpCleanup(dest: File) {
        try {
            File(dest.parentFile, dest.name + ".part").delete()
        } catch (e: Exception) {
        }
    }
}
