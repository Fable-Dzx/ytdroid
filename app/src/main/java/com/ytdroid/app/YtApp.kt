package com.ytdroid.app

import android.app.Application
import com.ytdroid.app.engine.YtDlpEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class YtApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // 后台完成引擎初始化与自动更新，不阻塞 UI
        appScope.launch {
            YtDlpEngine.ensureReady(this@YtApp)
        }
    }
}
