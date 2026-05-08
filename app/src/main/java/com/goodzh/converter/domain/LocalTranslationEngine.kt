package com.goodzh.converter.domain

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.goodzh.converter.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.min

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

data class TranslationDirectionStatus(
    val id: String,
    val source: TranslationLanguage,
    val target: TranslationLanguage,
    val missingFiles: List<String>
) {
    val ready: Boolean get() = missingFiles.isEmpty()
    val label: String get() = "${source.label} -> ${target.label}"
}

data class TranslationModelStatus(
    val fullOfflineBuild: Boolean,
    val runtimeReady: Boolean,
    val directions: List<TranslationDirectionStatus>
) {
    val readyDirections: List<String> get() = directions.filter { it.ready }.map { it.id }
    val missingDirections: List<String> get() = directions.filterNot { it.ready }.map { it.id }
    val allModelFilesReady: Boolean get() = directions.all { it.ready }
    val ready: Boolean get() = fullOfflineBuild && runtimeReady && allModelFilesReady

    fun route(source: TranslationLanguage, target: TranslationLanguage): List<TranslationDirectionStatus> {
        if (source == target) return emptyList()
        val direct = "${source.code}-${target.code}"
        val directionsById = directions.associateBy { it.id }
        if (direct in directionsById) return listOfNotNull(directionsById[direct])

        listOf("en", "zh").forEach { pivot ->
            if (source.code != pivot && target.code != pivot) {
                val first = directionsById["${source.code}-$pivot"]
                val second = directionsById["$pivot-${target.code}"]
                if (first != null || second != null) return listOfNotNull(first, second)
            }
        }
        return emptyList()
    }

    fun canTranslate(source: TranslationLanguage, target: TranslationLanguage): Boolean {
        val route = route(source, target)
        return source != target && fullOfflineBuild && runtimeReady && route.isNotEmpty() && route.all { it.ready }
    }

    fun unavailableReason(source: TranslationLanguage, target: TranslationLanguage): String {
        if (source == target) return "原语言和目标语言相同，无需翻译。"
        if (!fullOfflineBuild) return "当前是标准版，需要打包 fullOffline 版并内置翻译模型。"
        val route = route(source, target)
        if (route.isEmpty()) return "当前没有 ${source.label} -> ${target.label} 的本地模型路线。"
        val missingRoute = route.filterNot { it.ready }
        if (missingRoute.isNotEmpty()) {
            return missingRoute.joinToString("\n") { direction ->
                "${direction.id} 缺少：${direction.missingFiles.joinToString("、")}"
            }
        }
        if (!runtimeReady) return "ONNX Runtime 未能初始化，无法执行本地翻译推理。"
        return ""
    }
}

data class TranslationResult(
    val sourceText: String,
    val translatedText: String
)

class LocalTranslationEngine(private val context: Context) {
    fun status(): TranslationModelStatus {
        val directions = requiredDirections.map { inspectDirection(it) }
        return TranslationModelStatus(
            fullOfflineBuild = BuildConfig.FULL_OFFLINE_TRANSLATION,
            runtimeReady = isRuntimeReady(),
            directions = directions
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

        val status = status()
        if (!status.canTranslate(source, target)) {
            error(status.unavailableReason(source, target))
        }

        var currentLines = lines
        val route = status.route(source, target)
        var completed = 0
        val totalWork = (route.size * lines.size).coerceAtLeast(1)
        route.forEach { direction ->
            createDirectionRuntime(direction.id).use { runtime ->
                currentLines = currentLines.map { line ->
                    val translated = if (line.isBlank()) line else runtime.translate(line)
                    completed += 1
                    onProgress(completed.toFloat() / totalWork)
                    translated
                }
            }
        }

        lines.zip(currentLines).map { (sourceLine, translatedLine) ->
            TranslationResult(sourceLine, translatedLine)
        }
    }

    private fun createDirectionRuntime(direction: String): MarianDirectionRuntime {
        val modelDir = materializeDirectionAssets(direction)
        val env = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
        }
        return MarianDirectionRuntime(
            env = env,
            encoder = env.createSession(File(modelDir, encoderModelFile).absolutePath, options),
            decoder = env.createSession(File(modelDir, decoderModelFile).absolutePath, options),
            tokenizer = MarianTokenizer.fromFiles(
                tokenizerFile = File(modelDir, "tokenizer.json"),
                vocabFile = File(modelDir, "vocab.json")
            ),
            config = MarianGenerationConfig.fromFiles(
                configFile = File(modelDir, "config.json"),
                generationConfigFile = File(modelDir, "generation_config.json")
            )
        )
    }

    private fun materializeDirectionAssets(direction: String): File {
        val outputDir = File(context.noBackupFilesDir, "$assetRoot/$direction")
        val marker = File(outputDir, ".asset-version")
        val assetVersion = "${BuildConfig.VERSION_CODE}-${BuildConfig.VERSION_NAME}"
        if (marker.exists() && marker.readText() != assetVersion) {
            outputDir.deleteRecursively()
        }
        outputDir.mkdirs()

        runtimeFiles.forEach { fileName ->
            val output = File(outputDir, fileName)
            if (output.isFile && output.length() > 0L) return@forEach
            context.assets.open("$assetRoot/$direction/$fileName").use { input ->
                output.outputStream().buffered().use { outputStream ->
                    input.copyTo(outputStream)
                }
            }
        }
        marker.writeText(assetVersion)
        return outputDir
    }

    private fun inspectDirection(direction: String): TranslationDirectionStatus {
        val files = if (BuildConfig.FULL_OFFLINE_TRANSLATION) assetFiles(direction) else emptySet()
        val missing = requiredFiles.filterNot { it in files }
        val languages = direction.split("-")
        return TranslationDirectionStatus(
            id = direction,
            source = languageByCode.getValue(languages[0]),
            target = languageByCode.getValue(languages[1]),
            missingFiles = missing
        )
    }

    private fun assetFiles(direction: String): Set<String> {
        val base = "$assetRoot/$direction"
        return runCatching { context.assets.list(base)?.toSet().orEmpty() }.getOrDefault(emptySet())
    }

    private fun isRuntimeReady(): Boolean =
        runCatching {
            OrtEnvironment.getEnvironment()
            true
        }.getOrDefault(false)

    companion object {
        private const val assetRoot = "translation-models"
        private const val encoderModelFile = "encoder_model_quantized.onnx"
        private const val decoderModelFile = "decoder_model_quantized.onnx"
        val requiredDirections = listOf("en-zh", "zh-en", "en-ja", "ja-en", "ko-en", "en-ko")
        private val requiredFiles = listOf(
            "manifest.json",
            encoderModelFile,
            decoderModelFile,
            "tokenizer.json",
            "vocab.json",
            "config.json",
            "generation_config.json",
            "license.txt"
        )
        private val runtimeFiles = listOf(
            encoderModelFile,
            decoderModelFile,
            "tokenizer.json",
            "vocab.json",
            "config.json",
            "generation_config.json"
        )
        private val languageByCode = TranslationLanguage.entries.associateBy { it.code }
    }
}

private class MarianDirectionRuntime(
    private val env: OrtEnvironment,
    private val encoder: OrtSession,
    private val decoder: OrtSession,
    private val tokenizer: MarianTokenizer,
    private val config: MarianGenerationConfig
) : AutoCloseable {
    fun translate(text: String): String {
        val encoded = tokenizer.encode(
            text = text,
            maxTokens = config.maxInputTokens,
            targetPrefixToken = config.targetPrefixToken?.let { tokenizer.tokenId(it) }
        )
        val inputIds = encoded.map { it.toLong() }.toLongArray()
        val attentionMask = LongArray(inputIds.size) { 1L }

        OnnxTensor.createTensor(env, arrayOf(inputIds)).use { inputTensor ->
            OnnxTensor.createTensor(env, arrayOf(attentionMask)).use { attentionTensor ->
                encoder.run(
                    mapOf(
                        "input_ids" to inputTensor,
                        "attention_mask" to attentionTensor
                    )
                ).use { encoderResult ->
                    val encoderHiddenStates = encoderResult.get(0) as OnnxTensor
                    val generated = mutableListOf(config.decoderStartTokenId)

                    repeat(config.maxOutputTokens) {
                        val decoderIds = generated.map { it.toLong() }.toLongArray()
                        OnnxTensor.createTensor(env, arrayOf(decoderIds)).use { decoderInput ->
                            decoder.run(
                                mapOf(
                                    "encoder_attention_mask" to attentionTensor,
                                    "input_ids" to decoderInput,
                                    "encoder_hidden_states" to encoderHiddenStates
                                )
                            ).use { decoderResult ->
                                @Suppress("UNCHECKED_CAST")
                                val logits = (decoderResult.get(0) as OnnxTensor).value as Array<Array<FloatArray>>
                                val nextToken = logits[0][generated.lastIndex].argMaxBlocked(config.blockedTokenIds)
                                if (nextToken == config.eosTokenId) return tokenizer.decode(generated.drop(1))
                                generated += nextToken
                            }
                        }
                    }
                    return tokenizer.decode(generated.drop(1))
                }
            }
        }
    }

    override fun close() {
        decoder.close()
        encoder.close()
    }
}

private data class MarianGenerationConfig(
    val decoderStartTokenId: Int,
    val eosTokenId: Int,
    val padTokenId: Int,
    val maxInputTokens: Int,
    val maxOutputTokens: Int,
    val targetPrefixToken: String?
) {
    val blockedTokenIds: Set<Int> = setOf(padTokenId, decoderStartTokenId).filter { it >= 0 }.toSet()

    companion object {
        fun fromFiles(configFile: File, generationConfigFile: File): MarianGenerationConfig {
            val config = JSONObject(configFile.readText())
            val generation = JSONObject(generationConfigFile.readText())
            val targetLang = config.optString("_name_or_path", "")
                .substringAfterLast("-")
                .lowercase(Locale.US)
            return MarianGenerationConfig(
                decoderStartTokenId = generation.optInt("decoder_start_token_id", config.optInt("decoder_start_token_id", 65000)),
                eosTokenId = generation.optInt("eos_token_id", config.optInt("eos_token_id", 0)),
                padTokenId = generation.optInt("pad_token_id", config.optInt("pad_token_id", 65000)),
                maxInputTokens = config.optInt("max_position_embeddings", 512).coerceIn(32, 512),
                maxOutputTokens = min(generation.optInt("max_length", 128), 128).coerceAtLeast(16),
                targetPrefixToken = if (targetLang == "zh") ">>cmn_Hans<<" else null
            )
        }
    }
}

private class MarianTokenizer(
    private val tokenToId: Map<String, Int>,
    private val idToToken: List<String>,
    private val tokenScores: Map<String, Double>,
    private val maxPieceLength: Int
) {
    private val eosId = tokenToId["</s>"] ?: 0
    private val unkId = tokenToId["<unk>"] ?: 1
    private val padId = tokenToId["<pad>"] ?: 65000
    private val specialTokenIds = setOf(eosId, unkId, padId)
    private val unknownScore = -100.0

    fun tokenId(token: String): Int? = tokenToId[token]

    fun encode(text: String, maxTokens: Int, targetPrefixToken: Int?): IntArray {
        val ids = ArrayList<Int>()
        targetPrefixToken?.let { ids += it }
        text.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .forEach { word ->
                ids += encodePiece("▁$word")
            }
        val limited = ids.take((maxTokens - 1).coerceAtLeast(1)).toMutableList()
        limited += eosId
        return limited.toIntArray()
    }

    fun decode(ids: List<Int>): String {
        val raw = buildString {
            ids.asSequence()
                .takeWhile { it != eosId }
                .filterNot { it in specialTokenIds }
                .mapNotNull { idToToken.getOrNull(it) }
                .filterNot { it.startsWith(">>") && it.endsWith("<<") }
                .forEach { append(it) }
        }
        return raw.replace("▁", " ")
            .replace(Regex("\\s+([,.!?;:，。！？、）])"), "$1")
            .replace(Regex("([（])\\s+"), "$1")
            .trim()
    }

    private fun encodePiece(piece: String): List<Int> {
        if (piece.isEmpty()) return emptyList()
        val bestScores = DoubleArray(piece.length + 1) { Double.NEGATIVE_INFINITY }
        val bestPrevious = IntArray(piece.length + 1) { -1 }
        val bestTokenIds = IntArray(piece.length + 1) { unkId }
        bestScores[0] = 0.0

        for (start in piece.indices) {
            if (bestScores[start].isInfinite()) continue
            val maxEnd = min(piece.length, start + maxPieceLength)
            for (end in start + 1..maxEnd) {
                val token = piece.substring(start, end)
                val score = tokenScores[token] ?: continue
                val candidate = bestScores[start] + score
                if (candidate > bestScores[end]) {
                    bestScores[end] = candidate
                    bestPrevious[end] = start
                    bestTokenIds[end] = tokenToId[token] ?: unkId
                }
            }

            val fallbackEnd = start + 1
            val fallbackScore = bestScores[start] + unknownScore
            if (fallbackScore > bestScores[fallbackEnd]) {
                bestScores[fallbackEnd] = fallbackScore
                bestPrevious[fallbackEnd] = start
                bestTokenIds[fallbackEnd] = unkId
            }
        }

        val reversed = ArrayList<Int>()
        var cursor = piece.length
        while (cursor > 0) {
            reversed += bestTokenIds[cursor]
            cursor = bestPrevious[cursor].takeIf { it >= 0 } ?: break
        }
        return reversed.asReversed()
    }

    companion object {
        fun fromFiles(tokenizerFile: File, vocabFile: File): MarianTokenizer {
            val vocab = JSONObject(vocabFile.readText())
            val tokenToId = mutableMapOf<String, Int>()
            vocab.keys().forEach { token ->
                tokenToId[token] = vocab.getInt(token)
            }
            val idToToken = MutableList((tokenToId.values.maxOrNull() ?: 0) + 1) { "" }
            tokenToId.forEach { (token, id) ->
                if (id in idToToken.indices) idToToken[id] = token
            }

            val modelVocab = JSONObject(tokenizerFile.readText())
                .getJSONObject("model")
                .getJSONArray("vocab")
            val tokenScores = HashMap<String, Double>(modelVocab.length())
            var maxLength = 1
            for (index in 0 until modelVocab.length()) {
                val item = modelVocab.getJSONArray(index)
                val token = item.getString(0)
                tokenScores[token] = item.getDouble(1)
                maxLength = maxOf(maxLength, token.length)
            }
            return MarianTokenizer(
                tokenToId = tokenToId,
                idToToken = idToToken,
                tokenScores = tokenScores,
                maxPieceLength = min(maxLength, 64)
            )
        }
    }
}

private fun FloatArray.argMaxBlocked(blocked: Set<Int>): Int {
    var bestIndex = 0
    var bestValue = Float.NEGATIVE_INFINITY
    for (index in indices) {
        if (index in blocked) continue
        val value = this[index]
        if (value > bestValue) {
            bestValue = value
            bestIndex = index
        }
    }
    return bestIndex
}

private fun Double.isInfinite(): Boolean =
    this == Double.NEGATIVE_INFINITY || this == Double.POSITIVE_INFINITY
