package com.ytdroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ytdroid.app.R
import com.ytdroid.app.data.AppSettings
import com.ytdroid.app.data.SettingsRepository
import com.ytdroid.app.engine.DownloadManager
import com.ytdroid.app.engine.YtDlpEngine
import com.ytdroid.app.util.formatCount
import com.ytdroid.app.util.formatDuration
import com.ytdroid.app.util.toast
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by SettingsRepository.settings(context).collectAsStateWithLifecycle(initialValue = AppSettings())
    val engineState by YtDlpEngine.state.collectAsStateWithLifecycle()

    var url by rememberSaveable { mutableStateOf("") }
    var info by remember { mutableStateOf<YtDlpEngine.VideoInfo?>(null) }
    var fetching by remember { mutableStateOf(false) }

    val canDownload = url.trim().isNotEmpty() && engineState.ready

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { EngineStatusCard(engineState) }

        item {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; info = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("视频 / 播放列表链接") },
                placeholder = { Text("https://…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                trailingIcon = {
                    if (url.isNotEmpty()) {
                        IconButton(onClick = { url = ""; info = null }) {
                            Icon(Icons.Default.Close, contentDescription = "清空")
                        }
                    }
                },
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        val u = url.trim()
                        if (u.isEmpty()) {
                            context.toast("请先输入链接")
                            return@OutlinedButton
                        }
                        scope.launch {
                            fetching = true
                            info = YtDlpEngine.fetchInfo(context, settings, u)
                            if (info == null) context.toast("解析失败：检查链接或引擎状态")
                            fetching = false
                        }
                    },
                    enabled = url.trim().isNotEmpty() && !fetching && engineState.ready,
                    modifier = Modifier.weight(1f),
                ) {
                    if (fetching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("解析")
                }
                Button(
                    onClick = {
                        val u = url.trim()
                        if (u.isEmpty()) {
                            context.toast("请先输入链接")
                            return@Button
                        }
                        DownloadManager.enqueue(context, u)
                        context.toast("已加入下载队列")
                        url = ""
                        info = null
                    },
                    enabled = canDownload,
                    modifier = Modifier.weight(1.4f),
                ) {
                    Icon(painterResource(R.drawable.ic_download), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("下载")
                }
            }
        }

        info?.let { v -> item { InfoCard(v) } }

        item {
            CardBox {
                Text(
                    "格式",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppSettings.FORMAT_PRESETS.forEach { preset ->
                        val selected = settings.formatString == preset.format
                        FilterChip(
                            selected = selected,
                            onClick = {
                                scope.launch {
                                    SettingsRepository.save(context) {
                                        it.copy(formatString = preset.format, audioOnly = preset.label == "仅音频")
                                    }
                                }
                            },
                            label = { Text(preset.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = settings.formatString,
                    onValueChange = { v ->
                        scope.launch { SettingsRepository.save(context) { it.copy(formatString = v) } }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    label = { Text("自定义格式串 (-f)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item {
            CardBox {
                SwitchRow(
                    title = "仅音频",
                    subtitle = "提取音频（未安装 ffmpeg 时自动改为下载原生音频）",
                    checked = settings.audioOnly,
                    onCheckedChange = { v -> scope.launch { SettingsRepository.save(context) { it.copy(audioOnly = v) } } },
                )
                SwitchRow(
                    title = "播放列表",
                    subtitle = "允许下载整个播放列表",
                    checked = settings.allowPlaylist,
                    onCheckedChange = { v -> scope.launch { SettingsRepository.save(context) { it.copy(allowPlaylist = v) } } },
                )
                OutlinedTextField(
                    value = settings.outputTemplate,
                    onValueChange = { v ->
                        scope.launch { SettingsRepository.save(context) { it.copy(outputTemplate = v) } }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    label = { Text("输出文件名模板 (-o)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                if (engineState.ffmpegVersion == null) {
                    Text(
                        "提示：未安装 ffmpeg。合并音视频 / 提取音频 / 嵌入字幕需要它，可在「引擎」页配置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EngineStatusCard(state: YtDlpEngine.EngineState) {
    CardBox {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            EngineDot(ready = state.ready, updating = state.updating)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        state.updating -> "引擎更新中…"
                        state.ready -> "引擎就绪"
                        else -> "引擎初始化…"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                val ver = buildList {
                    state.ytDlpVersion?.let { add("yt-dlp $it") }
                    state.ffmpegVersion?.let { add("ffmpeg $it") }
                    if (state.ffmpegVersion == null) add("ffmpeg 未安装")
                }
                Text(
                    ver.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.updating || !state.ready) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        state.message?.let { msg ->
            Spacer(Modifier.height(6.dp))
            Text(
                msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun InfoCard(info: YtDlpEngine.VideoInfo) {
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            if (info.thumbnail != null) {
                AsyncImage(
                    model = info.thumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(Modifier.padding(14.dp)) {
                Text(
                    info.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                val meta = buildList {
                    info.uploader.takeIf { it.isNotBlank() }?.let { add(it) }
                    formatDuration(info.duration).takeIf { it.isNotEmpty() }?.let { add(it) }
                    if (info.width != null && info.height != null) add("${info.width}×${info.height}")
                    formatCount(info.viewCount).takeIf { it.isNotEmpty() }?.let { add("${it} 次播放") }
                }
                Text(
                    meta.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
