Full offline translation models live here.

Model direction directories:
- en-zh/
- zh-en/
- en-ja/
- ja-en/
- ko-en/
- en-ko/

Each directory must contain:
- manifest.json
- encoder_model_quantized.onnx
- decoder_model_quantized.onnx
- tokenizer.json
- vocab.json
- config.json
- generation_config.json
- license.txt

Current bundled ONNX directions:
- en-zh
- zh-en
- en-ja
- ja-en
- ko-en

The en-ko direction keeps the same directory contract, but a public ONNX export
has not been bundled yet. Chinese/Japanese routes are handled through English
when a direct model is not present.

The source repository should not commit large model files directly.
Use Git LFS or attach the full offline APK/model pack to GitHub Releases.
