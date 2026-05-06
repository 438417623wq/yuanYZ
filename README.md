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

## 许可证与第三方声明

本仓库中由项目作者原创的代码和文档默认保留所有权利，未经许可不得二次分发或商业化使用。第三方组件、SDK 和模型分别遵循其原始许可证或模型协议。

- 项目授权：[`LICENSE`](LICENSE)
- 第三方组件与模型声明：[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)
- 隐私说明：[`PRIVACY.md`](PRIVACY.md)

## 隐私说明

项目目标是在安卓设备本地完成视频、语音和图片内容识别。项目代码不主动把用户选择的音频、视频、图片或识别文本上传到项目作者服务器。部分第三方 SDK 或系统服务可能根据其条款进行模型更新、兼容性信息或性能指标通信，详见 [`PRIVACY.md`](PRIVACY.md)。
