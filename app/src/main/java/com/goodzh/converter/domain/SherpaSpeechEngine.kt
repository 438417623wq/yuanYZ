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

class SherpaSpeechEngine(private val context: Context) {
    fun isBundledModelReady(): Boolean = runCatching {
        context.assets.open("$assetRoot/model.int8.onnx").close()
        context.assets.open("$assetRoot/tokens.txt").close()
        true
    }.getOrDefault(false)

    suspend fun transcribe(
        wavFile: File,
        mode: RecognitionMode,
        onProgress: (Float) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val language = when (mode) {
            RecognitionMode.MandarinAccurate -> "zh"
            RecognitionMode.DialectEnhanced -> "auto"
            RecognitionMode.FastFallback -> "zh"
        }
        val recognizer = createRecognizer(language)
        try {
            val samples = readPcm16WavAsFloat(wavFile)
            if (samples.isEmpty()) return@withContext ""

            val chunkSize = 16_000 * 25
            val parts = ArrayList<String>()
            var offset = 0
            while (offset < samples.size) {
                val end = (offset + chunkSize).coerceAtMost(samples.size)
                val chunk = samples.copyOfRange(offset, end)
                val stream = recognizer.createStream()
                try {
                    stream.acceptWaveform(chunk, 16_000)
                    recognizer.decode(stream)
                    val text = recognizer.getResult(stream).text.cleanupSenseVoiceText()
                    if (text.isNotBlank()) parts.add(text)
                } finally {
                    stream.release()
                }
                offset = end
                onProgress(0.95f + ((offset.toFloat() / samples.size) * 0.05f).coerceIn(0f, 0.05f))
            }
            parts.joinToString("").ifBlank { parts.joinToString("\n") }
        } finally {
            recognizer.release()
        }
    }

    private fun createRecognizer(language: String): OfflineRecognizer {
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
            modelConfig = OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = "$assetRoot/model.int8.onnx",
                    language = language,
                    useInverseTextNormalization = true
                ),
                tokens = "$assetRoot/tokens.txt",
                numThreads = 4,
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

private fun readPcm16WavAsFloat(file: File): FloatArray {
    val bytes = file.readBytes()
    if (bytes.size <= 44) return FloatArray(0)
    val audioBytes = bytes.copyOfRange(44, bytes.size)
    val shorts = ByteBuffer.wrap(audioBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
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
