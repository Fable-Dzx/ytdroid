package com.ytdroid.app.ui

import android.content.Intent
import android.net.Uri
import com.ytdroid.app.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytdroid.app.data.AppSettings
import com.ytdroid.app.data.SettingsRepository
import com.ytdroid.app.engine.YtDlpEngine
import com.ytdroid.app.util.formatBytes
import com.ytdroid.app.util.resolveDownloadDir
import com.ytdroid.app.util.toast
import kotlinx.coroutines.launch
import java.io.File

/**
 * 「环境」页（Termux 式环境管理）：
 * 环境概览 / 组件包（pkg 式）/ 存储访问（termux-setup-storage 式）/ 命令行 / 更新与维护。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnvironmentScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engineState by YtDlpEngine.state.collectAsStateWithLifecycle()
    val settings by SettingsRepository.settings(context).collectAsStateWithLifecycle(initialValue = AppSettings())

    var diskUsage by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(engineState.ready) {
        if (engineState.ready) diskUsage = YtDlpEngine.envDiskUsage(context)
    }

    var showConfigEditor by remember { mutableStateOf(false) }
    var configText by remember { mutableStateOf("") }

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

    val storagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        scope.launch { SettingsRepository.save(context) { it.copy(storageUri = uri.toString()) } }
        context.toast("存储目录已授权")
    }

    val storageName = remember(settings.storageUri) {
        if (settings.storageUri.isEmpty()) ""
        else runCatching {
            DocumentFile.fromTreeUri(context, Uri.parse(settings.storageUri))?.name
                ?: settings.storageUri.substringAfterLast("/")
        }.getOrDefault(settings.storageUri.substringAfterLast("/"))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // ---------- 环境概览 ----------
        item { SectionTitle("环境概览") }
        item {
            CardBox {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    EngineDot(ready = engineState.ready, updating = engineState.updating)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                engineState.updating -> "组件更新中…"
                                engineState.ready -> "环境就绪"
                                else -> "环境初始化…"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            buildList {
                                engineState.pythonVersion?.let { add("Python $it") }
                                engineState.ytDlpVersion?.let { add("yt-dlp $it") }
                                add(engineState.ffmpegVersion?.let { "ffmpeg $it" } ?: "ffmpeg 未安装")
                            }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (engineState.updating || !engineState.ready) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
                engineState.message?.let { msg ->
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                ActionRow("数据占用", diskUsage?.let { formatBytes(it) } ?: "计算中…") { }
                ActionRow("数据目录", context.filesDir.absolutePath) { }
                ActionRow("引擎目录", YtDlpEngine.ytdlpDir(context).absolutePath) { }
                ActionRow("下载目录", resolveDownloadDir(context, settings).absolutePath) { }
                ActionRow("配置文件", YtDlpEngine.configFile(context).absolutePath) { }
            }
        }

        // ---------- 组件包（pkg 式） ----------
        item { SectionTitle("组件包") }
        item {
            CardBox {
                ComponentRow("yt-dlp", engineState.ytDlpVersion ?: "获取中…", "核心下载引擎，随官方源自动更新") {
                    Button(
                        onClick = { scope.launch { YtDlpEngine.updateYtDlp(context, settings, force = true) } },
                        enabled = !engineState.updating,
                    ) { Text("更新") }
                }
                ComponentRow("Python", engineState.pythonVersion ?: "…", "内嵌运行时（Chaquopy）") { }
                ComponentRow("ffmpeg", engineState.ffmpegVersion ?: "未安装", "音视频合并 / 转码 / 缩略图嵌入") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                val u = settings.ffmpegUrl.trim().ifEmpty { BuildConfig.DEFAULT_FFMPEG_URL }
                                scope.launch {
                                    context.toast("正在下载 ffmpeg…")
                                    YtDlpEngine.downloadAndInstallFfmpeg(context, u)
                                }
                            },
                            enabled = !engineState.updating,
                        ) { Text("安装") }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = {
                                filePicker.launch(
                                    arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*")
                                )
                            },
                            enabled = !engineState.updating,
                        ) { Text("选 zip") }
                    }
                }
                ComponentRow("配置文件", "yt-dlp.conf", "全局参数，可在应用内编辑") {
                    OutlinedButton(onClick = {
                        configText = YtDlpEngine.readConfig(context)
                        showConfigEditor = true
                    }) { Text("编辑") }
                }
            }
        }

        // ---------- 存储访问 ----------
        item { SectionTitle("存储访问") }
        item {
            CardBox {
                ActionRow(
                    title = "存储目录授权",
                    subtitle = if (settings.storageUri.isEmpty()) "未授权 · 授权后可长期访问所选目录" else "已授权：$storageName",
                ) {
                    if (settings.storageUri.isEmpty()) {
                        Button(onClick = { storagePicker.launch(null) }) { Text("授权") }
                    } else {
                        OutlinedButton(onClick = { storagePicker.launch(null) }) { Text("重新授权") }
                    }
                }
                ActionRow("浏览存储", "打开系统文件选择器查看已下载文件") {
                    OutlinedButton(onClick = { storagePicker.launch(null) }) { Text("打开") }
                }
            }
        }

        // ---------- 命令行 ----------
        item { SectionTitle("命令行") }
        item {
            CardBox {
                val cliPresets = listOf("-F ", "--list-subs ", "-x ", "--write-description ", "--print title ")
                var cmd by remember { mutableStateOf("") }
                var output by remember { mutableStateOf("") }
                var running by remember { mutableStateOf(false) }

                Text(
                    "任意 yt-dlp 参数直接执行，完整输出实时显示。例如：-F 查看格式列表",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    cliPresets.forEach { p ->
                        FilterChip(
                            selected = false,
                            onClick = { cmd += p },
                            label = { Text(p.trim()) },
                        )
                    }
                }
                OutlinedTextField(
                    value = cmd,
                    onValueChange = { cmd = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    label = { Text("命令（yt-dlp 参数或完整命令）") },
                    placeholder = { Text("yt-dlp -F https://…") },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            val c = cmd.trim()
                            if (c.isEmpty()) {
                                context.toast("请输入命令")
                                return@Button
                            }
                            running = true
                            output = ""
                            scope.launch {
                                val rc = YtDlpEngine.runCli(context, c) { line -> output += line + "\n" }
                                output += "\n[进程退出，代码 $rc]"
                                running = false
                            }
                        },
                        enabled = cmd.trim().isNotEmpty() && !running && engineState.ready,
                    ) {
                        if (running) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(if (running) "运行中" else "运行")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { YtDlpEngine.cancelCli(context) }, enabled = running) { Text("停止") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { output = "" }, enabled = output.isNotEmpty()) { Text("清空") }
                }
                if (output.isNotEmpty()) {
                    Text(
                        output,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .height(240.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        }

        // ---------- 更新与维护 ----------
        item { SectionTitle("更新与维护") }
        item {
            CardBox {
                ActionRow("一键全部更新", "yt-dlp 引擎 + 配置文件（等价 pkg upgrade）") {
                    Button(
                        onClick = {
                            scope.launch {
                                YtDlpEngine.updateYtDlp(context, settings, force = true)
                                YtDlpEngine.updateConfig(context, settings, force = true)
                                context.toast("更新完成")
                            }
                        },
                        enabled = !engineState.updating && !engineState.configUpdating,
                    ) { Text("升级") }
                }
                ActionRow("同步配置文件", "从仓库拉取 config/yt-dlp.conf") {
                    Button(
                        onClick = { scope.launch { YtDlpEngine.updateConfig(context, settings, force = true) } },
                        enabled = !engineState.configUpdating,
                    ) { Text("同步") }
                }
                ActionRow("重建环境", "清空引擎目录并重新初始化（运行任务时勿用）") {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                if (YtDlpEngine.reinitialize(context)) context.toast("环境已重建")
                                else context.toast("重建失败")
                            }
                        },
                        enabled = !engineState.updating,
                    ) { Text("重建") }
                }
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

        // ---------- 下载源 ----------
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
                    subtitle = "留空 = 内置默认源一键安装（可选自定义 zip 直链）",
                )
                TextSettingRow(
                    title = "配置文件地址",
                    value = settings.configUrl,
                    onValueChange = { v -> scope.launch { SettingsRepository.save(context) { it.copy(configUrl = v) } } },
                    subtitle = "raw 文本链接",
                )
            }
        }
    }

    // ---------- 配置文件编辑器 ----------
    if (showConfigEditor) {
        AlertDialog(
            onDismissRequest = { showConfigEditor = false },
            title = { Text("编辑 yt-dlp.conf") },
            text = {
                OutlinedTextField(
                    value = configText,
                    onValueChange = { configText = it },
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (YtDlpEngine.writeConfig(context, configText)) context.toast("已保存，下次下载生效")
                    else context.toast("保存失败")
                    showConfigEditor = false
                }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        if (YtDlpEngine.restoreDefaultConfig(context)) context.toast("已恢复默认")
                        else context.toast("恢复失败")
                        showConfigEditor = false
                    }) { Text("恢复默认") }
                    TextButton(onClick = { showConfigEditor = false }) { Text("取消") }
                }
            },
        )
    }
}
