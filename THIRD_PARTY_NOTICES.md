# 第三方组件与模型声明

本项目集成了第三方开源库、模型和 SDK。以下信息用于归属声明和合规参考，不构成法律意见。发布 APK、二次分发或商业化前，请再次核对上游最新许可证、模型协议和服务条款。

## 语音识别与模型

### Vosk Android

- 用途：备用本地语音识别能力。
- 依赖：`com.alphacephei:vosk-android:0.3.47`
- 上游：https://github.com/alphacep/vosk-api
- 许可证：Apache License 2.0
- 注意：分发时应保留上游版权、许可证和声明。

### Vosk Chinese small model

- 用途：移动端中文本地语音识别模型。
- 文件：`app/src/main/assets/vosk-model/vosk-model-small-cn-0.22/`
- 上游模型页：https://alphacephei.com/vosk/models
- 模型：`vosk-model-small-cn-0.22`
- 许可证：Apache License 2.0
- 注意：官方模型页标注该中文小模型为 Apache 2.0。二次分发或商业化时应保留模型名称、来源、版权和许可证信息。

### sherpa-onnx

- 用途：本地离线语音识别运行时。
- 文件：`app/libs/sherpa-onnx-1.13.0.aar`
- 上游：https://github.com/k2-fsa/sherpa-onnx
- 许可证：Apache License 2.0
- 注意：分发 AAR 或包含其二进制产物时，应保留 Apache 2.0 许可证和上游声明。

### SenseVoice / FunASR model

- 用途：本地离线语音识别模型。
- 文件：`app/src/main/assets/sherpa-models/sense-voice/`
- 模型来源说明：目录内模型由 SenseVoice / FunASR 相关模型转换得到。
- SenseVoice 上游：https://github.com/FunAudioLLM/SenseVoice
- FunASR 模型协议：https://github.com/modelscope/FunASR/blob/main/MODEL_LICENSE
- 许可证/协议：FunASR Model Open Source License Agreement 1.1
- 注意：该协议允许在协议范围内使用、复制、修改和分享 FunASR 模型，但要求注明来源和作者信息，并保留相关模型名称。商业化、改名发布、模型再训练、模型转换后二次分发时，应保留来源和协议，并建议在正式商业发布前向上游或法律顾问确认授权边界。

## 图片文字识别

### Google ML Kit Text Recognition

- 用途：本地 OCR 图片文字识别。
- 依赖：
  - `com.google.mlkit:text-recognition:16.0.1`
  - `com.google.mlkit:text-recognition-chinese:16.0.1`
- 条款与隐私：https://developers.google.com/ml-kit/terms
- 注意：Google ML Kit 文档说明，ML Kit API 对输入数据的处理在设备端完成，不会把输入和输出发送给 Google；但 ML Kit API 可能联系 Google 服务器获取修复、模型更新和硬件兼容信息，也可能发送 API 性能和使用指标。发布应用时，应在隐私政策和应用市场数据披露中说明。

## Android 与 Kotlin 依赖

### AndroidX / Jetpack Compose

- 用途：Android 应用框架、界面和生命周期组件。
- 主要依赖：`androidx.core`、`androidx.activity`、`androidx.compose`、`androidx.lifecycle`、`androidx.navigation`
- 上游：https://developer.android.com/jetpack/androidx
- 常见许可证：Apache License 2.0
- 注意：以实际依赖包随附的许可证为准。

### AndroidX Media3 / ExoPlayer

- 用途：字幕校对界面的视频播放、横竖屏播放、全屏播放、进度拖动和倍速控制。
- 依赖：
  - `androidx.media3:media3-exoplayer:1.5.1`
  - `androidx.media3:media3-ui:1.5.1`
- 上游：https://developer.android.com/media/media3
- 常见许可证：Apache License 2.0
- 注意：以实际依赖包随附的许可证为准。

### Kotlin 与 kotlinx.coroutines

- 用途：Kotlin 语言运行时和协程支持。
- 上游：https://kotlinlang.org/
- 常见许可证：Apache License 2.0
- 注意：以实际依赖包随附的许可证为准。

## 分发建议

- 在 GitHub 仓库、APK 关于页或设置页中保留本文件。
- 不要把第三方模型描述为项目作者自研或独占拥有。
- 如果发布付费版、广告版、企业版或应用市场版本，请保留第三方组件和模型归属声明。
- 如果替换、裁剪、量化、转换或再训练模型，请记录来源、转换过程和修改日期。
- APK 建议通过 GitHub Releases 或应用市场分发，不建议提交到 Git 历史。
