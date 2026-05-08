# Full Offline Translation Plan

This project has two APK editions:

- `standard`: normal app package without bundled subtitle translation weights.
- `fullOffline`: full offline edition prepared for bundled local translation weights.

## Required Model Directions

The full offline edition keeps five model direction slots and can route through English when a direct model is not present:

- `en-zh`
- `zh-en`
- `en-ja`
- `ja-en`
- `ko-en`

Current bundled ONNX directions:

- `en-zh`: ready, using `onnx-community/opus-mt-en-zh`
- `zh-en`: ready, using `onnx-community/opus-mt-zh-en`
- `en-ja`: ready, using `Xenova/opus-mt-en-jap`
- `ja-en`: ready, using `Xenova/opus-mt-jap-en`
- `ko-en`: ready, using `Xenova/opus-mt-ko-en`

Korean is currently supported as a source language. Korean output is disabled in the app because no English-to-Korean ONNX model is bundled.

## Asset Layout

Put converted and quantized model files under:

```text
app/src/fullOffline/assets/translation-models/
├─ manifest.json
├─ en-zh/
│  ├─ manifest.json
│  ├─ encoder_model_quantized.onnx
│  ├─ decoder_model_quantized.onnx
│  ├─ tokenizer.json
│  ├─ vocab.json
│  ├─ config.json
│  ├─ generation_config.json
│  └─ license.txt
├─ zh-en/
├─ en-ja/
├─ ja-en/
└─ ko-en/
```

The APK-side code checks for `manifest.json`, `encoder_model_quantized.onnx`, `decoder_model_quantized.onnx`, `tokenizer.json`, `vocab.json`, `config.json`, `generation_config.json`, and `license.txt` in each directory.

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

The app exposes subtitle translation UI, model readiness checks, translated subtitle storage, and Marian/ONNX greedy decoding through ONNX Runtime Android. On first use, bundled model files are copied from APK assets to the app no-backup files directory so ONNX Runtime can open them by file path.

Current repository status:

- Full offline APK flavor: implemented.
- English <-> Chinese ONNX Marian model files: bundled.
- English <-> Japanese ONNX Marian model files: bundled.
- Korean -> English ONNX Marian model file: bundled.
- Marian tokenizer and greedy ONNX inference runtime: implemented.
- Git LFS patterns and GitHub Actions build workflow: implemented.
