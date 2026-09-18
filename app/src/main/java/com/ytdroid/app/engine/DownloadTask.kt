package com.ytdroid.app.engine

/** 单个下载任务的状态快照。 */
data class DownloadTask(
    val id: Long,
    val url: String,
    val title: String = "",
    val status: Status = Status.QUEUED,
    val progress: Float = 0f,          // 0..1
    val speed: String = "",
    val eta: String = "",
    val stage: String = "",
    val fileName: String = "",
    val outputs: List<String> = emptyList(),
    val error: String = "",
    val cancelled: Boolean = false,
    val startTime: Long = 0L,
    val finishTime: Long = 0L,
) {
    enum class Status { QUEUED, RUNNING, DONE, FAILED, CANCELLED }
}
