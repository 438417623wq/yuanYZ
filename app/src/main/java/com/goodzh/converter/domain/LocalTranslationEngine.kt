package com.goodzh.converter.domain

import android.content.Context
import com.goodzh.converter.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class TranslationLanguage(val code: String, val label: String) {
    Chinese("zh", "中文"),
    English("en", "英文"),
    Japanese("ja", "日文"),
    Korean("ko", "韩文")
}

enum class TranslationApplyMode(val label: String) {
    TranslationOnly("只看译文"),
    Bilingual("双语字幕")
}

data class TranslationModelStatus(
    val fullOfflineBuild: Boolean,
    val readyDirections: List<String>,
    val missingDirections: List<String>
) {
    val ready: Boolean get() = missingDirections.isEmpty()
}

data class TranslationResult(
    val sourceText: String,
    val translatedText: String
)

class LocalTranslationEngine(private val context: Context) {
    fun status(): TranslationModelStatus {
        val ready = requiredDirections.filter { isDirectionReady(it) }
        return TranslationModelStatus(
            fullOfflineBuild = BuildConfig.FULL_OFFLINE_TRANSLATION,
            readyDirections = ready,
            missingDirections = requiredDirections - ready.toSet()
        )
    }

    suspend fun translateLines(
        source: TranslationLanguage,
        target: TranslationLanguage,
        lines: List<String>,
        onProgress: (Float) -> Unit
    ): List<TranslationResult> = withContext(Dispatchers.IO) {
        if (source == target) {
            return@withContext lines.mapIndexed { index, line ->
                onProgress((index + 1).toFloat() / lines.size.coerceAtLeast(1))
                TranslationResult(line, line)
            }
        }

        val route = routeDirections(source, target)
        route.forEach { direction ->
            if (!isDirectionReady(direction)) {
                error("缺少内置翻译模型：$direction。请把模型文件、tokenizer.json 与 license.txt 放入 fullOffline assets 后重新打包。")
            }
        }

        // The APK-side model packaging and routing are ready. The real ONNX/Marian
        // runtime should be wired here after the converted int8 model files are present.
        error("已检测到翻译模型目录，但当前 APK 尚未接入 Marian/ONNX 推理运行时。")
    }

    private fun routeDirections(source: TranslationLanguage, target: TranslationLanguage): List<String> {
        val direct = "${source.code}-${target.code}"
        if (direct in requiredDirections) return listOf(direct)
        return listOf("${source.code}-zh", "zh-${target.code}")
    }

    private fun isDirectionReady(direction: String): Boolean {
        if (!BuildConfig.FULL_OFFLINE_TRANSLATION) return false
        val base = "translation-models/$direction"
        val files = runCatching { context.assets.list(base)?.toSet().orEmpty() }.getOrDefault(emptySet())
        return "manifest.json" in files &&
            "tokenizer.json" in files &&
            "license.txt" in files &&
            modelFileNames.any { it in files }
    }

    companion object {
        val requiredDirections = listOf("en-zh", "zh-en", "ja-zh", "zh-ja", "ko-zh", "zh-ko")
        private val modelFileNames = setOf("model.onnx", "model.int8.onnx", "model.ort", "model.bin")
    }
}
