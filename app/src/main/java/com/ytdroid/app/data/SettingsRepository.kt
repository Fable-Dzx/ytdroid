package com.ytdroid.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ytdroid.app.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

object SettingsRepository {

    private val KEY_DOWNLOAD_DIR = stringPreferencesKey("download_dir")
    private val KEY_OUTPUT_TEMPLATE = stringPreferencesKey("output_template")
    private val KEY_FORMAT = stringPreferencesKey("format")
    private val KEY_AUDIO_ONLY = booleanPreferencesKey("audio_only")
    private val KEY_AUDIO_FORMAT = stringPreferencesKey("audio_format")
    private val KEY_AUDIO_QUALITY = stringPreferencesKey("audio_quality")
    private val KEY_ALLOW_PLAYLIST = booleanPreferencesKey("allow_playlist")
    private val KEY_EMBED_META = booleanPreferencesKey("embed_meta")
    private val KEY_EMBED_THUMB = booleanPreferencesKey("embed_thumb")
    private val KEY_WRITE_THUMB = booleanPreferencesKey("write_thumb")
    private val KEY_WRITE_SUBS = booleanPreferencesKey("write_subs")
    private val KEY_SUB_LANGS = stringPreferencesKey("sub_langs")
    private val KEY_WRITE_AUTO_SUBS = booleanPreferencesKey("write_auto_subs")
    private val KEY_EMBED_SUBS = booleanPreferencesKey("embed_subs")
    private val KEY_RETRIES = intPreferencesKey("retries")
    private val KEY_CONCURRENT = intPreferencesKey("concurrent_fragments")
    private val KEY_TIMEOUT = intPreferencesKey("socket_timeout")
    private val KEY_RATE = stringPreferencesKey("rate_limit")
    private val KEY_PROXY = stringPreferencesKey("proxy")
    private val KEY_UA = stringPreferencesKey("user_agent")
    private val KEY_COOKIES = stringPreferencesKey("cookies")
    private val KEY_REFERER = stringPreferencesKey("referer")
    private val KEY_EXTRA = stringPreferencesKey("extra_args")
    private val KEY_MAX_PARALLEL = intPreferencesKey("max_parallel")
    private val KEY_MEDIASTORE = booleanPreferencesKey("media_store")
    private val KEY_AUTO_BIN = booleanPreferencesKey("auto_bin")
    private val KEY_AUTO_CFG = booleanPreferencesKey("auto_cfg")
    private val KEY_CUSTOM_YTD_URL = stringPreferencesKey("custom_ytd_url")
    private val KEY_FFMPEG_URL = stringPreferencesKey("ffmpeg_url")
    private val KEY_CONFIG_URL = stringPreferencesKey("config_url")
    private val KEY_THEME = stringPreferencesKey("theme")
    private val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color")

    fun settings(context: Context): Flow<AppSettings> {
        val ds = context.applicationContext.dataStore
        return ds.data.map { p ->
            AppSettings(
                downloadDir = p[KEY_DOWNLOAD_DIR] ?: "",
                outputTemplate = p[KEY_OUTPUT_TEMPLATE] ?: "%(title)s [%(id)s].%(ext)s",
                formatString = p[KEY_FORMAT] ?: "bv*+ba/b",
                audioOnly = p[KEY_AUDIO_ONLY] ?: false,
                audioFormat = p[KEY_AUDIO_FORMAT] ?: "mp3",
                audioQuality = p[KEY_AUDIO_QUALITY] ?: "0",
                allowPlaylist = p[KEY_ALLOW_PLAYLIST] ?: false,
                embedMetadata = p[KEY_EMBED_META] ?: true,
                embedThumbnail = p[KEY_EMBED_THUMB] ?: true,
                writeThumbnail = p[KEY_WRITE_THUMB] ?: false,
                writeSubtitles = p[KEY_WRITE_SUBS] ?: false,
                subtitleLangs = p[KEY_SUB_LANGS] ?: "zh.*,zh-Hans.*,en.*",
                writeAutoSubs = p[KEY_WRITE_AUTO_SUBS] ?: false,
                embedSubtitles = p[KEY_EMBED_SUBS] ?: false,
                retries = p[KEY_RETRIES] ?: 10,
                concurrentFragments = p[KEY_CONCURRENT] ?: 2,
                socketTimeout = p[KEY_TIMEOUT] ?: 20,
                rateLimit = p[KEY_RATE] ?: "",
                proxy = p[KEY_PROXY] ?: "",
                userAgent = p[KEY_UA] ?: "",
                cookiesPath = p[KEY_COOKIES] ?: "",
                referer = p[KEY_REFERER] ?: "",
                extraArgs = p[KEY_EXTRA] ?: "",
                maxParallel = p[KEY_MAX_PARALLEL] ?: 1,
                saveToMediaStore = p[KEY_MEDIASTORE] ?: true,
                autoUpdateBinary = p[KEY_AUTO_BIN] ?: true,
                autoUpdateConfig = p[KEY_AUTO_CFG] ?: true,
                customYtDlpUrl = p[KEY_CUSTOM_YTD_URL] ?: "",
                ffmpegUrl = p[KEY_FFMPEG_URL] ?: "",
                configUrl = p[KEY_CONFIG_URL] ?: BuildConfig.DEFAULT_CONFIG_URL,
                themeMode = p[KEY_THEME] ?: "system",
                dynamicColor = p[KEY_DYNAMIC] ?: true,
            )
        }
    }

    suspend fun current(context: Context): AppSettings = settings(context).first()

    suspend fun save(context: Context, transform: (AppSettings) -> AppSettings) {
        val target = transform(current(context))
        context.applicationContext.dataStore.edit { p ->
            p[KEY_DOWNLOAD_DIR] = target.downloadDir
            p[KEY_OUTPUT_TEMPLATE] = target.outputTemplate
            p[KEY_FORMAT] = target.formatString
            p[KEY_AUDIO_ONLY] = target.audioOnly
            p[KEY_AUDIO_FORMAT] = target.audioFormat
            p[KEY_AUDIO_QUALITY] = target.audioQuality
            p[KEY_ALLOW_PLAYLIST] = target.allowPlaylist
            p[KEY_EMBED_META] = target.embedMetadata
            p[KEY_EMBED_THUMB] = target.embedThumbnail
            p[KEY_WRITE_THUMB] = target.writeThumbnail
            p[KEY_WRITE_SUBS] = target.writeSubtitles
            p[KEY_SUB_LANGS] = target.subtitleLangs
            p[KEY_WRITE_AUTO_SUBS] = target.writeAutoSubs
            p[KEY_EMBED_SUBS] = target.embedSubtitles
            p[KEY_RETRIES] = target.retries
            p[KEY_CONCURRENT] = target.concurrentFragments
            p[KEY_TIMEOUT] = target.socketTimeout
            p[KEY_RATE] = target.rateLimit
            p[KEY_PROXY] = target.proxy
            p[KEY_UA] = target.userAgent
            p[KEY_COOKIES] = target.cookiesPath
            p[KEY_REFERER] = target.referer
            p[KEY_EXTRA] = target.extraArgs
            p[KEY_MAX_PARALLEL] = target.maxParallel
            p[KEY_MEDIASTORE] = target.saveToMediaStore
            p[KEY_AUTO_BIN] = target.autoUpdateBinary
            p[KEY_AUTO_CFG] = target.autoUpdateConfig
            p[KEY_CUSTOM_YTD_URL] = target.customYtDlpUrl
            p[KEY_FFMPEG_URL] = target.ffmpegUrl
            p[KEY_CONFIG_URL] = target.configUrl
            p[KEY_THEME] = target.themeMode
            p[KEY_DYNAMIC] = target.dynamicColor
        }
    }
}
