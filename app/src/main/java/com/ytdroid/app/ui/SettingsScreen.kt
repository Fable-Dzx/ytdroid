package com.ytdroid.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytdroid.app.data.AppSettings
import com.ytdroid.app.data.SettingsRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by SettingsRepository.settings(context).collectAsStateWithLifecycle(initialValue = AppSettings())

    // 仅 Android 11+ 存在「所有文件访问」；Android 10 及以下分区存储下无法直写公共目录
    val canManageAllFiles = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()
    fun save(t: (AppSettings) -> AppSettings) {
        scope.launch { SettingsRepository.save(context, t) }
    }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { SectionTitle("下载") }
        item {
            CardBox {
                TextSettingRow(
                    title = "下载目录",
                    value = settings.downloadDir,
                    onValueChange = { v -> save { it.copy(downloadDir = v) } },
                    subtitle = "留空 = 应用私有目录；自定义绝对路径需「所有文件访问」权限",
                )
                ActionRow(
                    title = "所有文件访问权限",
                    subtitle = if (canManageAllFiles) {
                        "已授予（可直写公共目录）"
                    } else if (Build.VERSION.SDK_INT >= 30) {
                        "未授予（自定义下载目录需开启；未开启时用应用私有目录并自动存入系统下载）"
                    } else {
                        "系统限制（Android 10 及以下无法直写公共目录，将使用应用私有目录）"
                    },
                ) {
                    if (!canManageAllFiles && Build.VERSION.SDK_INT >= 30) {
                        Button(onClick = {
                            try {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${context.packageName}"),
                                    )
                                )
                            } catch (e: Exception) {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                            }
                        }) { Text("去授权") }
                    }
                }
                TextSettingRow(
                    title = "输出文件名模板 (-o)",
                    value = settings.outputTemplate,
                    onValueChange = { v -> save { it.copy(outputTemplate = v) } },
                )
                SliderRow(
                    title = "最大并行下载",
                    value = settings.maxParallel,
                    range = 1..3,
                    onValue = { v -> save { it.copy(maxParallel = v) } },
                )
                SwitchRow(
                    title = "完成后复制到公共下载",
                    subtitle = "在系统文件管理器中可见（Android 10+）",
                    checked = settings.saveToMediaStore,
                    onCheckedChange = { v -> save { it.copy(saveToMediaStore = v) } },
                )
                SwitchRow(
                    title = "允许播放列表",
                    subtitle = "允许下载整个播放列表",
                    checked = settings.allowPlaylist,
                    onCheckedChange = { v -> save { it.copy(allowPlaylist = v) } },
                )
            }
        }

        item { SectionTitle("格式与转码") }
        item {
            CardBox {
                TextSettingRow(
                    title = "格式选择串 (-f)",
                    value = settings.formatString,
                    onValueChange = { v -> save { it.copy(formatString = v) } },
                    subtitle = "示例：bv*[height<=1080]+ba/b",
                )
                SwitchRow(
                    title = "仅音频",
                    subtitle = "提取音频（需 ffmpeg）",
                    checked = settings.audioOnly,
                    onCheckedChange = { v -> save { it.copy(audioOnly = v) } },
                )
                ChoiceRow(
                    title = "音频格式",
                    options = listOf("mp3", "m4a", "opus", "flac", "best"),
                    selected = settings.audioFormat,
                    onSelect = { v -> save { it.copy(audioFormat = v) } },
                )
                TextSettingRow(
                    title = "音频码率",
                    value = settings.audioQuality,
                    onValueChange = { v -> save { it.copy(audioQuality = v) } },
                    subtitle = "0 = 最佳；也可写 320K / 192K",
                    keyboardType = KeyboardType.Text,
                )
                SwitchRow(
                    title = "嵌入元数据",
                    subtitle = "--embed-metadata",
                    checked = settings.embedMetadata,
                    onCheckedChange = { v -> save { it.copy(embedMetadata = v) } },
                )
                SwitchRow(
                    title = "嵌入缩略图",
                    subtitle = "--embed-thumbnail（无 ffmpeg 时降级为另存）",
                    checked = settings.embedThumbnail,
                    onCheckedChange = { v -> save { it.copy(embedThumbnail = v) } },
                )
                SwitchRow(
                    title = "另存缩略图",
                    subtitle = "--write-thumbnail",
                    checked = settings.writeThumbnail,
                    onCheckedChange = { v -> save { it.copy(writeThumbnail = v) } },
                )
            }
        }

        item { SectionTitle("字幕") }
        item {
            CardBox {
                SwitchRow(
                    title = "下载字幕",
                    subtitle = "--write-subs",
                    checked = settings.writeSubtitles,
                    onCheckedChange = { v -> save { it.copy(writeSubtitles = v) } },
                )
                TextSettingRow(
                    title = "字幕语言",
                    value = settings.subtitleLangs,
                    onValueChange = { v -> save { it.copy(subtitleLangs = v) } },
                    subtitle = "逗号分隔，支持通配，如 zh.*,en.*",
                )
                SwitchRow(
                    title = "自动字幕",
                    subtitle = "--write-auto-subs",
                    checked = settings.writeAutoSubs,
                    onCheckedChange = { v -> save { it.copy(writeAutoSubs = v) } },
                )
                SwitchRow(
                    title = "嵌入字幕",
                    subtitle = "--embed-subs（需 ffmpeg）",
                    checked = settings.embedSubtitles,
                    onCheckedChange = { v -> save { it.copy(embedSubtitles = v) } },
                )
            }
        }

        item { SectionTitle("网络与容错") }
        item {
            CardBox {
                TextSettingRow(
                    title = "重试次数",
                    value = settings.retries.toString(),
                    onValueChange = { v -> save { it.copy(retries = v.toIntOrNull() ?: 0) } },
                    keyboardType = KeyboardType.Number,
                )
                TextSettingRow(
                    title = "并发分片数",
                    value = settings.concurrentFragments.toString(),
                    onValueChange = { v -> save { it.copy(concurrentFragments = v.toIntOrNull() ?: 1) } },
                    subtitle = "--concurrent-fragments",
                    keyboardType = KeyboardType.Number,
                )
                TextSettingRow(
                    title = "Socket 超时（秒）",
                    value = settings.socketTimeout.toString(),
                    onValueChange = { v -> save { it.copy(socketTimeout = v.toIntOrNull() ?: 20) } },
                    keyboardType = KeyboardType.Number,
                )
                TextSettingRow(
                    title = "限速",
                    value = settings.rateLimit,
                    onValueChange = { v -> save { it.copy(rateLimit = v) } },
                    subtitle = "--limit-rate，如 1M / 500K",
                )
                TextSettingRow(
                    title = "代理",
                    value = settings.proxy,
                    onValueChange = { v -> save { it.copy(proxy = v) } },
                    subtitle = "--proxy，如 http://127.0.0.1:7890 或 socks5://…",
                )
                TextSettingRow(
                    title = "User-Agent",
                    value = settings.userAgent,
                    onValueChange = { v -> save { it.copy(userAgent = v) } },
                )
                TextSettingRow(
                    title = "Cookies 文件路径",
                    value = settings.cookiesPath,
                    onValueChange = { v -> save { it.copy(cookiesPath = v) } },
                    subtitle = "Netscape 格式 cookies.txt",
                )
                TextSettingRow(
                    title = "Referer",
                    value = settings.referer,
                    onValueChange = { v -> save { it.copy(referer = v) } },
                )
            }
        }

        item { SectionTitle("高级") }
        item {
            CardBox {
                TextSettingRow(
                    title = "附加参数",
                    value = settings.extraArgs,
                    onValueChange = { v -> save { it.copy(extraArgs = v) } },
                    subtitle = "每行一个参数，追加在命令末尾（优先级最高）",
                    singleLine = false,
                    minLines = 4,
                )
                Text(
                    "示例：每行一个参数，如\n--limit-rate\n2M\n--extractor-args\nyoutube:player_client=android,web",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        item { SectionTitle("外观") }
        item {
            CardBox {
                ChoiceRow(
                    title = "主题",
                    options = listOf("system", "light", "dark"),
                    selected = settings.themeMode,
                    onSelect = { v -> save { it.copy(themeMode = v) } },
                    subtitle = "system = 跟随系统",
                )
                SwitchRow(
                    title = "动态取色",
                    subtitle = "Android 12+ 使用壁纸色（Material You）",
                    checked = settings.dynamicColor,
                    onCheckedChange = { v -> save { it.copy(dynamicColor = v) } },
                )
            }
        }
    }
}
