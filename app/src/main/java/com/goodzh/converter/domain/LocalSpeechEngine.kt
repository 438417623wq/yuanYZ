package com.goodzh.converter.domain

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

class LocalSpeechEngine(@Suppress("unused") private val context: Context) {
    suspend fun transcribe(wavFile: File, modelDir: File, onProgress: (Float) -> Unit): String =
        withContext(Dispatchers.IO) {
            val result = StringBuilder()
            Model(modelDir.absolutePath).use { model ->
                Recognizer(model, 16_000f).use { recognizer ->
                    val total = wavFile.length().coerceAtLeast(1L)
                    wavFile.inputStream().buffered().use { input ->
                        if (input.skip(44) < 44) error("音频文件格式错误")
                        val buffer = ByteArray(4096)
                        var readTotal = 44L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            readTotal += read
                            if (recognizer.acceptWaveForm(buffer, read)) {
                                parseText(recognizer.result)?.let { result.appendLine(it) }
                            }
                            onProgress(0.95f + ((readTotal.toFloat() / total) * 0.05f).coerceIn(0f, 0.05f))
                        }
                        parseText(recognizer.finalResult)?.let { result.appendLine(it) }
                    }
                }
            }
            result.toString().trim()
        }

}

private fun parseText(json: String): String? =
    runCatching { JSONObject(json).optString("text").trim() }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
