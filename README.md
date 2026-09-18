# ytDroid — yt-dlp GUI for Android

一个基于 [yt-dlp](https://github.com/yt-dlp/yt-dlp) 的安卓下载器图形界面。内核为官方 yt-dlp（纯 Python），通过 Chaquopy 内嵌 Python 3.13 运行，**无需 root、无需 Termux**。

- 🎨 Material 3 设计，支持动态取色（Android 12+）/ 深色 / 浅色
- ⚙️ 配置齐全：格式串、音频提取、字幕、缩略图、元数据、代理、限速、Cookie、自定义参数……
- 🧩 自由度高：每行一个参数的「附加参数」、自定义输出模板、自定义 yt-dlp / ffmpeg / 配置源
- 🔄 自动更新：启动时自动检查并更新 **yt-dlp 引擎**与**配置文件**（本仓库 `config/yt-dlp.conf`）
- ⚡ 前台服务下载 + 实时进度通知；队列 + 并行（1–3）；完成后复制到公共下载目录
- 🤖 GitHub Actions 自动编译，每次 push 产出可安装的签名 APK

## 架构

```
┌─────────────────────────────────────────────┐
│                    ytDroid (Kotlin + Compose)│
│  主页(解析/下载) · 任务(进度/管理) · 引擎(更新) · 设置 │
└──────────────┬──────────────────────────────┘
               │ ProcessBridge (runner.py + 参数 JSON)
               ▼
┌─────────────────────────────────────────────┐
│       Chaquopy 内嵌 Python 3.13 (同进程)        │
│   filesDir/ytdlp/yt_dlp  ← 内置, 启动自动更新    │
│   filesDir/bin/ffmpeg     ← 可选, 可配置源      │
│   filesDir/config/yt-dlp.conf ← 仓库同步        │
└─────────────────────────────────────────────┘
```

- **yt-dlp 引擎**：应用内置官方源码包（`app/src/main/assets/ytdlp.zip`）。启动时若开启了「自动更新」，会从官方 GitHub 下载最新 `yt-dlp.tar.gz` 并热替换，无需安装应用更新。
- **ffmpeg**：用于合并音视频流 / 提取音频 / 嵌入字幕缩略图。官方不再发布安卓二进制，应用将其做成**可配置**：在「引擎」页填 zip 直链安装，或从本地 zip 文件安装；未安装时自动降级为单文件格式下载。

## 编译（GitHub Actions）

推送到 `main` 分支即自动编译，产物为 Actions 页面的 `ytdroid-apk` 构件：

```bash
git push origin main
```

工作流见 [`.github/workflows/build.yml`](.github/workflows/build.yml)：

1. JDK 17 + Gradle 8.9
2. 生成一次性签名密钥（keytool），release 签名打包
3. 上传 APK 构件

版本号自动取 CI run 号（`versionCode=run_number`, `versionName=1.0.run_number`）。

## 本地编译

```bash
# 需要 JDK 17
./gradlew assembleRelease
# 产物: app/build/outputs/apk/release/app-release.apk
```

## 安装

侧载 APK（`设置 → 安全 → 允许安装未知来源`）。最低 Android 8.0（API 26），支持 arm64-v8a / armeabi-v7a。

## 配置文件

仓库内 [`config/yt-dlp.conf`](config/yt-dlp.conf) 是应用的默认配置文件：每行一个参数，等价于命令行参数；应用「设置」页生成的参数优先级更高。修改并推送该文件后，用户端启动时（开启「自动更新配置文件」）即可同步。

## 设置参考

| 分组 | 说明 |
|---|---|
| 下载 | 下载目录（自定义需「所有文件访问」）、输出模板、并行数、公共目录复制、播放列表 |
| 格式与转码 | 格式串 `-f`、仅音频、音频格式/码率、元数据/缩略图嵌入 |
| 字幕 | 语言选择、自动字幕、嵌入字幕 |
| 网络与容错 | 重试、并发分片、超时、限速、代理、UA、Cookie、Referer |
| 高级 | 附加参数（每行一个，追加在命令末尾） |
| 外观 | 主题、动态取色 |

## 常见问题

- **「解析失败」**：确认「引擎」页 yt-dlp 已就绪；部分站点需要 Cookie（设置 → 网络与容错 → Cookies 文件路径）或代理。
- **下载后文件在哪里**：默认在应用私有目录（`Android/data/com.ytdroid.app/files/Download/ytdroid`），完成后自动复制一份到系统 `Download/ytDroid`（Android 10+）。
- **ffmpeg 未安装**：合并高画质音视频、提取 mp3 等需要 ffmpeg，在「引擎」页配置。
- **取消任务**：会向 yt-dlp 注入中断信号，已下载的分片保留，下次可续传（`--continue` 默认开启）。

## 技术栈

Kotlin 2.0 · Jetpack Compose (Material 3) · Chaquopy 17（Python 3.13）· DataStore · OkHttp · Coil · 前台服务

> Chaquopy 为个人/开源项目免费，商用与上架前请查阅 [Chaquopy 授权](https://chaquo.com/chaquopy/doc/current/license.html)。
