package com.goodzh.converter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.goodzh.converter.data.ConversionRecord
import com.goodzh.converter.data.ConversionStatus
import com.goodzh.converter.data.ConversionType
import com.goodzh.converter.domain.OcrConverter
import com.goodzh.converter.domain.PerformanceMode
import com.goodzh.converter.domain.SherpaSpeechEngine
import com.goodzh.converter.domain.SpeechLanguage
import com.goodzh.converter.domain.SpeechTranscriptChunk
import com.goodzh.converter.domain.VideoAudioExtractor
import com.goodzh.converter.domain.displayName
import com.goodzh.converter.domain.profile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class BackgroundConversionState(
    val running: Boolean = false,
    val progress: Float = 0f,
    val message: String = "",
    val resultText: String = ""
)

object BackgroundConversionBus {
    private val _state = MutableStateFlow(BackgroundConversionState())
    val state: StateFlow<BackgroundConversionState> = _state.asStateFlow()

    fun update(state: BackgroundConversionState) {
        _state.value = state
    }
}

class BackgroundConversionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var cancelRequested = false

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelRequested = true
                activeJob?.cancel(CancellationException("用户已取消后台转换"))
                BackgroundConversionBus.update(BackgroundConversionState(message = "后台转换已取消"))
                stopSelf()
            }
            ACTION_START -> {
                cancelRequested = false
                startForeground(NOTIFICATION_ID, buildNotification("正在准备后台转换", 0f, true))
                activeJob?.cancel()
                activeJob = scope.launch {
                    runTask(intent)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        activeJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private suspend fun runTask(intent: Intent) {
        acquireWakeLock()
        val app = application as GoodZhApp
        val repository = app.repository
        val type = intent.getStringExtra(EXTRA_TYPE)?.let { ConversionType.valueOf(it) } ?: return finishService()
        val uri = Uri.parse(intent.getStringExtra(EXTRA_URI) ?: return finishService())
        val outputUri = intent.getStringExtra(EXTRA_OUTPUT_URI)?.let(Uri::parse)
        val audioFormat = intent.getStringExtra(EXTRA_AUDIO_FORMAT) ?: "m4a"
        val language = intent.getStringExtra(EXTRA_LANGUAGE)?.let { SpeechLanguage.valueOf(it) } ?: SpeechLanguage.Mandarin
        val mode = intent.getStringExtra(EXTRA_PERFORMANCE)?.let { PerformanceMode.valueOf(it) } ?: PerformanceMode.Balanced
        val title = displayName(this, uri)
        val pending = ConversionRecord(
            type = type,
            title = title,
            sourceUri = uri.toString(),
            outputUri = outputUri?.toString().orEmpty(),
            resultText = "",
            status = ConversionStatus.Pending,
            message = "后台转换中"
        )

        repository.save(pending)
        updateProgress("后台转换已开始：$title", 0.01f)

        runCatching {
            when (type) {
                ConversionType.Video -> convertVideo(uri, language, mode)
                ConversionType.Audio -> convertAudio(uri, language, mode)
                ConversionType.Image -> convertImage(uri, mode)
                ConversionType.VideoAudio -> convertVideoAudio(uri, outputUri ?: error("请选择音频保存位置"), audioFormat)
            }
        }.onSuccess { result ->
            repository.update(
                pending.copy(
                    resultText = result.text,
                    outputUri = result.outputUri.ifBlank { pending.outputUri },
                    segmentsJson = result.segmentsJson,
                    status = ConversionStatus.Success,
                    message = ""
                )
            )
            updateProgress("后台转换完成：$title", 1f, result.text, running = false)
            notifyDone("转换完成", title)
        }.onFailure { error ->
            val message = if (error is CancellationException || cancelRequested) "后台转换已取消" else "后台转换失败：${error.message ?: "未知错误"}"
            repository.update(
                pending.copy(
                    resultText = "",
                    status = ConversionStatus.Failed,
                    message = message
                )
            )
            updateProgress(message, 0f, running = false)
            notifyDone("转换未完成", message)
        }

        finishService()
    }

    private suspend fun convertVideo(uri: Uri, language: SpeechLanguage, mode: PerformanceMode): ConversionResult {
        val speech = SherpaSpeechEngine(this)
        if (!speech.isBundledModelReady()) error("缺少 sherpa-onnx 精准模型，请重新安装新版 APK")
        val extractor = VideoAudioExtractor(this)
        val wav = extractor.extractToWav(uri) { value ->
            checkCancelled()
            updateProgress("正在后台提取视频音频 ${(value * 45).toInt()}%", value * 0.45f)
        }
        return try {
            val chunks = speech.transcribeChunks(wav, language, mode.profile) { value ->
                checkCancelled()
                updateProgress("正在后台识别${language.label} ${(45 + value * 53).toInt()}%", 0.45f + value * 0.53f)
            }
            val text = joinRecognizedChunks(chunks, language).ifBlank { "未识别到语音文字" }
            val durationMs = videoDurationMs(this, uri)
            val segments = buildSubtitleSegmentsFromChunks(chunks, language, durationMs)
                .ifEmpty { buildSubtitleSegments(text, durationMs) }
            ConversionResult(text = text, segmentsJson = segments.toJsonString())
        } finally {
            wav.delete()
        }
    }

    private suspend fun convertAudio(uri: Uri, language: SpeechLanguage, mode: PerformanceMode): ConversionResult {
        val speech = SherpaSpeechEngine(this)
        if (!speech.isBundledModelReady()) error("缺少 sherpa-onnx 语音模型，请重新安装新版 APK")
        val extractor = VideoAudioExtractor(this)
        val wav = extractor.extractToWav(uri) { value ->
            checkCancelled()
            updateProgress("正在后台处理音频 ${(value * 45).toInt()}%", value * 0.45f)
        }
        return try {
            val text = speech.transcribe(wav, language, mode.profile) { value ->
                checkCancelled()
                updateProgress("正在后台识别${language.label} ${(45 + value * 53).toInt()}%", 0.45f + value * 0.53f)
            }
            ConversionResult(text = normalizeRecognizedText(text, language).ifBlank { "未识别到语音文字" })
        } finally {
            wav.delete()
        }
    }

    private suspend fun convertImage(uri: Uri, mode: PerformanceMode): ConversionResult {
        val text = OcrConverter(this).recognize(uri, mode.profile)
        updateProgress("图片后台识别完成", 1f)
        return ConversionResult(text = text.ifBlank { "未识别到文字" })
    }

    private suspend fun convertVideoAudio(uri: Uri, outputUri: Uri, format: String): ConversionResult {
        val extractor = VideoAudioExtractor(this)
        val normalizedFormat = format.lowercase(Locale.US)
        when (normalizedFormat) {
            "wav" -> extractor.exportToWav(uri, outputUri) { value ->
                checkCancelled()
                updateProgress("正在导出 WAV 音频 ${(value * 100).toInt()}%", value)
            }
            else -> extractor.exportToM4a(uri, outputUri) { value ->
                checkCancelled()
                updateProgress("正在导出 M4A 音频 ${(value * 100).toInt()}%", value)
            }
        }
        return ConversionResult(
            text = "音频导出完成：${normalizedFormat.uppercase(Locale.US)}",
            outputUri = outputUri.toString()
        )
    }

    private fun checkCancelled() {
        if (cancelRequested) throw CancellationException("用户已取消后台转换")
    }

    private fun updateProgress(message: String, progress: Float, resultText: String = "", running: Boolean = true) {
        val safeProgress = progress.coerceIn(0f, 1f)
        BackgroundConversionBus.update(
            BackgroundConversionState(
                running = running,
                progress = safeProgress,
                message = message,
                resultText = resultText
            )
        )
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(message, safeProgress, running))
    }

    private fun notifyDone(title: String, text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification("$title：$text", 1f, false))
    }

    private fun buildNotification(text: String, progress: Float, running: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        builder
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("本地全能转文字")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(running)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)

        if (running) {
            builder.setProgress(100, (progress.coerceIn(0f, 1f) * 100).toInt(), false)
            val cancelIntent = PendingIntent.getService(
                this,
                1,
                Intent(this, BackgroundConversionService::class.java).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消", cancelIntent)
        } else {
            builder.setProgress(0, 0, false)
        }
        return builder.build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "后台转换",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GoodZh:BackgroundConversion").apply {
            setReferenceCounted(false)
            acquire(2 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun finishService() {
        releaseWakeLock()
        stopForeground(false)
        stopSelf()
    }

    companion object {
        private const val CHANNEL_ID = "background_conversion"
        private const val NOTIFICATION_ID = 7301
        private const val ACTION_START = "com.goodzh.converter.action.START_BACKGROUND_CONVERSION"
        private const val ACTION_CANCEL = "com.goodzh.converter.action.CANCEL_BACKGROUND_CONVERSION"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_URI = "uri"
        private const val EXTRA_OUTPUT_URI = "output_uri"
        private const val EXTRA_AUDIO_FORMAT = "audio_format"
        private const val EXTRA_LANGUAGE = "language"
        private const val EXTRA_PERFORMANCE = "performance"

        fun start(
            context: Context,
            type: ConversionType,
            uri: Uri,
            language: SpeechLanguage?,
            performanceMode: PerformanceMode,
            outputUri: Uri? = null,
            audioFormat: String? = null
        ) {
            val intent = Intent(context, BackgroundConversionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TYPE, type.name)
                putExtra(EXTRA_URI, uri.toString())
                outputUri?.let { putExtra(EXTRA_OUTPUT_URI, it.toString()) }
                audioFormat?.let { putExtra(EXTRA_AUDIO_FORMAT, it) }
                language?.let { putExtra(EXTRA_LANGUAGE, it.name) }
                putExtra(EXTRA_PERFORMANCE, performanceMode.name)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

private data class ConversionResult(
    val text: String,
    val segmentsJson: String = "",
    val outputUri: String = ""
)

private data class ServiceSubtitleSegment(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val text: String
)

private fun joinRecognizedChunks(chunks: List<SpeechTranscriptChunk>, language: SpeechLanguage): String {
    val separator = when (language) {
        SpeechLanguage.English,
        SpeechLanguage.OtherDialect -> " "
        SpeechLanguage.Mandarin,
        SpeechLanguage.Japanese,
        SpeechLanguage.Korean,
        SpeechLanguage.Cantonese -> ""
    }
    return chunks
        .map { normalizeRecognizedText(it.text, language) }
        .filter { it.isNotBlank() }
        .joinToString(separator)
        .trim()
}

private fun buildSubtitleSegmentsFromChunks(
    chunks: List<SpeechTranscriptChunk>,
    language: SpeechLanguage,
    durationMs: Long
): List<ServiceSubtitleSegment> {
    val segments = ArrayList<ServiceSubtitleSegment>()
    var nextId = System.currentTimeMillis()
    chunks.forEach { chunk ->
        val text = normalizeRecognizedText(chunk.text, language)
        if (text.isBlank()) return@forEach
        val chunkStart = chunk.startMs.coerceAtLeast(0L)
        val rawEnd = chunk.endMs.coerceAtLeast(chunkStart + 800L)
        val chunkEnd = if (durationMs > 0L) rawEnd.coerceAtMost(durationMs) else rawEnd
        if (chunkEnd <= chunkStart) return@forEach
        val localSegments = buildSubtitleSegments(text, chunkEnd - chunkStart)
        localSegments.forEach { segment ->
            val start = (chunkStart + segment.startMs).coerceAtLeast(chunkStart)
            val maxEnd = chunkEnd.coerceAtLeast(start + 1L)
            val end = (chunkStart + segment.endMs).coerceIn(start + 1L, maxEnd)
            segments.add(segment.copy(id = nextId++, startMs = start, endMs = end))
        }
    }
    return segments.sortedBy { it.startMs }
}

private fun buildSubtitleSegments(text: String, durationMs: Long): List<ServiceSubtitleSegment> {
    val sentences = text
        .replace(Regex("([。！？!?])"), "$1\n")
        .replace(Regex("([，,；;])"), "$1\n")
        .lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .ifEmpty { listOf(text.trim()).filter { it.isNotBlank() } }
    if (sentences.isEmpty()) return emptyList()

    val safeDuration = durationMs.takeIf { it > 0 } ?: (sentences.size * 2_500L)
    var cursor = 0L
    return sentences.mapIndexed { index, sentence ->
        val remaining = sentences.size - index
        val end = if (remaining == 1) {
            safeDuration
        } else {
            (cursor + (safeDuration - cursor) / remaining).coerceAtLeast(cursor + 800L)
        }
        ServiceSubtitleSegment(
            id = System.currentTimeMillis() + index,
            startMs = cursor,
            endMs = end.coerceAtMost(safeDuration),
            text = sentence
        ).also {
            cursor = it.endMs
        }
    }
}

private fun List<ServiceSubtitleSegment>.toJsonString(): String {
    val array = JSONArray()
    forEach { segment ->
        array.put(
            JSONObject()
                .put("id", segment.id)
                .put("startMs", segment.startMs)
                .put("endMs", segment.endMs)
                .put("text", segment.text)
        )
    }
    return array.toString()
}

private fun normalizeRecognizedText(text: String, language: SpeechLanguage): String =
    when (language) {
        SpeechLanguage.English,
        SpeechLanguage.OtherDialect -> text.replace(Regex("\\s+"), " ").trim()
        SpeechLanguage.Mandarin,
        SpeechLanguage.Japanese,
        SpeechLanguage.Korean,
        SpeechLanguage.Cantonese -> text.replace(Regex("\\s+"), "").trim()
    }

private fun videoDurationMs(context: Context, uri: Uri): Long =
    runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
    }.getOrDefault(0L)
