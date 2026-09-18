package com.ytdroid.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

object Zips {

    /** 解压 zip 到 destDir，保留相对路径。返回解出的文件数。 */
    suspend fun extractZip(zipFile: File, destDir: File): Int = withContext(Dispatchers.IO) {
        destDir.mkdirs()
        var count = 0
        ZipInputStream(FileInputStream(zipFile).buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val out = File(destDir, entry.name)
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zis.copyTo(it) }
                    count++
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        count
    }

    /**
     * 解压 tar.gz，只提取路径段中包含 [prefix] 的文件到 destDir（去掉前缀段）。
     * 例如 yt-dlp 官方 tar.gz 内为 yt-dlp/yt_dlp/...，prefix=yt_dlp 时得到 destDir/yt_dlp/...
     */
    suspend fun extractTarGzPrefix(tgzFile: File, destDir: File, prefix: String): Int =
        withContext(Dispatchers.IO) {
            destDir.mkdirs()
            var count = 0
            TarArchiveInputStream(GzipCompressorInputStream(FileInputStream(tgzFile).buffered())).use { tis ->
                var entry = tis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory) {
                        val segs = name.split('/')
                        val idx = segs.indexOf(prefix)
                        if (idx >= 0) {
                            val rel = segs.drop(idx).joinToString("/")
                            val out = File(destDir, rel)
                            out.parentFile?.mkdirs()
                            out.outputStream().use { tis.copyTo(it) }
                            count++
                        }
                    }
                    entry = tis.nextEntry
                }
            }
            count
        }
}
