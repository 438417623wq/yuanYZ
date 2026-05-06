# 智能转文字

一个以安卓 App 为主的本地转文字工具，支持在安卓手机上进行视频转文字、语音转文字和图片转文字。识别流程尽量在设备本地完成，减少音视频、图片和文字内容上传到云端的隐私风险。

## 功能

- 视频转文字：从本地视频中提取音频并转写为文本
- 语音转文字：使用本地语音识别模型完成离线转写
- 图片转文字：使用 OCR 识别图片中的文字
- 本地处理：核心识别资源随 App 或本地模型文件提供
- 隐私保护：用户文件默认不上传到远程服务器

## 技术栈

- Kotlin
- Android Jetpack Compose
- Vosk Android
- sherpa-onnx
- Google ML Kit Text Recognition

## 本地构建

1. 使用 Android Studio 打开项目根目录。
2. 确认已安装 Android SDK 35 和 JDK 17。
3. 同步 Gradle。
4. 运行 `app` 模块，或执行：

```powershell
.\gradlew assembleDebug
```

## 大文件说明

本项目包含本地语音识别模型和 AAR 二进制依赖，已通过 Git LFS 配置管理。APK、构建产物、临时下载包和崩溃日志不会提交到源码仓库。

如果要发布可安装包，建议在 GitHub 的 Releases 页面上传 APK，而不是把 APK 放进 Git 提交历史。

## 隐私说明

项目目标是在安卓设备本地完成视频、语音和图片内容识别。请在发布前检查第三方 SDK 的实际行为、权限声明和隐私政策，确保与应用说明一致。
