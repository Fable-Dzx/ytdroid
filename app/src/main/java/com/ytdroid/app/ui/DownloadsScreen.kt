package com.ytdroid.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytdroid.app.engine.DownloadManager
import com.ytdroid.app.engine.DownloadTask
import com.ytdroid.app.util.openFile
import com.ytdroid.app.util.toast
import java.io.File

@Composable
fun DownloadsScreen() {
    val context = LocalContext.current
    val tasks by DownloadManager.tasks.collectAsStateWithLifecycle()
    val hasFinished = tasks.any {
        it.status == DownloadTask.Status.DONE ||
            it.status == DownloadTask.Status.FAILED ||
            it.status == DownloadTask.Status.CANCELLED
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "下载任务",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { DownloadManager.clearFinished() }, enabled = hasFinished) {
                Text("清空已完成")
            }
        }
        if (tasks.isEmpty()) {
            EmptyState(icon = Icons.Default.List, text = "暂无任务\n输入链接开始下载")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskCard(task, context)
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: DownloadTask, context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        task.title.ifBlank { task.url },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (task.title.isNotEmpty()) {
                        Text(
                            task.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.padding(start = 8.dp))
                StatusBadge(
                    label = when (task.status) {
                        DownloadTask.Status.RUNNING -> "下载中"
                        DownloadTask.Status.QUEUED -> "排队中"
                        DownloadTask.Status.DONE -> "完成"
                        DownloadTask.Status.FAILED -> "失败"
                        DownloadTask.Status.CANCELLED -> "已取消"
                    },
                    color = when (task.status) {
                        DownloadTask.Status.RUNNING -> MaterialTheme.colorScheme.primary
                        DownloadTask.Status.DONE -> Color(0xFF43A047)
                        DownloadTask.Status.FAILED -> Color(0xFFE53935)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            when (task.status) {
                DownloadTask.Status.RUNNING -> {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    val line = buildList {
                        task.stage.takeIf { it.isNotEmpty() }?.let { add(it) }
                        if (task.speed.isNotEmpty()) add(task.speed)
                        if (task.eta.isNotEmpty()) add("ETA ${task.eta}")
                    }
                    if (line.isNotEmpty()) {
                        Text(
                            line.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (task.fileName.isNotEmpty()) {
                        Text(
                            task.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                DownloadTask.Status.QUEUED -> {
                    Spacer(Modifier.height(6.dp))
                    Text("排队等待中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DownloadTask.Status.FAILED -> {
                    if (task.error.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            task.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE53935),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                DownloadTask.Status.DONE -> {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "已完成 · ${task.outputs.size} 个文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DownloadTask.Status.CANCELLED -> {
                    Spacer(Modifier.height(6.dp))
                    Text("已取消", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(Modifier.align(Alignment.End)) {
                if (task.status == DownloadTask.Status.RUNNING || task.status == DownloadTask.Status.QUEUED) {
                    IconButton(onClick = { DownloadManager.cancel(context, task.id) }) {
                        Icon(Icons.Default.Close, contentDescription = "取消", tint = MaterialTheme.colorScheme.error)
                    }
                } else {
                    if (task.status == DownloadTask.Status.FAILED || task.status == DownloadTask.Status.CANCELLED) {
                        IconButton(onClick = { DownloadManager.retry(context, task.id) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "重试")
                        }
                    }
                    if (task.status == DownloadTask.Status.DONE && task.outputs.isNotEmpty()) {
                        IconButton(onClick = {
                            val f = File(task.outputs.first())
                            if (f.exists()) openFile(context, f) else context.toast("文件不存在")
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "打开")
                        }
                    }
                    IconButton(onClick = { DownloadManager.delete(task.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                }
            }
        }
    }
}
