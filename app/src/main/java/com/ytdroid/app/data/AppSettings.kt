package com.ytdroid.app.data

/**
 * 应用全部设置项。字段与「设置」页一一对应，持久化于 DataStore。
 */
data class AppSettings(
    // 下载
    val downloadDir: String = "",                 // 空 = 应用私有下载目录
    val outputTemplate: String = "%(title)s [%(id)s].%(ext)s",
    val allowPlaylist: Boolean = false,
    val maxParallel: Int = 1,                     // 1..3
    val saveToMediaStore: Boolean = true,         // 完成后复制到公共下载目录
    // 格式与转码
    val formatString: String = "bv*+ba/b",
    val audioOnly: Boolean = false,
    val audioFormat: String = "mp3",
    val audioQuality: String = "0",               // 0 = 最佳
    val embedMetadata: Boolean = true,
    val embedThumbnail: Boolean = true,
    val writeThumbnail: Boolean = false,
    // 字幕
    val writeSubtitles: Boolean = false,
    val subtitleLangs: String = "zh.*,zh-Hans.*,en.*",
    val writeAutoSubs: Boolean = false,
    val embedSubtitles: Boolean = false,
    // 网络与容错
    val retries: Int = 10,
    val concurrentFragments: Int = 2,
    val socketTimeout: Int = 20,
    val rateLimit: String = "",
    val proxy: String = "",
    val userAgent: String = "",
    val cookiesPath: String = "",
    val referer: String = "",
    // 高级
    val extraArgs: String = "",                   // 每行一个参数
    // 更新
    val autoUpdateBinary: Boolean = true,
    val autoUpdateConfig: Boolean = true,
    val customYtDlpUrl: String = "",
    val ffmpegUrl: String = "",
    val configUrl: String = "",
    // 存储（termux-setup-storage 式 SAF 授权）
    val storageUri: String = "",              // 已授权的存储目录 URI（持久化权限）
    // 外观
    val themeMode: String = "system",             // system / light / dark
    val dynamicColor: Boolean = true,
) {
    companion object {
        data class Preset(val label: String, val format: String)

        val FORMAT_PRESETS = listOf(
            Preset("最佳", "bv*+ba/b"),
            Preset("1080P", "bv*[height<=1080]+ba/b[height<=1080]"),
            Preset("720P", "bv*[height<=720]+ba/b[height<=720]"),
            Preset("480P", "bv*[height<=480]+ba/b[height<=480]"),
            Preset("仅音频", "bestaudio/best"),
        )
    }
}
