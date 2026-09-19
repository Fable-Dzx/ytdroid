package com.ytdroid.app.engine

import android.content.Context
import com.ytdroid.app.BuildConfig
import com.ytdroid.app.data.AppSettings
import com.ytdroid.app.data.SettingsRepository
import com.ytdroid.app.util.Logs
import com.ytdroid.app.util.Net
import com.ytdroid.app.util.Zips
import com.ytdroid.app.util.ffmpegVersion
import com.ytdroid.app.util.parseShellArgs
import com.ytdroid.app.util.resolveDownloadDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 引擎管理：内置 Python 包解压、yt-dlp / ffmpeg 版本管理与更新、
 * 配置文件同步、参数构建、任务执行与进度解析。
 */
object YtDlpEngine {

    private const val YTDLP_SOURCE_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.tar.gz"

    data class EngineState(
        val ready: Boolean = false,
        val pythonVersion: String? = null,
        val ytDlpVersion: String? = null,
        val ffmpegVersion: String? = null,
        val updating: Boolean = false,
        val configUpdating: Boolean = false,
        val message: String? = null,
    )

    data class VideoInfo(
        val title: String,
        val uploader: String,
        val duration: Double?,
        val thumbnail: String?,
        val viewCount: Long?,
        val width: Int?,
        val height: Int?,
    )

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val initMutex = Mutex()
    private val initialized = MutableStateFlow(false)

    fun ytdlpDir(context: Context): File = File(context.filesDir, "ytdlp")
    fun binDir(context: Context): File = File(context.filesDir, "bin")
    fun ffmpegFile(context: Context): File = File(binDir(context), "ffmpeg")
    fun configFile(context: Context): File = File(context.filesDir, "config/yt-dlp.conf")
    fun runDir(context: Context): File = File(context.filesDir, "run")
    private fun versionMarker(context: Context): File = File(context.filesDir, "ytdlp.version")

    // ---------------- 初始化 ----------------

    /** 幂等初始化：解压内置包 → 启动 Python → 按设置自动更新。 */
    suspend fun ensureReady(context: Context, settings: AppSettings? = null): Boolean {
        if (initialized.value) return true
        return initMutex.withLock {
            if (initialized.value) return true
            val appCtx = context.applicationContext
            try {
                Logs.append(appCtx, "engine", "初始化开始")
                extractBundled(appCtx)
                cleanupRunDir(appCtx)
                PythonBridge.ensureStarted(appCtx)
                val s = settings ?: SettingsRepository.current(appCtx)
                refreshVersions(appCtx)
                if (s.autoUpdateBinary) updateYtDlpIfNeeded(appCtx, s, force = false)
                if (s.autoUpdateConfig) updateConfig(appCtx, s, force = false)
                refreshVersions(appCtx)
                initialized.value = true
                _state.value = _state.value.copy(ready = true, message = null)
                Logs.append(appCtx, "engine", "初始化完成（yt-dlp ${_state.value.ytDlpVersion ?: "?"} / ffmpeg ${_state.value.ffmpegVersion ?: "未安装"} / Python ${_state.value.pythonVersion ?: "?"}）")
                true
            } catch (e: Exception) {
                Logs.append(appCtx, "engine", "初始化失败: ${e.message}")
                _state.value = _state.value.copy(message = "引擎初始化失败: ${e.message}")
                false
            }
        }
    }

    private suspend fun extractBundled(context: Context) {
        val dir = ytdlpDir(context)
        if (File(dir, "yt_dlp").isDirectory && File(dir, "runner.py").exists()) return
        dir.deleteRecursively()
        dir.mkdirs()
        context.assets.open("ytdlp.zip").use { input ->
            val tmp = File(dir.parentFile, "ytdlp.tmp.zip")
            tmp.outputStream().use { input.copyTo(it) }
            Zips.extractZip(tmp, dir)
            tmp.delete()
        }
        val cfg = configFile(context)
        if (!cfg.exists()) {
            cfg.parentFile?.mkdirs()
            context.assets.open("config/yt-dlp.conf").use { input ->
                cfg.outputStream().use { input.copyTo(it) }
            }
        }
    }

    private fun cleanupRunDir(context: Context) {
        try {
            val run = runDir(context)
            if (!run.exists()) return
            val cutoff = System.currentTimeMillis() - 24 * 3600 * 1000L
            run.listFiles()?.forEach { f ->
                if (f.lastModified() < cutoff) f.delete()
            }
        } catch (e: Exception) {
        }
    }

    private suspend fun refreshVersions(context: Context) {
        val pv = withContext(Dispatchers.IO) { PythonBridge.pythonVersion() }
        val yv = withContext(Dispatchers.IO) { PythonBridge.currentVersion(ytdlpDir(context)) }
        val fv = withContext(Dispatchers.IO) { ffmpegVersion(binDir(context)) }
        _state.value = _state.value.copy(pythonVersion = pv, ytDlpVersion = yv, ffmpegVersion = fv)
    }

    // ---------------- 更新 ----------------

    private suspend fun updateYtDlpIfNeeded(context: Context, settings: AppSettings, force: Boolean) {
        val installed = PythonBridge.currentVersion(ytdlpDir(context)) ?: return
        val marker = versionMarker(context)
        val marked = runCatching { marker.readText().trim() }.getOrNull()
        if (!force && marked == installed) return
        updateYtDlp(context, settings, force)
    }

    suspend fun updateYtDlp(context: Context, settings: AppSettings, force: Boolean) {
        if (_state.value.updating) return
        _state.value = _state.value.copy(updating = true, message = null)
        try {
            val custom = settings.customYtDlpUrl.trim()
            val url = if (custom.isNotEmpty()) custom else YTDLP_SOURCE_URL
            val tgz = File(context.cacheDir, "yt-dlp.tar.gz")
            var lastErr: String? = null
            var downloaded = false
            for (u in Net.candidateUrls(url)) {
                lastErr = Net.downloadToFile(u, tgz)
                if (lastErr == null) { downloaded = true; break }
            }
            if (!downloaded) {
                Logs.append(context, "yt-dlp", "更新下载失败：$lastErr")
                _state.value = _state.value.copy(message = "下载 yt-dlp 更新失败：$lastErr")
                return
            }
            val dir = ytdlpDir(context)
            val tmpDir = File(context.filesDir, "ytdlp-new")
            tmpDir.deleteRecursively()
            tmpDir.mkdirs()
            val n = Zips.extractTarGzPrefix(tgz, tmpDir, "yt_dlp")
            if (n == 0) {
                tmpDir.deleteRecursively()
                _state.value = _state.value.copy(message = "更新包内未找到 yt_dlp 包")
                return
            }
            val src = File(tmpDir, "yt_dlp")
            val target = File(dir, "yt_dlp")
            if (target.exists()) target.deleteRecursively()
            if (!src.renameTo(target)) {
                // 跨分区回退为逐文件复制
                target.mkdirs()
                src.copyRecursively(target, overwrite = true)
            }
            tmpDir.deleteRecursively()
            refreshVersions(context)
            val newVersion = _state.value.ytDlpVersion
            if (newVersion != null) {
                versionMarker(context).writeText(newVersion)
            }
            _state.value = _state.value.copy(message = "yt-dlp 已更新至 $newVersion")
            Logs.append(context, "yt-dlp", "已更新至 $newVersion（源: $url）")
        } catch (e: Exception) {
            Logs.append(context, "yt-dlp", "更新失败: ${e.message}")
            _state.value = _state.value.copy(message = "yt-dlp 更新失败: ${e.message}")
        } finally {
            _state.value = _state.value.copy(updating = false)
        }
    }

    /** 从 zip 文件安装 ffmpeg（库式包：可执行文件 + 同目录 .so，自动平铺到 bin；兼容旧式单文件包）。 */
    suspend fun installFfmpegZip(context: Context, zipFile: File) {
        if (_state.value.updating) return
        _state.value = _state.value.copy(updating = true, message = null)
        try {
            val tmp = File(context.filesDir, "ffmpeg-tmp")
            tmp.deleteRecursively()
            tmp.mkdirs()
            val n = Zips.extractZip(zipFile, tmp)
            if (n == 0) {
                _state.value = _state.value.copy(message = "压缩包为空")
                return
            }
            val bin = binDir(context)
            bin.mkdirs()
            // 平铺复制到 bin（共享库必须与可执行文件同目录）；只保留需要的文件，跳过 jar 的 META-INF 等杂质
            var copied = 0
            tmp.walkTopDown().forEach { f ->
                if (f.isFile) {
                    val name = f.name
                    // 只保留 ffmpeg / ffprobe 与核心共享库，排除 JavaCPP 的 JNI 包装（libjni*.so）及 jar 元数据
                    val keep = name == "ffmpeg" || name == "ffprobe" ||
                        (name.endsWith(".so") && !name.startsWith("libjni"))
                    if (keep) {
                        val dst = File(bin, name)
                        if (!dst.isDirectory) {
                            f.copyTo(dst, overwrite = true)
                            copied++
                            if (name == "ffmpeg" || name == "ffprobe") dst.setExecutable(true, false)
                        }
                    }
                }
            }
            tmp.deleteRecursively()
            if (copied == 0) {
                _state.value = _state.value.copy(message = "压缩包为空")
                return
            }
            val ff = ffmpegFile(context)
            if (!ff.exists()) {
                _state.value = _state.value.copy(message = "压缩包内未找到 ffmpeg 可执行文件")
                Logs.append(context, "ffmpeg", "安装失败：压缩包内未找到可执行文件")
                return
            }
            refreshVersions(context)
            _state.value = _state.value.copy(message = "ffmpeg 安装完成：${_state.value.ffmpegVersion ?: "已就绪"}")
            Logs.append(context, "ffmpeg", "安装完成（${_state.value.ffmpegVersion ?: "已就绪"}，共 $copied 个文件）")
        } catch (e: Exception) {
            Logs.append(context, "ffmpeg", "安装失败: ${e.message}")
            _state.value = _state.value.copy(message = "ffmpeg 安装失败: ${e.message}")
        } finally {
            _state.value = _state.value.copy(updating = false)
        }
    }

    /** 从 URL 下载 ffmpeg 并安装（默认源 + 镜像 + 仓库兜底，逐个回退）。 */
    suspend fun downloadAndInstallFfmpeg(context: Context, url: String) {
        if (_state.value.updating) return
        _state.value = _state.value.copy(updating = true, message = null)
        try {
            var lastErr: String? = null
            var zip: File? = null
            val candidates = mutableListOf<String>()
            candidates += Net.candidateUrls(url)
            if (url != BuildConfig.FFMPEG_FALLBACK_URL) {
                candidates += Net.candidateUrls(BuildConfig.FFMPEG_FALLBACK_URL)
            }
            Logs.append(context, "ffmpeg", "开始下载安装（源: $url）")
            for (u in candidates.distinct()) {
                lastErr = Net.downloadToFile(u, File(context.cacheDir, "ffmpeg.zip"))
                if (lastErr == null) { zip = File(context.cacheDir, "ffmpeg.zip"); break }
            }
            if (zip == null) {
                Logs.append(context, "ffmpeg", "下载失败：$lastErr")
                _state.value = _state.value.copy(message = "ffmpeg 下载失败：$lastErr")
                return
            }
            _state.value = _state.value.copy(updating = false)
            installFfmpegZip(context, zip)
        } catch (e: Exception) {
            Logs.append(context, "ffmpeg", "下载失败: ${e.message}")
            _state.value = _state.value.copy(updating = false, message = "ffmpeg 下载失败: ${e.message}")
        }
    }

    private fun findExecutable(root: File, name: String): File? {
        if (!root.isDirectory) return null
        val queue = ArrayDeque<File>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val d = queue.removeFirst()
            d.listFiles()?.forEach { f ->
                when {
                    f.isDirectory -> queue.add(f)
                    f.name == name -> return f
                }
            }
        }
        return null
    }

    suspend fun updateConfig(context: Context, settings: AppSettings, force: Boolean) {
        if (_state.value.configUpdating) return
        _state.value = _state.value.copy(configUpdating = true, message = null)
        try {
            val url = settings.configUrl.trim().ifEmpty { BuildConfig.DEFAULT_CONFIG_URL }
            val text = Net.getText(url)
            if (text == null) {
                Logs.append(context, "config", "获取配置文件失败（$url）")
                _state.value = _state.value.copy(message = "获取配置文件失败")
                return
            }
            val cfg = configFile(context)
            cfg.parentFile?.mkdirs()
            cfg.writeText(text)
            _state.value = _state.value.copy(message = "配置文件已更新")
            Logs.append(context, "config", "已更新（$url）")
        } catch (e: Exception) {
            Logs.append(context, "config", "更新失败: ${e.message}")
            _state.value = _state.value.copy(message = "配置更新失败: ${e.message}")
        } finally {
            _state.value = _state.value.copy(configUpdating = false)
        }
    }

    // ---------------- 环境维护（Termux 式） ----------------

    /** 读取当前配置文件全文。 */
    fun readConfig(context: Context): String =
        runCatching { configFile(context).readText() }.getOrDefault("")

    /** 写回配置文件，返回是否成功。 */
    fun writeConfig(context: Context, text: String): Boolean = try {
        val cfg = configFile(context)
        cfg.parentFile?.mkdirs()
        cfg.writeText(text)
        true
    } catch (e: Exception) {
        false
    }

    /** 从内置资源恢复默认配置文件。 */
    fun restoreDefaultConfig(context: Context): Boolean = try {
        val cfg = configFile(context)
        cfg.parentFile?.mkdirs()
        context.assets.open("config/yt-dlp.conf").use { input ->
            cfg.outputStream().use { input.copyTo(it) }
        }
        true
    } catch (e: Exception) {
        false
    }

    /** 应用私有数据目录总占用（引擎 + 运行产物等）。 */
    suspend fun envDiskUsage(context: Context): Long = withContext(Dispatchers.IO) {
        runCatching {
            context.filesDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
    }

    /**
     * 重建环境：清空引擎目录与版本标记后重新初始化（等价 Termux 的重新安装核心包）。
     * 注意：有正在运行的任务时请勿调用。
     */
    suspend fun reinitialize(context: Context): Boolean {
        val appCtx = context.applicationContext
        initMutex.withLock {
            initialized.value = false
            ytdlpDir(appCtx).deleteRecursively()
            versionMarker(appCtx).delete()
            _state.value = _state.value.copy(
                ready = false, pythonVersion = null, ytDlpVersion = null, ffmpegVersion = null,
                message = "正在重建环境…",
            )
        }
        return ensureReady(appCtx)
    }

    // ---------------- 命令行运行器 ----------------

    @Volatile
    private var cliIdentFile: File? = null

    /**
     * 自由命令行运行器：把用户输入的命令（如 `yt-dlp -F <url>` 或直接 `-F <url>`）
     * 交给内嵌 Python 的 yt-dlp 执行，stdout/stderr 逐行回调，返回退出码。
     */
    suspend fun runCli(
        context: Context,
        commandLine: String,
        onLine: (String) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val settings = SettingsRepository.current(appCtx)
        if (!ensureReady(appCtx, settings)) return@withContext 2

        val tokens = parseShellArgs(commandLine)
        if (tokens.isEmpty()) return@withContext 2
        // runner.py 会自动在 argv 前补 "yt-dlp" 程序名，这里剥掉用户手写的
        val argv = if (tokens[0] == "yt-dlp") tokens.drop(1) else tokens
        Logs.append(appCtx, "cli", "执行: yt-dlp ${argv.joinToString(" ").take(300)}")

        val dir = ytdlpDir(appCtx)
        val run = runDir(appCtx)
        run.mkdirs()

        val ts = System.currentTimeMillis()
        val argfile = File(run, "cli-$ts.json")
        val outFile = File(run, "cli-$ts.out")
        val errFile = File(run, "cli-$ts.err")
        val rcFile = File(run, "cli-$ts.rc")
        val identFile = File(run, "cli-$ts.ident")
        argfile.delete(); outFile.delete(); errFile.delete(); rcFile.delete(); identFile.delete()

        val cfg = JSONObject().apply {
            put("argv", JSONArray(argv))
            put("extra_path", dir.absolutePath)
            put("cwd", resolveDownloadDir(appCtx, settings).absolutePath)
            put("redirect", JSONObject().apply {
                put("stdout", outFile.absolutePath)
                put("stderr", errFile.absolutePath)
            })
            put("rcfile", rcFile.absolutePath)
            put("identfile", identFile.absolutePath)
        }
        argfile.writeText(cfg.toString())
        cliIdentFile = identFile

        val stop = AtomicBoolean(false)
        val tailer = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            tailOutputs(outFile, errFile, stop = { stop.get() }) { line ->
                if (line.isNotBlank()) onLine(line)
            }
        }
        try {
            PythonBridge.ensureStarted(appCtx)
            PythonBridge.run(argfile, dir)
        } finally {
            stop.set(true)
            tailer.join()
            cliIdentFile = null
        }
        val rc = runCatching { rcFile.readText().trim().toInt() }.getOrNull() ?: 2
        Logs.append(appCtx, "cli", "执行结束（退出码 $rc）")
        rc
    }

    /** 请求取消当前命令行任务（注入 KeyboardInterrupt）。 */
    fun cancelCli(context: Context) {
        cliIdentFile?.let { PythonBridge.cancel(it, ytdlpDir(context)) }
    }

    // ---------------- 参数构建 ----------------

    fun buildArgs(context: Context, settings: AppSettings, url: String): List<String> {
        val a = mutableListOf<String>()
        val cfg = configFile(context)
        if (cfg.exists()) {
            a += "--config-locations"
            a += cfg.absolutePath
        }
        a += "--newline"
        a += "--no-warnings"
        val dir = resolveDownloadDir(context, settings)
        a += "--paths"
        a += dir.absolutePath
        a += "-o"
        a += settings.outputTemplate.ifBlank { "%(title)s [%(id)s].%(ext)s" }
        a += if (settings.allowPlaylist) "--yes-playlist" else "--no-playlist"

        val hasFfmpeg = ffmpegFile(context).exists()
        if (settings.audioOnly) {
            if (hasFfmpeg) {
                a += "-f"; a += "bestaudio/best"
                a += "-x"
                a += "--audio-format"; a += settings.audioFormat.ifBlank { "mp3" }
                a += "--audio-quality"; a += settings.audioQuality.ifBlank { "0" }
            } else {
                // 无 ffmpeg：直接下载最佳原生音频，不做转码
                a += "-f"; a += "bestaudio[ext=m4a]/bestaudio[ext=opus]/bestaudio[ext=webm]/bestaudio/best"
            }
        } else {
            val fmt = settings.formatString.ifBlank { "bv*+ba/b" }
            a += "-f"; a += if (!hasFfmpeg && fmt.contains("+")) "b/bv*+ba/b" else fmt
        }

        if (settings.embedMetadata) a += "--embed-metadata"
        if (settings.embedThumbnail) {
            if (hasFfmpeg) a += "--embed-thumbnail" else a += "--write-thumbnail"
        }
        if (settings.writeThumbnail) a += "--write-thumbnail"
        if (settings.writeSubtitles) {
            a += "--write-subs"
            a += "--sub-langs"; a += settings.subtitleLangs.ifBlank { "all" }
        }
        if (settings.writeAutoSubs) a += "--write-auto-subs"
        if (settings.embedSubtitles && hasFfmpeg) a += "--embed-subs"

        if (settings.concurrentFragments > 1) {
            a += "--concurrent-fragments"; a += settings.concurrentFragments.toString()
        }
        if (settings.retries >= 0) {
            a += "--retries"; a += settings.retries.toString()
        }
        if (settings.socketTimeout > 0) {
            a += "--socket-timeout"; a += settings.socketTimeout.toString()
        }
        if (settings.rateLimit.isNotBlank()) { a += "--limit-rate"; a += settings.rateLimit.trim() }
        if (settings.proxy.isNotBlank()) { a += "--proxy"; a += settings.proxy.trim() }
        if (settings.userAgent.isNotBlank()) { a += "--user-agent"; a += settings.userAgent.trim() }
        if (settings.cookiesPath.isNotBlank()) { a += "--cookies"; a += settings.cookiesPath.trim() }
        if (settings.referer.isNotBlank()) { a += "--referer"; a += settings.referer.trim() }

        if (hasFfmpeg) {
            a += "--ffmpeg-location"; a += binDir(context).absolutePath
        }

        // 附加参数：每行一个参数，注释行以 # 开头
        settings.extraArgs.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { a += it }

        a += url
        return a
    }

    // ---------------- 任务执行 ----------------

    /**
     * 在 IO 线程上运行 yt-dlp，同时轮询输出文件解析进度。
     * 返回退出码。
     */
    suspend fun runTask(
        context: Context,
        task: DownloadTask,
        settings: AppSettings,
        onUpdate: (DownloadTask) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val dir = ytdlpDir(appCtx)
        val run = runDir(appCtx)
        run.mkdirs()

        val args = buildArgs(appCtx, settings, task.url)
        val argfile = File(run, "task-${task.id}.json")
        val outFile = File(run, "task-${task.id}.out")
        val errFile = File(run, "task-${task.id}.err")
        val rcFile = File(run, "task-${task.id}.rc")
        val identFile = File(run, "task-${task.id}.ident")
        outFile.delete(); errFile.delete(); rcFile.delete(); identFile.delete()

        val cfg = JSONObject().apply {
            put("argv", JSONArray(args))
            put("extra_path", dir.absolutePath)
            put("cwd", resolveDownloadDir(appCtx, settings).absolutePath)
            put("redirect", JSONObject().apply {
                put("stdout", outFile.absolutePath)
                put("stderr", errFile.absolutePath)
            })
            put("rcfile", rcFile.absolutePath)
            put("identfile", identFile.absolutePath)
        }
        argfile.writeText(cfg.toString())

        var current = task
        val stop = AtomicBoolean(false)
        val tailer = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            tailOutputs(outFile, errFile, stop = { stop.get() }) { line ->
                current = ProgressParser.apply(current, line)
                onUpdate(current)
            }
        }

        try {
            PythonBridge.ensureStarted(appCtx)
            PythonBridge.run(argfile, dir)
        } finally {
            stop.set(true)
            tailer.join()
        }

        runCatching { rcFile.readText().trim().toInt() }.getOrNull() ?: 2
    }

    private suspend fun tailOutputs(
        outFile: File,
        errFile: File,
        stop: () -> Boolean,
        onLine: (String) -> Unit,
    ) {
        val files = listOf(outFile, errFile)
        val ptrs = mutableMapOf<File, Long>()
        val pending = mutableMapOf<File, StringBuilder>()
        files.forEach { ptrs[it] = 0L; pending[it] = StringBuilder() }

        var idle = 0L
        while (true) {
            var newData = false
            for (f in files) {
                val len = f.length()
                val ptr = ptrs[f] ?: 0L
                if (len > ptr) {
                    newData = true
                    val chunk = (len - ptr).toInt().coerceAtMost(256 * 1024)
                    val bytes = ByteArray(chunk)
                    RandomAccessFile(f, "r").use { raf ->
                        raf.seek(ptr)
                        var n = raf.read(bytes)
                        if (n > 0) {
                            val sb = pending[f] ?: StringBuilder()
                            sb.append(String(bytes, 0, n, Charsets.UTF_8))
                            var idx: Int
                            while (sb.indexOf("\n").also { idx = it } >= 0) {
                                val line = sb.substring(0, idx).trimEnd('\r')
                                sb.delete(0, idx + 1)
                                if (line.isNotEmpty()) onLine(line)
                            }
                        }
                    }
                    ptrs[f] = len
                }
            }
            idle = if (newData) 0 else idle + 150
            if (stop() && idle >= 500) break
            delay(150)
        }
        // 冲刷残留行
        pending.forEach { (f, sb) ->
            if (sb.isNotEmpty()) {
                val rest = sb.toString().trimEnd('\r', '\n')
                if (rest.isNotEmpty()) onLine(rest)
                sb.clear()
            }
        }
    }

    // ---------------- 信息解析 ----------------

    suspend fun fetchInfo(context: Context, settings: AppSettings, url: String): VideoInfo? =
        withContext(Dispatchers.IO) {
            val appCtx = context.applicationContext
            if (!ensureReady(appCtx, settings)) return@withContext null
            val dir = ytdlpDir(appCtx)
            val run = runDir(appCtx)
            run.mkdirs()

            val args = mutableListOf<String>()
            val cfg = configFile(appCtx)
            if (cfg.exists()) {
                args += "--config-locations"; args += cfg.absolutePath
            }
            args += "--no-warnings"; args += "--no-playlist"; args += "-J"
            args += url

            val ts = System.currentTimeMillis()
            val argfile = File(run, "info-$ts.json")
            val outFile = File(run, "info-$ts.out")
            val errFile = File(run, "info-$ts.err")
            val rcFile = File(run, "info-$ts.rc")

            val cfgJson = JSONObject().apply {
                put("argv", JSONArray(args))
                put("extra_path", dir.absolutePath)
                put("cwd", resolveDownloadDir(appCtx, settings).absolutePath)
                put("redirect", JSONObject().apply {
                    put("stdout", outFile.absolutePath)
                    put("stderr", errFile.absolutePath)
                })
                put("rcfile", rcFile.absolutePath)
            }
            argfile.writeText(cfgJson.toString())

            try {
                PythonBridge.ensureStarted(appCtx)
                PythonBridge.run(argfile, dir)
            } catch (e: Exception) {
                return@withContext null
            }

            val text = runCatching { outFile.readText() }.getOrNull() ?: return@withContext null
            val jsonLine = text.lineSequence().lastOrNull { it.trimStart().startsWith("{") }
                ?: return@withContext null
            try {
                val j = JSONObject(jsonLine)
                VideoInfo(
                    title = j.optString("title"),
                    uploader = j.optString("uploader").ifBlank { j.optString("channel") },
                    duration = if (j.has("duration")) {
                        j.optDouble("duration", 0.0).takeIf { it > 0 }
                    } else null,
                    thumbnail = j.optString("thumbnail").ifBlank { null },
                    viewCount = if (j.has("view_count")) j.optLong("view_count") else null,
                    width = j.optInt("width").takeIf { it > 0 },
                    height = j.optInt("height").takeIf { it > 0 },
                )
            } catch (e: Exception) {
                null
            }
        }
}
