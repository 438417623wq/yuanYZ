# Full Offline Translation Plan

This project now has two APK editions:

- `standard`: normal app package without bundled subtitle translation weights.
- `fullOffline`: full offline edition prepared for bundled local translation weights.

## Required Model Directions

The full offline edition uses Chinese as the pivot language and expects six model directions:

- `en-zh`
- `zh-en`
- `ja-zh`
- `zh-ja`
- `ko-zh`
- `zh-ko`

This covers English, Chinese, Japanese, and Korean translation. Non-Chinese pairs can be routed through Chinese.

## Asset Layout

Put converted and quantized model files under:

```text
app/src/fullOffline/assets/translation-models/
├─ manifest.json
├─ en-zh/
│  ├─ manifest.json
│  ├─ model.onnx
│  ├─ tokenizer.json
│  └─ license.txt
├─ zh-en/
├─ ja-zh/
├─ zh-ja/
├─ ko-zh/
└─ zh-ko/
```

The APK-side code checks for `manifest.json`, `tokenizer.json`, `license.txt`, and one model file in each directory.
Accepted model file names are `model.onnx`, `model.int8.onnx`, `model.ort`, or `model.bin`.

## Build Commands

Check missing model files:

```powershell
.\gradlew :app:checkFullOfflineTranslationModels
```

Build standard debug APK:

```powershell
.\gradlew :app:assembleStandardDebug
```

Build full offline debug APK:

```powershell
.\gradlew :app:assembleFullOfflineDebug
```

## GitHub Upload Strategy

Do not commit large model files directly to normal Git history.

Recommended:

- Source code stays in normal Git commits.
- Large model files use Git LFS patterns from `.gitattributes`.
- Full offline APKs are uploaded to GitHub Releases.
- Release notes must list model names, source links, licenses, and package size.

## Runtime Status

The app already exposes subtitle translation UI, model readiness checks, and storage for translated subtitles. The actual ONNX/Marian runtime must be wired after real converted int8 model files are placed in the fullOffline asset directories.

Current repository status:

- Full offline APK flavor: implemented.
- Model asset directories and manifest protocol: implemented.
- Git LFS patterns and GitHub Actions build workflow: implemented.
- Real translation model weights: not committed.
- Marian/ONNX text generation runtime: not wired yet.
