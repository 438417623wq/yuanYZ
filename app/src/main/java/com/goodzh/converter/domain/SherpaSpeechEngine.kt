package com.goodzh.converter.domain

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil

class SherpaSpeechEngine(private val context: Context) {
    fun isBundledModelReady(): Boolean = runCatching {
        context.assets.open("$assetRoot/model.int8.onnx").close()
        context.assets.open("$assetRoot/tokens.txt").close()
        true
    }.getOrDefault(false)

    suspend fun transcribe(
        wavFile: File,
        language: SpeechLanguage,
        profile: PerformanceProfile,
        onProgress: (Float) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val recognizer = createRecognizer(language.modelCode, profile.speechThreads)
        try {
            val parts = ArrayList<String>()
            val dataBytes = (wavFile.length() - 44L).coerceAtLeast(0L)
            if (dataBytes == 0L) return@withContext ""

            val chunkBytes = profile.speechChunkSeconds.coerceAtLeast(5) * 16_000 * 2
            val totalChunks = ceil(dataBytes.toDouble() / chunkBytes).toInt().coerceAtLeast(1)
            wavFile.inputStream().buffered().use { input ->
                if (input.skip(44) < 44) error("音频文件格式错误")
                val buffer = ByteArray(chunkBytes)
                var readBytes = 0L
                var chunkIndex = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    readBytes += read
                    chunkIndex += 1
                    val samples = pcm16BytesToFloat(buffer, read)
                    if (samples.isNotEmpty()) {
                        val stream = recognizer.createStream()
                        try {
                            stream.acceptWaveform(samples, 16_000)
                            recognizer.decode(stream)
                            val text = recognizer.getResult(stream).text.cleanupSenseVoiceText()
                            if (text.isNotBlank()) parts.add(text)
                        } finally {
                            stream.release()
                        }
                    }
                    val chunkProgress = chunkIndex.toFloat() / totalChunks
                    val byteProgress = readBytes.toFloat() / dataBytes
                    onProgress(0.95f + (maxOf(chunkProgress, byteProgress) * 0.05f).coerceIn(0f, 0.05f))
                }
            }
            parts.joinToString("").ifBlank { parts.joinToString("\n") }
        } finally {
            recognizer.release()
        }
    }

    private fun createRecognizer(language: String, threads: Int): OfflineRecognizer {
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
            modelConfig = OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = "$assetRoot/model.int8.onnx",
                    language = language,
                    useInverseTextNormalization = true
                ),
                tokens = "$assetRoot/tokens.txt",
                numThreads = threads.coerceIn(1, 4),
                provider = "cpu"
            ),
            decodingMethod = "greedy_search"
        )
        return OfflineRecognizer(context.assets, config)
    }

    companion object {
        private const val assetRoot = "sherpa-models/sense-voice"
    }
}

private fun pcm16BytesToFloat(bytes: ByteArray, read: Int): FloatArray {
    val usable = read - (read % 2)
    if (usable <= 0) return FloatArray(0)
    val shorts = ByteBuffer.wrap(bytes, 0, usable).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
    return FloatArray(shorts.remaining()) { shorts.get().toFloat() / Short.MAX_VALUE }
}

private fun String.cleanupSenseVoiceText(): String =
    replace("<|zh|>", "")
        .replace("<|en|>", "")
        .replace("<|yue|>", "")
        .replace("<|ja|>", "")
        .replace("<|ko|>", "")
        .replace("<|nospeech|>", "")
        .replace("<|Speech|>", "")
        .replace("<|withitn|>", "")
        .replace("<|woitn|>", "")
        .trim()
