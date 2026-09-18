package com.ytdroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytdroid.app.R
import com.ytdroid.app.engine.YtDlpEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold() {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val engineState by YtDlpEngine.state.collectAsStateWithLifecycle()

    val tabs = listOf(
        TabSpec("下载") { Icon(painterResource(R.drawable.ic_download), contentDescription = null) },
        TabSpec("任务") { Icon(Icons.Default.List, contentDescription = null) },
        TabSpec("引擎") { Icon(Icons.Default.Build, contentDescription = null) },
        TabSpec("设置") { Icon(Icons.Default.Settings, contentDescription = null) },
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ytDroid", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        EngineDot(ready = engineState.ready, updating = engineState.updating)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { i, tab ->
                    NavigationBarItem(
                        selected = tabIndex == i,
                        onClick = { tabIndex = i },
                        icon = tab.icon,
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tabIndex) {
                0 -> HomeScreen()
                1 -> DownloadsScreen()
                2 -> EngineScreen()
                3 -> SettingsScreen()
            }
        }
    }
}

private data class TabSpec(val label: String, val icon: @Composable () -> Unit)
