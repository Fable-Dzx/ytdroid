package com.ytdroid.app.engine

import java.util.Locale

/** 解析 yt-dlp 输出行，产出任务状态增量。 */
object ProgressParser {

    private val stageRe = Regex("""^\[([^\]]+)\]""")
    private val destRe = Regex("""\[download\]\s+Destination:\s+(.+)""")
    private val postDestRe = Regex("""^\[(ExtractAudio|Merger|VideoConvertor|Metadata|EmbedThumbnail|Subtitles|FixupM3u8|ThumbnailsConvertor)\]\s+(?:Destination:\s+)?["']?([^"']+)["']?\s*$""")
    private val progressRe = Regex(
        """\[download\]\s+(\d+(?:\.\d+)?)%(?: of ~?([\d.]+)\s*([KMG]iB))?(?: at ([\d.]+)\s*([KMG]iB)/s)?(?: ETA (\d{2}:\d{2}))?"""
    )
    private val errorRe = Regex("""^ERROR:\s*(.+)$""")
    private val itemRe = Regex("""\[download\]\s+Downloading item (\d+) of (\d+)""")
    private val finishRe = Regex("""\[download\]\s+100%""")

    fun apply(task: DownloadTask, line: String): DownloadTask {
        var t = task
        val l = line.trim()
        if (l.isEmpty()) return t

        errorRe.find(l)?.let { m ->
            return t.copy(status = DownloadTask.Status.FAILED, error = m.groupValues[1])
        }

        stageRe.find(l)?.let { m ->
            t = t.copy(stage = m.groupValues[1])
        }

        destRe.find(l)?.let { m ->
            val p = m.groupValues[1].trim()
            t = t.copy(fileName = p.substringAfterLast('/'), outputs = t.outputs + p)
        }
        postDestRe.find(l)?.let { m ->
            val p = m.groupValues[2].trim()
            t = t.copy(outputs = t.outputs + p)
        }

        progressRe.find(l)?.let { m ->
            val pct = m.groupValues[1].toFloatOrNull() ?: 0f
            val speed = if (m.groupValues[4].isNotEmpty()) {
                String.format(Locale.US, "%.2f %s/s", m.groupValues[4].toFloatOrNull() ?: 0f, m.groupValues[5])
            } else ""
            t = t.copy(progress = (pct / 100f).coerceIn(0f, 1f), speed = speed, eta = m.groupValues[6])
        }

        finishRe.find(l)?.let { t = t.copy(progress = 1f) }

        itemRe.find(l)?.let { m ->
            t = t.copy(stage = "下载中 ${m.groupValues[1]}/${m.groupValues[2]}")
        }
        return t
    }
}
