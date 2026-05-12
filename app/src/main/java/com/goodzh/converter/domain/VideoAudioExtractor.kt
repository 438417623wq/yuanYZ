package com.goodzh.converter.domain

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

class VideoAudioExtractor(private val context: Context) {
    suspend fun exportToWav(
        inputUri: Uri,
        outputUri: Uri,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val wav = extractToWav(inputUri) { value -> onProgress(value * 0.92f) }
        try {
            copyFileToUri(wav, outputUri)
            onProgress(1f)
            wav
        } finally {
            wav.delete()
        }
    }

    suspend fun exportToM4a(
        inputUri: Uri,
        outputUri: Uri,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "video-audio-${System.currentTimeMillis()}.m4a")
        extractAacTrackToM4a(inputUri, temp, onProgress)
        try {
            copyFileToUri(temp, outputUri)
            onProgress(1f)
            temp
        } finally {
            temp.delete()
        }
    }

    suspend fun extractToWav(uri: Uri, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val output = File(context.cacheDir, "video-audio-${System.currentTimeMillis()}.wav")
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)

        val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: error("这个视频没有可识别的音频轨道")

        extractor.selectTrack(trackIndex)
        val inputFormat = extractor.getTrackFormat(trackIndex)
        val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("无法识别音频格式")
        val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
            inputFormat.getLong(MediaFormat.KEY_DURATION)
        } else {
            0L
        }
        val decoder = MediaCodec.createDecoderByType(mime)
        val writer = WavPcmWriter(output)

        try {
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var resampler = StreamingResampler(sampleRate, channelCount)

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)
                        val sampleSize = extractor.readSampleData(inputBuffer ?: ByteBuffer.allocate(0), 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = decoder.outputFormat
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        resampler = StreamingResampler(sampleRate, channelCount)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        val outputBuffer = decoder.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && info.size > 0) {
                            outputBuffer.position(info.offset)
                            outputBuffer.limit(info.offset + info.size)
                            writer.writePcm16(resampler.resample(outputBuffer))
                        }
                        if (durationUs > 0L) onProgress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 0.9f))
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            writer.finish()
            onProgress(0.95f)
            output
        } finally {
            runCatching { decoder.stop() }
            decoder.release()
            extractor.release()
        }
    }

    private fun extractAacTrackToM4a(uri: Uri, output: File, onProgress: (Float) -> Unit) {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: error("这个视频没有可导出的音频轨道")

        val inputFormat = extractor.getTrackFormat(trackIndex)
        val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("无法识别音频格式")
        if (mime != "audio/mp4a-latm") {
            extractor.release()
            error("当前视频音轨不是 AAC，无法快速导出 M4A，请选择 WAV 导出")
        }

        val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
            inputFormat.getLong(MediaFormat.KEY_DURATION)
        } else {
            0L
        }
        val bufferSize = if (inputFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            inputFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(256 * 1024)
        } else {
            256 * 1024
        }

        output.parentFile?.mkdirs()
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            extractor.selectTrack(trackIndex)
            val muxerTrack = muxer.addTrack(inputFormat)
            muxer.start()

            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val info = MediaCodec.BufferInfo()
            while (true) {
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                info.set(
                    0,
                    sampleSize,
                    extractor.sampleTime.coerceAtLeast(0L),
                    extractor.sampleFlags
                )
                muxer.writeSampleData(muxerTrack, buffer, info)
                if (durationUs > 0L) {
                    onProgress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 0.96f))
                }
                extractor.advance()
            }
            onProgress(0.98f)
        } finally {
            runCatching { muxer.stop() }
            muxer.release()
            extractor.release()
        }
    }

    private fun copyFileToUri(file: File, uri: Uri) {
        context.contentResolver.openOutputStream(uri, "w")?.use { output ->
            file.inputStream().buffered().use { input ->
                input.copyTo(output)
            }
        } ?: error("无法写入选择的保存位置")
    }
}

private class StreamingResampler(
    private val sourceRate: Int,
    private val channels: Int
) {
    private val targetRate = 16_000
    private var tail = FloatArray(0)
    private var cursor = 0.0

    fun resample(buffer: ByteBuffer): ShortArray {
        val mono = decodeMono(buffer)
        val source = FloatArray(tail.size + mono.size)
        tail.copyInto(source)
        mono.copyInto(source, tail.size)
        val step = sourceRate.toDouble() / targetRate
        val out = ArrayList<Short>()

        while (cursor + 1 < source.size) {
            val index = cursor.toInt()
            val fraction = cursor - index
            val value = source[index] + ((source[index + 1] - source[index]) * fraction).toFloat()
            out.add((value.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort())
            cursor += step
        }

        val keepFrom = cursor.toInt().coerceAtMost(source.size)
        tail = source.copyOfRange(keepFrom, source.size)
        cursor -= keepFrom
        return out.toShortArray()
    }

    private fun decodeMono(buffer: ByteBuffer): FloatArray {
        val view = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val frames = view.remaining() / channels.coerceAtLeast(1)
        val mono = FloatArray(frames)
        for (frame in 0 until frames) {
            var sum = 0
            for (channel in 0 until channels.coerceAtLeast(1)) {
                sum += view.get().toInt()
            }
            mono[frame] = (sum / channels.coerceAtLeast(1)).toFloat() / Short.MAX_VALUE
        }
        return mono
    }
}

private class WavPcmWriter(private val file: File) {
    private val raf = RandomAccessFile(file, "rw")
    private var bytesWritten = 0

    init {
        raf.setLength(0)
        raf.write(ByteArray(44))
    }

    fun writePcm16(samples: ShortArray) {
        val bytes = ByteArray(samples.size * 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(samples)
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
