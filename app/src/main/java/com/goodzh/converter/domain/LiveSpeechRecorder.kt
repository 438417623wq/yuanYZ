package com.goodzh.converter.domain

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

sealed interface LiveSpeechRecorderEvent {
    data class Ready(val message: String) : LiveSpeechRecorderEvent
    data class Status(val message: String) : LiveSpeechRecorderEvent
    data class Level(val value: Float) : LiveSpeechRecorderEvent
    data class Result(val text: String) : LiveSpeechRecorderEvent
    data class Finished(val message: String) : LiveSpeechRecorderEvent
}

class LiveSpeechRecorder(private val context: Context) {
    suspend fun record(
        language: SpeechLanguage,
        profile: PerformanceProfile,
        noiseSuppression: Boolean,
        audioOutput: File?,
        continuous: () -> Boolean,
        onEvent: suspend (LiveSpeechRecorderEvent) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            error("缺少录音权限")
        }

        if (!isBundledModelReady()) {
            error("缺少内置语音模型，请重新安装新版 APK")
        }

        val recognizer = createRecognizer(language.modelCode, profile.speechThreads)
        var audioRecord: AudioRecord? = null
        var effects: AudioEffects? = null
        var writer: LiveWavWriter? = null
        try {
            writer = audioOutput?.let { LiveWavWriter(it) }
            audioRecord = createAudioRecord()
            effects = AudioEffects.create(audioRecord.audioSessionId, noiseSuppression)
            audioRecord.startRecording()
            if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                error("麦克风启动失败，可能被其它应用占用")
            }

            onEvent(
                LiveSpeechRecorderEvent.Ready(
                    if (noiseSuppression && effects.enabledCount > 0) {
                        "本地实时录音已启动，降噪已开启"
                    } else {
                        "本地实时录音已启动"
                    }
                )
            )

            val readBuffer = ShortArray(readBufferSamples())
            val chunkSamples = liveChunkSeconds(profile) * sampleRate
            val pending = ArrayList<Float>(chunkSamples + readBuffer.size)
            var hasDetectedVoice = false
            var hasAnyResult = false

            while (true) {
                currentCoroutineContext().ensureActive()
                val read = audioRecord.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
                if (read < 0) {
                    error(audioReadError(read))
                }
                if (read == 0) continue

                val rms = rms(readBuffer, read)
                onEvent(LiveSpeechRecorderEvent.Level(visualLevel(rms)))
                if (rms > voiceRmsThreshold) hasDetectedVoice = true
                val samples = shortBufferToFloat(readBuffer, read, noiseSuppression)
                writer?.writeFloat(samples)
                samples.forEach { pending.add(it) }

                if (pending.size >= chunkSamples) {
                    val text = recognizeSamples(recognizer, pending.toFloatArray())
                    pending.clear()
                    if (text.isNotBlank()) {
                        hasAnyResult = true
                        onEvent(LiveSpeechRecorderEvent.Result(text))
                    } else {
                        onEvent(
                            LiveSpeechRecorderEvent.Status(
                                if (hasDetectedVoice) "正在本地识别..." else "正在聆听，未检测到明显声音"
                            )
                        )
                    }
                    hasDetectedVoice = false
                    if (!continuous()) break
                }
            }

            if (pending.size >= sampleRate) {
                val text = recognizeSamples(recognizer, pending.toFloatArray())
                if (text.isNotBlank()) {
                    hasAnyResult = true
                    onEvent(LiveSpeechRecorderEvent.Result(text))
                }
            }

            onEvent(
                LiveSpeechRecorderEvent.Finished(
                    if (hasAnyResult) "单次录音已结束，内容可保存" else "单次录音结束，未识别到语音"
                )
            )
        } finally {
            runCatching { audioRecord?.stop() }
            writer?.finish()
            effects?.release()
            audioRecord?.release()
            recognizer.release()
        }
    }

    fun isBundledModelReady(): Boolean = runCatching {
        context.assets.open("$assetRoot/model.int8.onnx").close()
        context.assets.open("$assetRoot/tokens.txt").close()
        true
    }.getOrDefault(false)

    private fun recognizeSamples(recognizer: OfflineRecognizer, samples: FloatArray): String {
        if (samples.isEmpty()) return ""
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, sampleRate)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.cleanupSenseVoiceText()
        } finally {
            stream.release()
        }
    }

    private fun createRecognizer(language: String, threads: Int): OfflineRecognizer {
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = sampleRate, featureDim = 80),
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

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(): AudioRecord {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) error("当前设备不支持 16k 单声道录音")

        val bufferBytes = max(minBuffer, sampleRate * bytesPerSample)
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        )
        sources.forEach { source ->
            val record = runCatching {
                AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferBytes)
                    .build()
            }.getOrNull()
            if (record?.state == AudioRecord.STATE_INITIALIZED) return record
            record?.release()
        }
        error("录音设备初始化失败，请检查麦克风权限或是否被其它应用占用")
    }

    private fun readBufferSamples(): Int {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        return max(minBuffer / bytesPerSample, sampleRate / 4)
    }

    private fun liveChunkSeconds(profile: PerformanceProfile): Int =
        when {
            profile.speechThreads <= 1 -> 5
            profile.speechThreads >= 4 -> 3
            else -> 4
        }

    companion object {
        private const val assetRoot = "sherpa-models/sense-voice"
        private const val sampleRate = 16_000
        private const val bytesPerSample = 2
        private const val voiceRmsThreshold = 0.012f
    }
}

private class AudioEffects(
    private val noiseSuppressor: NoiseSuppressor?,
    private val gainControl: AutomaticGainControl?,
    private val echoCanceler: AcousticEchoCanceler?
) {
    val enabledCount: Int =
        listOf(noiseSuppressor, gainControl, echoCanceler).count { it?.enabled == true }

    fun release() {
        noiseSuppressor?.release()
        gainControl?.release()
        echoCanceler?.release()
    }

    companion object {
        fun create(audioSessionId: Int, enabled: Boolean): AudioEffects {
            if (!enabled) return AudioEffects(null, null, null)
            val noiseSuppressor = runCatching {
                if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(audioSessionId)?.apply { this.enabled = true } else null
            }.getOrNull()
            val gainControl = runCatching {
                if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(audioSessionId)?.apply { this.enabled = true } else null
            }.getOrNull()
            val echoCanceler = runCatching {
                if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(audioSessionId)?.apply { this.enabled = true } else null
            }.getOrNull()
            return AudioEffects(noiseSuppressor, gainControl, echoCanceler)
        }
    }
}

private fun shortBufferToFloat(buffer: ShortArray, read: Int, noiseSuppression: Boolean): FloatArray {
    val samples = FloatArray(read)
    for (index in 0 until read) {
        var sample = buffer[index].toFloat() / Short.MAX_VALUE
        if (noiseSuppression && abs(sample) < 0.006f) {
            sample *= 0.35f
        }
        samples[index] = sample.coerceIn(-1f, 1f)
    }
    return samples
}

private fun rms(buffer: ShortArray, read: Int): Float {
    if (read <= 0) return 0f
    var sum = 0.0
    for (index in 0 until read) {
        val value = buffer[index].toDouble() / Short.MAX_VALUE
        sum += value * value
    }
    return sqrt(sum / read).toFloat()
}

private fun visualLevel(rms: Float): Float {
    val normalized = ((rms - 0.002f) / 0.08f).coerceIn(0f, 1f)
    return sqrt(normalized.toDouble()).toFloat().coerceIn(0.04f, 1f)
}

private fun audioReadError(code: Int): String = when (code) {
    AudioRecord.ERROR_INVALID_OPERATION -> "录音读取失败，麦克风状态异常"
    AudioRecord.ERROR_BAD_VALUE -> "录音读取失败，设备返回了无效数据"
    AudioRecord.ERROR_DEAD_OBJECT -> "录音服务已断开，请重新开始录音"
    else -> "录音读取失败：$code"
}

private class LiveWavWriter(private val file: File) {
    private val raf: RandomAccessFile
    private var bytesWritten = 0

    init {
        file.parentFile?.mkdirs()
        raf = RandomAccessFile(file, "rw")
        raf.setLength(0)
        raf.write(ByteArray(44))
    }

    fun writeFloat(samples: FloatArray) {
        if (samples.isEmpty()) return
        val shorts = ShortArray(samples.size) {
            (samples[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort()
        }
        val bytes = ByteArray(shorts.size * 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts)
        raf.write(bytes)
        bytesWritten += bytes.size
    }

    fun finish() {
        raf.seek(0)
        raf.write("RIFF".toByteArray())
        raf.writeIntLe(36 + bytesWritten)
        raf.write("WAVEfmt ".toByteArray())
        raf.writeIntLe(16)
        raf.writeShortLe(1)
        raf.writeShortLe(1)
        raf.writeIntLe(16_000)
        raf.writeIntLe(16_000 * 2)
        raf.writeShortLe(2)
        raf.writeShortLe(16)
        raf.write("data".toByteArray())
        raf.writeIntLe(bytesWritten)
        raf.close()
    }
}

private fun RandomAccessFile.writeIntLe(value: Int) {
    write(byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte()))
}

private fun RandomAccessFile.writeShortLe(value: Int) {
    write(byteArrayOf(value.toByte(), (value shr 8).toByte()))
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
