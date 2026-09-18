package com.ytdroid.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytdroid.app.data.AppSettings
import com.ytdroid.app.data.SettingsRepository
import com.ytdroid.app.engine.YtDlpEngine
import com.ytdroid.app.util.resolveDownloadDir
import com.ytdroid.app.util.toast
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun EngineScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engineState by YtDlpEngine.state.collectAsStateWithLifecycle()
    val settings by SettingsRepository.settings(context).collectAsStateWithLifecycle(initialValue = AppSettings())

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            context.toast("正在安装 ffmpeg…")
            val tmp = File(context.cacheDir, "ffmpeg-picked.zip")
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { input.copyTo(it) }
                }
            }
            if (tmp.exists() && tmp.length() > 0) {
                YtDlpEngine.installFfmpegZip(context, tmp)
            } else {
                context.toast("读取文件失败")
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { SectionTitle("引擎状态") }
        item {
            CardBox {
                ActionRow("yt-dlp 版本", engineState.ytDlpVersion ?: "获取中…") {
                    if (engineState.updating) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 4.dp), strokeWidth = 2.dp)
                    }
                }
                ActionRow("ffmpeg", engineState.ffmpegVersion ?: "未安装") { }
            }
        }

        item { SectionTitle("更新与维护") }
        item {
            CardBox {
                ActionRow("更新 yt-dlp", "从官方 GitHub 下载最新源码包并替换") {
                    Button(
                        onClick = { scope.launch { YtDlpEngine.updateYtDlp(context, settings, force = true) } },
                        enabled = !engineState.updating,
                    ) { Text("更新") }
                }
                ActionRow("安装 ffmpeg", "从下方 URL 下载 zip 并安装") {
                    Button(
                        onClick = {
                            val u = settings.ffmpegUrl.trim()
                            if (u.isEmpty()) {
                                context.toast("请先填写 ffmpeg 下载地址")
                                return@Button
                            }
                            scope.launch {
                                context.toast("正在下载 ffmpeg…")
                                YtDlpEngine.downloadAndInstallFfmpeg(context, u)
                            }
                        },
                        enabled = !engineState.updating,
                    ) { Text("安装") }
                }
                ActionRow("从本地文件安装 ffmpeg", "选择含 ffmpeg 可执行文件的 zip") {
                    OutlinedButton(
                        onClick = {
                            filePicker.launch(
                                arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*")
                            )
                        },
                        enabled = !engineState.updating,
                    ) { Text("选择 zip") }
                }
                ActionRow("同步配置文件", "从仓库拉取 config/yt-dlp.conf") {
                    Button(
                        onClick = { scope.launch { YtDlpEngine.updateConfig(context, settings, force = true) } },
                        enabled = !engineState.configUpdating,
                    ) { Text("同步") }
                }
                engineState.message?.let { msg ->
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }

        item { SectionTitle("自动更新") }
        item {
            CardBox {
                SwitchRow(
                    title = "自动更新 yt-dlp",
                    subtitle = "启动时检查并更新到最新版",
                    checked = settings.autoUpdateBinary,
                    onCheckedChange = { v -> scope.launch { SettingsRepository.save(context) { it.copy(autoUpdateBinary = v) } } },
                )
                SwitchRow(
                    title = "自动更新配置文件",
                    subtitle = "启动时从仓库同步 config/yt-dlp.conf",
                    checked = settings.autoUpdateConfig,
                    onCheckedChange = { v -> scope.launch { SettingsRepository.save(context) { it.copy(autoUpdateConfig = v) } } },
                )
            }
        }

        item { SectionTitle("下载源") }
        item {
            CardBox {
                TextSettingRow(
                    title = "yt-dlp 自定义源",
                    value = settings.customYtDlpUrl,
                    onValueChange = { v -> scope.launch { SettingsRepository.save(context) { it.copy(customYtDlpUrl = v) } } },
                    subtitle = "留空 = 官方 GitHub 最新版 (tar.gz)",
                )
                TextSettingRow(
                    title = "ffmpeg 下载地址",
                    value = settings.ffmpegUrl,
                    onValueChange = { v -> scope.launch { SettingsRepository.save(context) { it.copy(ffmpegUrl = v) } } },
                    subtitle = "zip 直链，内含 ffmpeg 可执行文件",
                )
                TextSettingRow(
                    title = "配置文件地址",
                    value = settings.configUrl,
                    onValueChange = { v -> scope.launch { SettingsRepository.save(context) { it.copy(configUrl = v) } } },
                    subtitle = "raw 文本链接",
                )
            }
        }

        item { SectionTitle("目录") }
        item {
            CardBox {
                ActionRow("引擎目录", YtDlpEngine.ytdlpDir(context).absolutePath) { }
                ActionRow("下载目录", resolveDownloadDir(context, settings).absolutePath) { }
                ActionRow("配置文件", YtDlpEngine.configFile(context).absolutePath) { }
            }
        }
    }
}
