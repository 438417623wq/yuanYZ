package com.goodzh.converter

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goodzh.converter.data.ConversionRecord
import com.goodzh.converter.data.ConversionStatus
import com.goodzh.converter.data.ConversionType
import com.goodzh.converter.domain.ModelManager
import com.goodzh.converter.domain.OcrConverter
import com.goodzh.converter.domain.LocalTranslationEngine
import com.goodzh.converter.domain.LiveSpeechRecorder
import com.goodzh.converter.domain.LiveSpeechRecorderEvent
import com.goodzh.converter.domain.PerformanceMode
import com.goodzh.converter.domain.SpeechLanguage
import com.goodzh.converter.domain.SpeechTranscriptChunk
import com.goodzh.converter.domain.SherpaSpeechEngine
import com.goodzh.converter.domain.TranslationApplyMode
import com.goodzh.converter.domain.TranslationLanguage
import com.goodzh.converter.domain.TranslationModelStatus
import com.goodzh.converter.domain.VideoAudioExtractor
import com.goodzh.converter.domain.displayName
import com.goodzh.converter.domain.profile
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.io.File
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GoodZhTheme { ConverterApp() } }
    }
}

data class SubtitleSegment(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class VideoEditSession(
    val recordId: Long,
    val title: String,
    val videoUri: Uri,
    val segments: List<SubtitleSegment>,
    val translatedSegments: List<SubtitleSegment> = emptyList(),
    val subtitleDisplayMode: SubtitleDisplayMode = SubtitleDisplayMode.Original
)

data class TextNoteSession(
    val record: ConversionRecord?,
    val title: String,
    val text: String
)

enum class VideoOrientationMode(
    val label: String,
    val requestedOrientation: Int
) {
    Auto("自动", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
    Portrait("竖屏", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    Landscape("横屏", ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

    fun next(): VideoOrientationMode = when (this) {
        Auto -> Landscape
        Landscape -> Portrait
        Portrait -> Auto
    }
}

enum class SubtitleDisplayMode(val label: String) {
    Original("原文"),
    Translation("译文"),
    Bilingual("双语")
}

enum class VideoAudioExportFormat(
    val label: String,
    val extension: String,
    val mimeType: String
) {
    M4a("M4A/AAC 推荐", "m4a", "audio/mp4"),
    Wav("WAV 兼容", "wav", "audio/wav")
}

data class LiveSpeechUiState(
    val active: Boolean = false,
    val listening: Boolean = false,
    val paused: Boolean = false,
    val continuous: Boolean = true,
    val noiseSuppression: Boolean = true,
    val draft: String = "",
    val partial: String = "",
    val notice: String? = null
) {
    val preview: String get() = buildLiveSpeechPreview(draft, partial)
}

class ConverterViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as GoodZhApp
    private val ocr = OcrConverter(application)
    private val modelManager = ModelManager(application)
    private val audioExtractor = VideoAudioExtractor(application)
    private val sherpaEngine = SherpaSpeechEngine(application)
    private val liveSpeechRecorder = LiveSpeechRecorder(application)
    private val translationEngine = LocalTranslationEngine(application)
    private val settings = application.getSharedPreferences("goodzh_settings", Context.MODE_PRIVATE)
    private var liveSpeechJob: Job? = null
    private var liveSpeechAudioFile: File? = null
    private var liveSpeechAudioSaved = false
    @Volatile private var liveSpeechShouldRun = false
    @Volatile private var liveSpeechContinuous = true

    val records: StateFlow<List<ConversionRecord>> = app.repository.records.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    var currentText by mutableStateOf("")
        private set
    var statusText by mutableStateOf("请选择一个功能开始转换")
        private set
    var busy by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var modelReady by mutableStateOf(modelManager.hasModel())
        private set
    var sherpaReady by mutableStateOf(sherpaEngine.isBundledModelReady())
        private set
    var performanceMode by mutableStateOf(loadPerformanceMode())
        private set
    var translationStatus by mutableStateOf(translationEngine.status())
        private set
    var liveSpeechState by mutableStateOf(LiveSpeechUiState())
        private set
    var activeVideoEditor by mutableStateOf<VideoEditSession?>(null)
        private set
    var activeTextNote by mutableStateOf<TextNoteSession?>(null)
        private set

    init {
        viewModelScope.launch {
            modelReady = modelManager.ensureBundledModelInstalled()
            sherpaReady = sherpaEngine.isBundledModelReady()
            if (sherpaReady) {
                statusText = "本地语音模型已内置，可以开始转换"
            } else {
                statusText = "本地语音模型缺失，请重新安装新版 APK"
            }
        }
        viewModelScope.launch {
            BackgroundConversionBus.state.collect { state ->
                if (state.message.isBlank()) return@collect
                busy = state.running
                progress = state.progress
                statusText = state.message
                if (state.resultText.isNotBlank()) {
                    currentText = state.resultText
                }
            }
        }
    }

    private fun loadPerformanceMode(): PerformanceMode =
        runCatching {
            PerformanceMode.valueOf(settings.getString("performance_mode", PerformanceMode.Balanced.name) ?: PerformanceMode.Balanced.name)
        }.getOrDefault(PerformanceMode.Balanced)

    fun updatePerformanceMode(mode: PerformanceMode) {
        performanceMode = mode
        settings.edit().putString("performance_mode", mode.name).apply()
        statusText = "性能模式已切换为：${mode.label}"
    }

    fun updateLiveSpeechContinuous(enabled: Boolean) {
        liveSpeechContinuous = enabled
        liveSpeechState = liveSpeechState.copy(continuous = enabled)
    }

    fun updateLiveSpeechNoiseSuppression(enabled: Boolean) {
        liveSpeechState = liveSpeechState.copy(
            noiseSuppression = enabled,
            notice = if (enabled) "实时降噪已开启" else "实时降噪已关闭"
        )
    }

    fun waitForLiveSpeechPermission() {
        liveSpeechState = liveSpeechState.copy(
            active = true,
            listening = false,
            paused = false,
            notice = "等待录音权限"
        )
    }

    fun denyLiveSpeechPermission() {
        liveSpeechShouldRun = false
        liveSpeechState = liveSpeechState.copy(
            active = false,
            listening = false,
            paused = false,
            notice = "需要录音权限"
        )
    }

    fun startLiveSpeech() {
        if (!liveSpeechRecorder.isBundledModelReady()) {
            sherpaReady = false
            liveSpeechState = liveSpeechState.copy(
                active = false,
                listening = false,
                paused = false,
                notice = "缺少内置语音模型，请重新安装新版 APK"
            )
            return
        }

        liveSpeechJob?.cancel()
        if (!liveSpeechAudioSaved) {
            liveSpeechAudioFile?.delete()
        }
        liveSpeechAudioFile = createLiveSpeechAudioFile()
        liveSpeechAudioSaved = false
        liveSpeechShouldRun = true
        liveSpeechContinuous = liveSpeechState.continuous
        liveSpeechState = liveSpeechState.copy(
            active = true,
            listening = false,
            paused = false,
            partial = "",
            notice = "正在启动本地麦克风..."
        )

        val noiseSuppression = liveSpeechState.noiseSuppression
        val profile = performanceMode.profile
        val audioOutput = liveSpeechAudioFile
        liveSpeechJob = viewModelScope.launch {
            runCatching {
                liveSpeechRecorder.record(
                    language = SpeechLanguage.Mandarin,
                    profile = profile,
                    noiseSuppression = noiseSuppression,
                    audioOutput = audioOutput,
                    continuous = { liveSpeechShouldRun && liveSpeechContinuous },
                    onEvent = { event ->
                        withContext(Dispatchers.Main) {
                            handleLiveSpeechEvent(event)
                        }
                    }
                )
            }.onFailure { error ->
                if (error is CancellationException) return@launch
                liveSpeechShouldRun = false
                liveSpeechState = liveSpeechState.copy(
                    active = false,
                    listening = false,
                    paused = false,
                    notice = "实时录音失败：${error.message ?: "未知错误"}"
                )
            }
        }
    }

    private fun handleLiveSpeechEvent(event: LiveSpeechRecorderEvent) {
        when (event) {
            is LiveSpeechRecorderEvent.Ready -> {
                liveSpeechState = liveSpeechState.copy(
                    active = true,
                    listening = true,
                    paused = false,
                    notice = event.message
                )
            }
            is LiveSpeechRecorderEvent.Status -> {
                liveSpeechState = liveSpeechState.copy(
                    active = true,
                    listening = true,
                    paused = false,
                    notice = event.message
                )
            }
            is LiveSpeechRecorderEvent.Result -> {
                liveSpeechState = liveSpeechState.copy(
                    active = true,
                    listening = liveSpeechState.continuous,
                    paused = false,
                    draft = appendRecognizedSpeech(liveSpeechState.draft, event.text),
                    partial = "",
                    notice = null
                )
            }
            is LiveSpeechRecorderEvent.Finished -> {
                liveSpeechShouldRun = false
                liveSpeechState = liveSpeechState.copy(
                    active = false,
                    listening = false,
                    paused = false,
                    notice = event.message
                )
            }
        }
    }

    fun pauseLiveSpeech() {
        liveSpeechShouldRun = false
        liveSpeechJob?.cancel()
        liveSpeechJob = null
        liveSpeechState = liveSpeechState.copy(
            active = true,
            listening = false,
            paused = true,
            partial = "",
            notice = "已暂停，内容可保存"
        )
    }

    fun resumeLiveSpeech() {
        startLiveSpeech()
    }

    fun clearLiveSpeech() {
        liveSpeechShouldRun = false
        liveSpeechJob?.cancel()
        liveSpeechJob = null
        if (!liveSpeechAudioSaved) {
            liveSpeechAudioFile?.delete()
        }
        liveSpeechAudioFile = null
        liveSpeechAudioSaved = false
        liveSpeechState = LiveSpeechUiState(
            continuous = liveSpeechState.continuous,
            noiseSuppression = liveSpeechState.noiseSuppression
        )
    }

    fun saveLiveSpeech() {
        val text = liveSpeechState.preview
        if (text.isBlank()) {
            liveSpeechState = liveSpeechState.copy(notice = "暂无可保存内容")
            return
        }
        liveSpeechShouldRun = false
        liveSpeechJob?.cancel()
        liveSpeechJob = null
        val audioUri = liveSpeechAudioFile
            ?.takeIf { it.isFile && it.length() > 44L }
            ?.let { Uri.fromFile(it).toString() }
            .orEmpty()
        liveSpeechAudioSaved = audioUri.isNotBlank()
        saveSpeechResult(text, audioUri)
        liveSpeechState = liveSpeechState.copy(
            draft = text,
            partial = "",
            notice = "已保存到历史记录"
        )
    }

    private fun createLiveSpeechAudioFile(): File {
        val dir = File(getApplication<Application>().filesDir, "live-speech")
        dir.mkdirs()
        return File(dir, "live-speech-${System.currentTimeMillis()}.wav")
    }

    fun refreshTranslationStatus() {
        translationStatus = translationEngine.status()
    }

    fun prepareTranslationModel(source: TranslationLanguage, target: TranslationLanguage) {
        viewModelScope.launch {
            busy = true
            progress = 0f
            statusText = "正在启动本地翻译模型..."
            runCatching {
                translationEngine.prepareRoute(source, target) { value, message ->
                    progress = value
                    statusText = message
                }
            }.onSuccess {
                refreshTranslationStatus()
                progress = 1f
                statusText = "本地翻译模型已就绪：${source.label} -> ${target.label}"
            }.onFailure { error ->
                refreshTranslationStatus()
                statusText = "本地翻译模型启动失败：${error.message ?: "未知错误"}"
            }
            busy = false
        }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            busy = true
            progress = 0f
            statusText = "正在导入本地语音模型..."
            runCatching { modelManager.importZip(uri) }
                .onSuccess {
                    modelReady = true
                    statusText = "本地模型已就绪，可以选择视频转换"
                }
                .onFailure { error ->
                    modelReady = false
                    statusText = "模型导入失败：${error.message ?: "未知错误"}"
                }
            busy = false
        }
    }

    fun transcribeVideo(uri: Uri, language: SpeechLanguage) {
        if (!sherpaEngine.isBundledModelReady()) {
            sherpaReady = false
            statusText = "缺少内置精准模型，请重新安装新版 APK"
            return
        }
        busy = true
        progress = 0f
        currentText = ""
        statusText = "视频后台转换已开始，可切到后台继续"
        BackgroundConversionService.start(getApplication(), ConversionType.Video, uri, language, performanceMode)
    }

    fun recognizeImage(uri: Uri) {
        busy = true
        progress = 0f
        currentText = ""
        statusText = "图片后台识别已开始，可切到后台继续"
        BackgroundConversionService.start(getApplication(), ConversionType.Image, uri, null, performanceMode)
    }

    fun transcribeAudio(uri: Uri, language: SpeechLanguage) {
        if (!sherpaEngine.isBundledModelReady()) {
            sherpaReady = false
            statusText = "缺少内置语音模型，请重新安装新版 APK"
            return
        }
        busy = true
        progress = 0f
        currentText = ""
        statusText = "音频后台转换已开始，可切到后台继续"
        BackgroundConversionService.start(getApplication(), ConversionType.Audio, uri, language, performanceMode)
    }

    fun exportVideoAudio(videoUri: Uri, outputUri: Uri, format: VideoAudioExportFormat) {
        busy = true
        progress = 0f
        currentText = ""
        statusText = "视频转音频后台导出已开始，可切到后台继续"
        BackgroundConversionService.start(
            context = getApplication(),
            type = ConversionType.VideoAudio,
            uri = videoUri,
            language = null,
            performanceMode = performanceMode,
            outputUri = outputUri,
            audioFormat = format.extension
        )
    }

    fun saveSpeechResult(text: String, audioUri: String = "") {
        viewModelScope.launch {
            currentText = text
            statusText = if (audioUri.isNotBlank()) "语音和文字已保存" else "语音转文字完成"
            app.repository.save(
                ConversionRecord(
                    type = ConversionType.Audio,
                    title = "实时语音 ${timeLabel(System.currentTimeMillis())}",
                    sourceUri = audioUri,
                    resultText = text,
                    status = ConversionStatus.Success
                )
            )
        }
    }

    fun delete(record: ConversionRecord) {
        viewModelScope.launch {
            deleteLocalRecordAudio(record)
            app.repository.delete(record)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            records.value.forEach(::deleteLocalRecordAudio)
            app.repository.clear()
        }
    }

    private fun deleteLocalRecordAudio(record: ConversionRecord) {
        val uri = runCatching { Uri.parse(record.audioAssetUri()) }.getOrNull() ?: return
        if (uri.scheme != "file") return
        val file = File(uri.path ?: return)
        val filesDir = getApplication<Application>().filesDir.canonicalFile
        val target = runCatching { file.canonicalFile }.getOrNull() ?: return
        if (target.path.startsWith(filesDir.path)) {
            target.delete()
        }
    }

    fun openCurrentTextNote(text: String = currentText) {
        if (text.isBlank()) return
        activeTextNote = TextNoteSession(
            record = null,
            title = "转换结果",
            text = text
        )
    }

    fun openTextNote(record: ConversionRecord) {
        val text = record.resultText.ifBlank { record.message }
        if (text.isBlank()) return
        activeTextNote = TextNoteSession(
            record = record,
            title = record.title,
            text = text
        )
    }

    fun closeTextNote() {
        activeTextNote = null
    }

    fun saveTextNote(text: String) {
        val session = activeTextNote ?: return
        viewModelScope.launch {
            val record = session.record
            currentText = text
            statusText = "文本修改已保存"
            if (record != null) {
                app.repository.update(record.copy(resultText = text, message = if (record.resultText.isBlank()) "" else record.message))
                activeTextNote = session.copy(record = record.copy(resultText = text), text = text)
            } else {
                activeTextNote = session.copy(text = text)
            }
        }
    }

    fun openVideoEditor(record: ConversionRecord) {
        activeVideoEditor = record.toVideoEditSession() ?: return
    }

    fun closeVideoEditor() {
        activeVideoEditor = null
    }

    fun saveVideoSubtitles(segments: List<SubtitleSegment>) {
        val session = activeVideoEditor ?: return
        viewModelScope.launch {
            updateVideoSession(session, segments)
            val alignedTranslatedSegments = alignTranslatedSegments(segments, session.translatedSegments)
            activeVideoEditor = session.copy(
                segments = segments,
                translatedSegments = alignedTranslatedSegments,
                subtitleDisplayMode = if (alignedTranslatedSegments.isEmpty()) {
                    SubtitleDisplayMode.Original
                } else {
                    session.subtitleDisplayMode
                }
            )
        }
    }

    fun translateVideoSubtitles(
        source: TranslationLanguage,
        target: TranslationLanguage,
        mode: TranslationApplyMode,
        segments: List<SubtitleSegment>
    ) {
        val session = activeVideoEditor ?: return
        if (segments.isEmpty()) return
        viewModelScope.launch {
            busy = true
            progress = 0f
            statusText = "正在准备本地字幕翻译..."
            runCatching {
                translationEngine.translateLines(source, target, segments.map { it.text }) { value ->
                    progress = value
                    statusText = "正在本地翻译字幕 ${(value * 100).toInt()}%"
                }
            }.onSuccess { translatedLines ->
                val translatedSegments = segments.zip(translatedLines).map { (segment, result) ->
                    segment.copy(text = result.translatedText.ifBlank { result.sourceText })
                }
                val displayMode = when (mode) {
                    TranslationApplyMode.TranslationOnly -> SubtitleDisplayMode.Translation
                    TranslationApplyMode.Bilingual -> SubtitleDisplayMode.Bilingual
                }
                val translatedText = translatedSegments.joinToString("\n") { it.text }.trim()
                val existing = records.value.firstOrNull { it.id == session.recordId }
                app.repository.update(
                    existing?.copy(
                        translatedText = translatedText,
                        translatedSegmentsJson = translatedSegments.toJsonString(),
                        subtitleDisplayMode = displayMode.name,
                        message = ""
                    ) ?: ConversionRecord(
                        id = session.recordId,
                        type = ConversionType.Video,
                        title = session.title,
                        sourceUri = session.videoUri.toString(),
                        resultText = segments.joinToString("\n") { it.text }.trim(),
                        segmentsJson = segments.toJsonString(),
                        translatedText = translatedText,
                        translatedSegmentsJson = translatedSegments.toJsonString(),
                        subtitleDisplayMode = displayMode.name,
                        status = ConversionStatus.Success
                    )
                )
                activeVideoEditor = session.copy(
                    segments = segments,
                    translatedSegments = translatedSegments,
                    subtitleDisplayMode = displayMode
                )
                currentText = translatedText
                statusText = "字幕本地翻译完成：${source.label} -> ${target.label}"
                progress = 1f
            }.onFailure { error ->
                refreshTranslationStatus()
                statusText = "字幕翻译不可用：${error.message ?: "未知错误"}"
            }
            busy = false
        }
    }

    fun switchVideoEditor(record: ConversionRecord, saveCurrentSegments: List<SubtitleSegment>?) {
        val currentSession = activeVideoEditor
        val nextSession = record.toVideoEditSession() ?: return
        viewModelScope.launch {
            if (saveCurrentSegments != null && currentSession != null && currentSession.recordId != record.id) {
                updateVideoSession(currentSession, saveCurrentSegments)
            }
            activeVideoEditor = nextSession
            currentText = nextSession.segments.joinToString("\n") { it.text }.trim()
            statusText = "已切换视频：${nextSession.title}"
        }
    }

    fun replaceVideoEditorUri(uri: Uri, segments: List<SubtitleSegment>) {
        val session = activeVideoEditor ?: return
        viewModelScope.launch {
            val title = displayName(getApplication(), uri)
            val editedText = segments.joinToString("\n") { it.text }.trim()
            app.repository.update(
                ConversionRecord(
                    id = session.recordId,
                    type = ConversionType.Video,
                    title = title,
                    sourceUri = uri.toString(),
                    resultText = editedText,
                    segmentsJson = segments.toJsonString(),
                    status = ConversionStatus.Success
                )
            )
            activeVideoEditor = session.copy(
                title = title,
                videoUri = uri,
                segments = segments,
                translatedSegments = emptyList(),
                subtitleDisplayMode = SubtitleDisplayMode.Original
            )
        }
    }

    private suspend fun updateVideoSession(session: VideoEditSession, segments: List<SubtitleSegment>) {
        val editedText = segments.joinToString("\n") { it.text }.trim()
        val alignedTranslatedSegments = alignTranslatedSegments(segments, session.translatedSegments)
        val displayMode = if (alignedTranslatedSegments.isEmpty()) {
            SubtitleDisplayMode.Original
        } else {
            session.subtitleDisplayMode
        }
        val existing = records.value.firstOrNull { it.id == session.recordId }
        currentText = editedText
        statusText = "字幕修改已保存"
        app.repository.update(
            existing?.copy(
                title = session.title,
                sourceUri = session.videoUri.toString(),
                resultText = editedText,
                segmentsJson = segments.toJsonString(),
                translatedText = alignedTranslatedSegments.joinToString("\n") { it.text }.trim(),
                translatedSegmentsJson = alignedTranslatedSegments.toJsonString(),
                subtitleDisplayMode = displayMode.name,
                status = ConversionStatus.Success,
                message = ""
            ) ?: ConversionRecord(
                id = session.recordId,
                type = ConversionType.Video,
                title = session.title,
                sourceUri = session.videoUri.toString(),
                resultText = editedText,
                segmentsJson = segments.toJsonString(),
                translatedText = alignedTranslatedSegments.joinToString("\n") { it.text }.trim(),
                translatedSegmentsJson = alignedTranslatedSegments.toJsonString(),
                subtitleDisplayMode = displayMode.name,
                status = ConversionStatus.Success
            )
        )
    }

    override fun onCleared() {
        liveSpeechShouldRun = false
        liveSpeechJob?.cancel()
        super.onCleared()
    }
}

@Suppress("UNCHECKED_CAST")
class ConverterViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ConverterViewModel(application) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterApp(
    viewModel: ConverterViewModel = viewModel(
        factory = ConverterViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    var selected by remember { mutableStateOf(0) }
    var showAbout by remember { mutableStateOf(false) }
    val editorSession = viewModel.activeVideoEditor
    val noteSession = viewModel.activeTextNote
    val records by viewModel.records.collectAsState()
    val videoRecords = records.filter { it.isOpenableVideoRecord() }

    if (editorSession != null) {
        VideoSubtitleEditorScreen(
            session = editorSession,
            videoRecords = videoRecords,
            translationStatus = viewModel.translationStatus,
            translationBusy = viewModel.busy,
            onClose = viewModel::closeVideoEditor,
            onSave = viewModel::saveVideoSubtitles,
            onReplaceVideo = viewModel::replaceVideoEditorUri,
            onSwitchVideo = viewModel::switchVideoEditor,
            onPrepareTranslationModel = viewModel::prepareTranslationModel,
            onTranslate = viewModel::translateVideoSubtitles
        )
        return
    }

    if (noteSession != null) {
        TextNoteScreen(
            session = noteSession,
            onClose = viewModel::closeTextNote,
            onSave = viewModel::saveTextNote
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("本地全能转文字", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Default.Info, contentDescription = "关于")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF0F172A),
                    actionIconContentColor = Color(0xFF0F766E)
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = selected == 0,
                    onClick = { selected = 0 },
                    icon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                    label = { Text("转换") }
                )
                NavigationBarItem(
                    selected = selected == 1,
                    onClick = { selected = 1 },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text("历史") }
                )
            }
        }
    ) { padding ->
        if (selected == 0) WorkbenchScreen(viewModel, padding) else HistoryScreen(viewModel, padding)
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

@Composable
fun WorkbenchScreen(viewModel: ConverterViewModel, padding: PaddingValues) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val liveSpeechState = viewModel.liveSpeechState
    var showAudioActionDialog by remember { mutableStateOf(false) }
    var showVideoAudioFormatDialog by remember { mutableStateOf(false) }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingAudioUri by remember { mutableStateOf<Uri?>(null) }
    var pendingVideoAudioUri by remember { mutableStateOf<Uri?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::recognizeImage)
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            pendingAudioUri = it
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            pendingVideoUri = it
        }
    }
    val videoAudioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            pendingVideoAudioUri = it
            showVideoAudioFormatDialog = true
        }
    }
    val m4aDocumentCreator = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mp4")) { outputUri ->
        val videoUri = pendingVideoAudioUri
        if (videoUri != null && outputUri != null) {
            viewModel.exportVideoAudio(videoUri, outputUri, VideoAudioExportFormat.M4a)
        }
        pendingVideoAudioUri = null
    }
    val wavDocumentCreator = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { outputUri ->
        val videoUri = pendingVideoAudioUri
        if (videoUri != null && outputUri != null) {
            viewModel.exportVideoAudio(videoUri, outputUri, VideoAudioExportFormat.Wav)
        }
        pendingVideoAudioUri = null
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.startLiveSpeech()
        } else {
            viewModel.denyLiveSpeechPermission()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    fun startLiveSpeech() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startLiveSpeech()
        } else {
            viewModel.waitForLiveSpeechPermission()
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val liveSpeechPreview = liveSpeechState.preview
    val showLiveSpeechControls = liveSpeechState.active ||
        liveSpeechState.listening ||
        liveSpeechState.paused ||
        liveSpeechPreview.isNotBlank() ||
        liveSpeechState.notice != null
    val resultText = if (showLiveSpeechControls) liveSpeechPreview else viewModel.currentText
    val resultStatus = when {
        showLiveSpeechControls && liveSpeechState.notice != null -> liveSpeechState.notice.orEmpty()
        liveSpeechState.listening -> "正在本地聆听..."
        liveSpeechState.paused -> "实时录音已暂停"
        liveSpeechState.active && liveSpeechState.continuous -> "连续本地录音已开启"
        liveSpeechState.active -> "实时录音待继续"
        else -> viewModel.statusText
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7FAFC))
            .padding(padding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("视频、语音、图片统一转换，结果自动进入历史记录", color = Color(0xFF52616B))
        }
        item {
            PerformanceModeSelector(
                selected = viewModel.performanceMode,
                onSelect = viewModel::updatePerformanceMode
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolCard(
                    title = "视频转文字",
                    subtitle = "本地提取音频并用离线模型识别，不上传云端",
                    icon = Icons.Default.Videocam,
                    color = Color(0xFF155E75),
                    onClick = { videoPicker.launch(arrayOf("video/*")) }
                )
                ToolCard(
                    title = "语音转文字",
                    subtitle = "支持实时录音识别，也可导入音频文件",
                    icon = Icons.Default.Mic,
                    color = Color(0xFF0F766E),
                    onClick = { showAudioActionDialog = true }
                )
                ToolCard(
                    title = "视频转音频",
                    subtitle = "从视频中导出 M4A 或 WAV 音频文件",
                    icon = Icons.Default.UploadFile,
                    color = Color(0xFF2563EB),
                    onClick = { videoAudioPicker.launch(arrayOf("video/*")) }
                )
                ToolCard(
                    title = "图片转文字",
                    subtitle = "支持相册、截图等图片 OCR，自动保存结果",
                    icon = Icons.Default.Image,
                    color = Color(0xFF7C3AED),
                    onClick = { imagePicker.launch("image/*") }
                )
            }
        }
        if (showLiveSpeechControls) {
            item {
                LiveSpeechRecorderCard(
                    continuous = liveSpeechState.continuous,
                    noiseSuppression = liveSpeechState.noiseSuppression,
                    listening = liveSpeechState.listening,
                    paused = liveSpeechState.paused,
                    draft = liveSpeechPreview,
                    notice = liveSpeechState.notice,
                    onContinuousChange = viewModel::updateLiveSpeechContinuous,
                    onNoiseSuppressionChange = viewModel::updateLiveSpeechNoiseSuppression,
                    onStart = { startLiveSpeech() },
                    onPause = viewModel::pauseLiveSpeech,
                    onResume = { startLiveSpeech() },
                    onSave = viewModel::saveLiveSpeech,
                    onClear = viewModel::clearLiveSpeech
                )
            }
        }
        item {
            ResultPanel(
                busy = viewModel.busy,
                progress = viewModel.progress,
                status = resultStatus,
                text = resultText,
                onCopy = {
                    val text = resultText
                    if (text.isNotBlank()) clipboard.setText(AnnotatedString(text))
                },
                onShare = {
                    val text = resultText
                    if (text.isNotBlank()) shareText(context, text)
                },
                onOpenFull = {
                    val text = resultText
                    viewModel.openCurrentTextNote(text)
                }
            )
        }
    }

    if (showAudioActionDialog) {
        AudioActionDialog(
            onDismiss = { showAudioActionDialog = false },
            onLiveSpeech = {
                showAudioActionDialog = false
                startLiveSpeech()
            },
            onImportAudio = {
                showAudioActionDialog = false
                audioPicker.launch(arrayOf("audio/*"))
            }
        )
    }

    if (showVideoAudioFormatDialog) {
        VideoAudioFormatDialog(
            onDismiss = {
                showVideoAudioFormatDialog = false
                pendingVideoAudioUri = null
            },
            onSelect = { format ->
                showVideoAudioFormatDialog = false
                val fileName = videoAudioOutputName(context, pendingVideoAudioUri, format)
                when (format) {
                    VideoAudioExportFormat.M4a -> m4aDocumentCreator.launch(fileName)
                    VideoAudioExportFormat.Wav -> wavDocumentCreator.launch(fileName)
                }
            }
        )
    }

    if (pendingVideoUri != null || pendingAudioUri != null) {
        SpeechLanguageDialog(
            onDismiss = {
                pendingVideoUri = null
                pendingAudioUri = null
            },
            onSelect = { language ->
                val videoUri = pendingVideoUri
                val audioUri = pendingAudioUri
                pendingVideoUri = null
                pendingAudioUri = null
                when {
                    videoUri != null -> viewModel.transcribeVideo(videoUri, language)
                    audioUri != null -> viewModel.transcribeAudio(audioUri, language)
                }
            }
        )
    }
}

@Composable
fun AudioActionDialog(
    onDismiss: () -> Unit,
    onLiveSpeech: () -> Unit,
    onImportAudio: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("语音转文字") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("选择语音来源", color = Color(0xFF64748B))
                Button(onClick = onLiveSpeech, modifier = Modifier.fillMaxWidth()) {
                    Text("实时录音")
                }
                Button(onClick = onImportAudio, modifier = Modifier.fillMaxWidth()) {
                    Text("导入音频")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun VideoAudioFormatDialog(
    onDismiss: () -> Unit,
    onSelect: (VideoAudioExportFormat) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("视频转音频") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("选择导出格式", color = Color(0xFF64748B))
                Button(
                    onClick = { onSelect(VideoAudioExportFormat.M4a) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("M4A/AAC 推荐")
                }
                Text(
                    "速度快、体积小。需要视频本身包含 AAC 音轨，否则请选择 WAV。",
                    color = Color(0xFF64748B),
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = { onSelect(VideoAudioExportFormat.Wav) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("WAV 兼容")
                }
                Text(
                    "兼容性更强，文件更大，适合后续识别或剪辑。",
                    color = Color(0xFF64748B),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun LiveSpeechRecorderCard(
    continuous: Boolean,
    noiseSuppression: Boolean,
    listening: Boolean,
    paused: Boolean,
    draft: String,
    notice: String?,
    onContinuousChange: (Boolean) -> Unit,
    onNoiseSuppressionChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("实时录音", fontWeight = FontWeight.SemiBold)
                    Text(
                        notice ?: when {
                            listening -> "正在聆听"
                            paused -> "已暂停"
                            continuous -> "本地持续分段识别"
                            else -> "单次录音"
                        },
                        color = Color(0xFF64748B),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text("连续录音", color = Color(0xFF334155), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(8.dp))
                Switch(checked = continuous, onCheckedChange = onContinuousChange)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("实时降噪", color = Color(0xFF334155), style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (noiseSuppression) "优先启用系统降噪和自动增益" else "关闭系统降噪",
                        color = Color(0xFF64748B),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = noiseSuppression, onCheckedChange = onNoiseSuppressionChange)
            }

            Text(
                draft.ifBlank { "识别内容会先保存在这里，点击保存后进入历史记录" },
                color = if (draft.isBlank()) Color(0xFF94A3B8) else Color(0xFF0F172A),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (paused) {
                    Button(onClick = onResume, modifier = Modifier.weight(1f)) {
                        Text("继续")
                    }
                } else if (listening) {
                    Button(onClick = onPause, modifier = Modifier.weight(1f)) {
                        Text("暂停")
                    }
                } else {
                    Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                        Text("开始录音")
                    }
                }
                Button(onClick = onSave, enabled = draft.isNotBlank(), modifier = Modifier.weight(1f)) {
                    Text("保存")
                }
                TextButton(onClick = onClear) {
                    Text("清空")
                }
            }
        }
    }
}

@Composable
fun SpeechLanguageDialog(
    onDismiss: () -> Unit,
    onSelect: (SpeechLanguage) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择识别语言") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SpeechLanguage.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { language ->
                            Button(
                                onClick = { onSelect(language) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(language.label, maxLines = 1)
                            }
                        }
                        repeat(3 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Text(
                    "四川话、上海话、口音普通话、中英混合请选择“其它方言”。",
                    color = Color(0xFF64748B),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关于与第三方声明", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "本地全能转文字以安卓本地处理为目标，支持视频转文字、语音转文字和图片转文字。项目代码不主动把用户选择的音频、视频、图片或识别文本上传到项目作者服务器。",
                    color = Color(0xFF334155),
                    style = MaterialTheme.typography.bodyMedium
                )
                NoticeSection(
                    title = "作者",
                    body = "开心小元\n开心哔站：https://b23.tv/OEuDkek"
                )
                NoticeSection(
                    title = "Vosk Android",
                    body = "用于备用本地语音识别。依赖 com.alphacephei:vosk-android:0.3.47，许可证为 Apache License 2.0。"
                )
                NoticeSection(
                    title = "Vosk 中文小模型",
                    body = "内置 vosk-model-small-cn-0.22，用于移动端中文本地识别。官方模型页标注为 Apache License 2.0，分发时应保留模型名称、来源、版权和许可证信息。"
                )
                NoticeSection(
                    title = "sherpa-onnx",
                    body = "用于本地离线语音识别运行时。项目内置 sherpa-onnx-1.13.0.aar，上游许可证为 Apache License 2.0。"
                )
                NoticeSection(
                    title = "SenseVoice / FunASR 模型",
                    body = "用于本地离线语音识别。项目内置模型由 SenseVoice / FunASR 相关模型转换得到，遵循 FunASR Model Open Source License Agreement 1.1。二次分发或商业化时应注明来源和作者信息，并保留相关模型名称。"
                )
                NoticeSection(
                    title = "OPUS-MT / Marian 本地翻译模型",
                    body = "fullOffline 版内置 OPUS-MT / Marian ONNX 量化模型，用于中英、中日、韩英等本地字幕翻译路线。模型应保留来源、许可证和署名信息；推荐使用 CC-BY 4.0 兼容模型，避免带非商业限制的模型进入商业版本。"
                )
                NoticeSection(
                    title = "Google ML Kit Text Recognition",
                    body = "用于图片 OCR。ML Kit 对输入数据的处理在设备端完成，但可能联系 Google 服务器获取修复、模型更新、硬件兼容信息，也可能发送 API 性能和使用指标。"
                )
                NoticeSection(
                    title = "AndroidX Media3 / ExoPlayer",
                    body = "用于字幕校对界面的视频播放、全屏播放、横竖屏切换、进度拖动和倍速控制。相关组件通常遵循 Apache License 2.0。"
                )
                NoticeSection(
                    title = "AndroidX / Jetpack Compose / Kotlin",
                    body = "用于 Android 应用框架、界面和协程能力。相关组件通常遵循 Apache License 2.0，以实际依赖包随附许可证为准。"
                )
                Text(
                    "完整声明见仓库 THIRD_PARTY_NOTICES.md、PRIVACY.md 和 LICENSE。公开可见不等于授予开源许可；本项目原创代码默认保留所有权利。",
                    color = Color(0xFF64748B),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        }
    )
}

@Composable
fun NoticeSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
        Text(body, color = Color(0xFF475569), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun TextNoteScreen(
    session: TextNoteSession,
    onClose: () -> Unit,
    onSave: (String) -> Unit
) {
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var editing by remember(session.title, session.text) { mutableStateOf(false) }
    var draft by remember(session.title, session.text) { mutableStateOf(session.text) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7FAFC))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(if (editing) "正在编辑" else "便签详情", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { if (draft.isNotBlank()) clipboard.setText(AnnotatedString(draft)) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "复制")
            }
            IconButton(onClick = { if (draft.isNotBlank()) shareText(context, draft) }) {
                Icon(Icons.Default.Share, contentDescription = "分享")
            }
            TextButton(
                onClick = {
                    if (editing) {
                        onSave(draft)
                        editing = false
                    } else {
                        editing = true
                    }
                }
            ) {
                Text(if (editing) "保存" else "编辑")
            }
        }

        Surface(
            color = Color.White,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (editing) {
                TextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    minLines = 18
                )
            } else {
                Text(
                    draft.ifBlank { "暂无内容" },
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(18.dp),
                    color = if (draft.isBlank()) Color(0xFF94A3B8) else Color(0xFF0F172A),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoSubtitleEditorScreen(
    session: VideoEditSession,
    videoRecords: List<ConversionRecord>,
    translationStatus: TranslationModelStatus,
    translationBusy: Boolean,
    onClose: () -> Unit,
    onSave: (List<SubtitleSegment>) -> Unit,
    onReplaceVideo: (Uri, List<SubtitleSegment>) -> Unit,
    onSwitchVideo: (ConversionRecord, List<SubtitleSegment>?) -> Unit,
    onPrepareTranslationModel: (TranslationLanguage, TranslationLanguage) -> Unit,
    onTranslate: (TranslationLanguage, TranslationLanguage, TranslationApplyMode, List<SubtitleSegment>) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = context as? Activity
    val clipboard = LocalClipboardManager.current
    var segments by remember(session.recordId, session.segments) { mutableStateOf(session.segments) }
    var translatedSegments by remember(session.recordId, session.translatedSegments) { mutableStateOf(session.translatedSegments) }
    var subtitleDisplayMode by remember(session.recordId, session.subtitleDisplayMode) { mutableStateOf(session.subtitleDisplayMode) }
    var selectedId by remember(session.recordId) { mutableStateOf(session.segments.firstOrNull()?.id) }
    var positionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var isVideoReady by remember(session.videoUri) { mutableStateOf(false) }
    var videoError by remember(session.videoUri) { mutableStateOf<String?>(null) }
    var editingSegmentId by remember(session.recordId) { mutableStateOf<Long?>(null) }
    var editDraft by remember { mutableStateOf("") }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var isFullScreen by remember { mutableStateOf(false) }
    var showPlayerControls by remember { mutableStateOf(true) }
    var subtitlesVisible by remember { mutableStateOf(true) }
    var showVideoHistory by remember { mutableStateOf(false) }
    var showTranslationDialog by remember { mutableStateOf(false) }
    var pendingSwitchRecord by remember { mutableStateOf<ConversionRecord?>(null) }
    var orientationMode by remember { mutableStateOf(VideoOrientationMode.Auto) }
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    BackHandler {
        if (isFullScreen) {
            isFullScreen = false
        } else {
            onClose()
        }
    }

    val selectedSegment = segments.firstOrNull { it.id == selectedId } ?: segments.firstOrNull()
    val activeSegment = segments.firstOrNull { segment ->
        positionMs.toLong() >= segment.startMs && positionMs.toLong() < segment.endMs
    }
    val activeTranslatedSegment = activeSegment?.let { active ->
        translatedSegments.firstOrNull { it.id == active.id }
    } ?: translatedSegments.firstOrNull { segment ->
        positionMs.toLong() >= segment.startMs && positionMs.toLong() < segment.endMs
    }
    val activeSubtitleText = displaySubtitleText(
        original = activeSegment,
        translated = activeTranslatedSegment,
        mode = subtitleDisplayMode
    )
    val fullText = segments.joinToString("\n") { it.text }.trim()
    val hasUnsavedChanges = remember(segments, session.segments) {
        segments.toJsonString() != session.segments.toJsonString()
    }
    val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    val requestVideoSwitch: (ConversionRecord) -> Unit = { record ->
        if (record.id == session.recordId) {
            showVideoHistory = false
        } else if (hasUnsavedChanges) {
            pendingSwitchRecord = record
        } else {
            showVideoHistory = false
            onSwitchVideo(record, null)
        }
    }
    val player = remember(session.videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(session.videoUri))
            prepare()
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            positionMs = 0
            durationMs = 0
            isPlaying = false
            isVideoReady = false
            videoError = null
            player.setMediaItem(MediaItem.fromUri(it))
            player.prepare()
            onReplaceVideo(it, segments)
        }
    }

    LaunchedEffect(session.recordId) {
        positionMs = 0
        durationMs = 0
        isPlaying = false
        showVideoHistory = false
        pendingSwitchRecord = null
    }

    val togglePlayback: () -> Unit = {
        showPlayerControls = true
        if (player.isPlaying) {
            player.pause()
            isPlaying = false
        } else if (videoError != null) {
            videoError = "原视频无法读取，请重新选择视频文件"
        } else {
            runCatching {
                player.play()
                isPlaying = true
            }.onFailure {
                videoError = "播放失败，请重新选择视频文件"
                isPlaying = false
            }
        }
    }
    val seekBy: (Long) -> Unit = { deltaMs ->
        showPlayerControls = true
        val maxDuration = durationMs.takeIf { it > 0 }?.toLong() ?: player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = (player.currentPosition + deltaMs).coerceIn(0L, maxDuration)
        player.seekTo(target)
        positionMs = target.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    DisposableEffect(activity) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(activity, orientationMode) {
        activity?.requestedOrientation = orientationMode.requestedOrientation
    }

    DisposableEffect(activity, isFullScreen) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            if (isFullScreen) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val duration = player.duration
                if (duration > 0) {
                    durationMs = duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                }
                isVideoReady = playbackState == Player.STATE_READY || durationMs > 0
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                }
            }

            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
            }

            override fun onPlayerError(error: PlaybackException) {
                isPlaying = false
                isVideoReady = false
                durationMs = 0
                videoError = "原视频读取失败，请重新选择视频文件"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, playbackSpeed) {
        player.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(isFullScreen) {
        showPlayerControls = true
    }

    LaunchedEffect(isFullScreen, isPlaying, showPlayerControls) {
        if (isFullScreen && isPlaying && showPlayerControls) {
            delay(3000)
            if (isFullScreen && isPlaying) showPlayerControls = false
        }
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val duration = player.duration
            if (duration > 0) {
                durationMs = duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
            isPlaying = player.isPlaying
            delay(250)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111111))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!isFullScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("字幕校对", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(session.title, color = Color(0xFFBDBDBD), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = {
                        if (fullText.isNotBlank()) clipboard.setText(AnnotatedString(fullText))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制", tint = Color.White)
                    }
                    IconButton(onClick = {
                        if (fullText.isNotBlank()) shareText(context, fullText)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "分享", tint = Color.White)
                    }
                    TextButton(onClick = { showTranslationDialog = true }) {
                        Text("翻译字幕", color = Color.White)
                    }
                    Button(onClick = { onSave(segments) }) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("保存")
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        this.player = player
                    }
                },
                update = { view ->
                    view.player = player
                },
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        onClick = {
                            if (isFullScreen) {
                                showPlayerControls = !showPlayerControls
                            } else {
                                togglePlayback()
                            }
                        }
                    )
            )

            if (isFullScreen && showPlayerControls) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .fillMaxWidth()
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.56f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { isFullScreen = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出全屏", tint = Color.White)
                    }
                    Text(
                        session.title,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = { showVideoHistory = true }) {
                        Icon(Icons.Default.History, contentDescription = "历史视频", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            if (translatedSegments.isEmpty()) {
                                subtitlesVisible = !subtitlesVisible
                            } else {
                                subtitlesVisible = true
                                subtitleDisplayMode = subtitleDisplayMode.next()
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Subtitles,
                            contentDescription = "字幕模式",
                            tint = if (subtitlesVisible) Color.White else Color(0xFF777777)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(36.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    IconButton(onClick = { seekBy(-10_000L) }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Default.FastRewind, contentDescription = "快退10秒", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    IconButton(
                        onClick = togglePlayback,
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.White, RoundedCornerShape(32.dp))
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "播放",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    IconButton(onClick = { seekBy(10_000L) }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Default.FastForward, contentDescription = "快进10秒", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                }
            }

            if (subtitlesVisible && activeSubtitleText.isNotBlank()) {
                Text(
                    activeSubtitleText,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(18.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            if (!isVideoReady || videoError != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(18.dp)
                        .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        videoError ?: "正在读取原视频...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (videoError != null) {
                        Button(onClick = { videoPicker.launch(arrayOf("video/*")) }) {
                            Text("重新选择视频")
                        }
                    }
                }
            }
        }

        if (!isFullScreen || showPlayerControls) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF171717))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val safeDuration = durationMs.coerceAtLeast(1)
                Column(modifier = Modifier.weight(1f)) {
                    Text("${formatDuration(positionMs.toLong())} / ${formatDuration(durationMs.toLong())}", color = Color.White)
                    Slider(
                        value = positionMs.coerceIn(0, safeDuration).toFloat(),
                        onValueChange = {
                            showPlayerControls = true
                            positionMs = it.toInt()
                        },
                        onValueChangeFinished = { player.seekTo(positionMs.toLong()) },
                        valueRange = 0f..safeDuration.toFloat()
                    )
                }
                if (isFullScreen) {
                    TextButton(onClick = { segments = segments.shiftedBy(-500L) }) {
                        Text("-0.5s", color = Color.White)
                    }
                    TextButton(onClick = { segments = segments.shiftedBy(500L) }) {
                        Text("+0.5s", color = Color.White)
                    }
                }
                Box {
                    TextButton(onClick = { speedMenuExpanded = true }) {
                        Text(formatSpeed(playbackSpeed), color = Color.White)
                    }
                    DropdownMenu(
                        expanded = speedMenuExpanded,
                        onDismissRequest = { speedMenuExpanded = false }
                    ) {
                        speedOptions.forEach { speed ->
                            DropdownMenuItem(
                                text = { Text(formatSpeed(speed)) },
                                onClick = {
                                    playbackSpeed = speed
                                    speedMenuExpanded = false
                                    showPlayerControls = true
                                }
                            )
                        }
                    }
                }
                TextButton(onClick = { orientationMode = orientationMode.next() }) {
                    Icon(Icons.Default.ScreenRotation, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text(orientationMode.label, color = Color.White)
                }
                TextButton(onClick = { isFullScreen = !isFullScreen }) {
                    Text(if (isFullScreen) "退出全屏" else "全屏", color = Color.White)
                }
                if (!isFullScreen) {
                    IconButton(
                        onClick = togglePlayback,
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.White, RoundedCornerShape(24.dp))
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "播放",
                            tint = Color.Black
                        )
                    }
                }
            }
        }

        if (!isFullScreen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 300.dp, max = 420.dp)
                    .background(Color(0xFF242424))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("批量编辑", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { segments = segments.shiftedBy(-500L) }) {
                        Text("提前0.5秒", color = Color.White)
                    }
                    TextButton(onClick = { segments = segments.shiftedBy(500L) }) {
                        Text("延后0.5秒", color = Color.White)
                    }
                    Text("${segments.size} 段", color = Color(0xFFBDBDBD))
                }
                Spacer(Modifier.height(8.dp))

                selectedSegment?.let { segment ->
                    TextField(
                        value = segment.text,
                        onValueChange = { value ->
                            segments = segments.map { if (it.id == segment.id) it.copy(text = value) else it }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        label = { Text(formatDuration(segment.startMs)) }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(segments, key = { it.id }) { segment ->
                        SubtitleSegmentRow(
                            segment = segment,
                            selected = segment.id == selectedSegment?.id,
                            onClick = {
                                selectedId = segment.id
                                player.seekTo(segment.startMs)
                            },
                            onEdit = {
                                selectedId = segment.id
                                editDraft = segment.text
                                editingSegmentId = segment.id
                            },
                            onDelete = {
                                segments = segments.filterNot { it.id == segment.id }
                                selectedId = segments.firstOrNull()?.id
                            }
                        )
                    }
                }
            }
        }
        }

        if (isFullScreen && showVideoHistory) {
            FullScreenVideoHistoryPanel(
                records = videoRecords,
                currentRecordId = session.recordId,
                isLandscape = isLandscape,
                onSelect = requestVideoSwitch,
                onDismiss = { showVideoHistory = false }
            )
        }
    }

    val editingSegment = segments.firstOrNull { it.id == editingSegmentId }
    if (editingSegment != null) {
        AlertDialog(
            onDismissRequest = { editingSegmentId = null },
            title = { Text("编辑字幕") },
            text = {
                TextField(
                    value = editDraft,
                    onValueChange = { editDraft = it },
                    label = { Text(formatDuration(editingSegment.startMs)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        segments = segments.map {
                            if (it.id == editingSegment.id) it.copy(text = editDraft) else it
                        }
                        editingSegmentId = null
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSegmentId = null }) {
                    Text("取消")
                }
            }
        )
    }

    pendingSwitchRecord?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingSwitchRecord = null },
            title = { Text("当前字幕还未保存") },
            text = { Text("切换到“${record.title}”前，是否保存当前字幕修改？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showVideoHistory = false
                        pendingSwitchRecord = null
                        onSwitchVideo(record, segments)
                    }
                ) {
                    Text("保存并切换")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingSwitchRecord = null }) {
                        Text("取消")
                    }
                    TextButton(
                        onClick = {
                            showVideoHistory = false
                            pendingSwitchRecord = null
                            onSwitchVideo(record, null)
                        }
                    ) {
                        Text("不保存切换")
                    }
                }
            }
        )
    }

    if (showTranslationDialog) {
        SubtitleTranslationDialog(
            status = translationStatus,
            busy = translationBusy,
            onDismiss = { showTranslationDialog = false },
            onPrepare = onPrepareTranslationModel,
            onTranslate = { source, target, mode ->
                showTranslationDialog = false
                onTranslate(source, target, mode, segments)
            }
        )
    }
}

@Composable
fun SubtitleTranslationDialog(
    status: TranslationModelStatus,
    busy: Boolean,
    onDismiss: () -> Unit,
    onPrepare: (TranslationLanguage, TranslationLanguage) -> Unit,
    onTranslate: (TranslationLanguage, TranslationLanguage, TranslationApplyMode) -> Unit
) {
    var source by remember { mutableStateOf(TranslationLanguage.English) }
    var target by remember { mutableStateOf(TranslationLanguage.Chinese) }
    var mode by remember { mutableStateOf(TranslationApplyMode.Bilingual) }
    val selectedRoute = status.route(source, target)
    val routeReady = status.hasModelRoute(source, target)
    val canTranslate = status.canTranslate(source, target)
    val statusText = when {
        !status.fullOfflineBuild -> "当前是标准版，请打包 fullOffline 版并内置翻译模型。"
        canTranslate -> "当前路线可用：${selectedRoute.joinToString(" + ") { it.id }}"
        else -> status.unavailableReason(source, target)
    }
    val availableTargets = TranslationLanguage.entries.filter { language ->
        language != source && status.hasModelRoute(source, language)
    }

    LaunchedEffect(source, status) {
        if (availableTargets.isNotEmpty() && target !in availableTargets) {
            target = availableTargets.first()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("翻译字幕") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    statusText,
                    color = if (canTranslate) Color(0xFF0F766E) else Color(0xFFB45309),
                    style = MaterialTheme.typography.bodySmall
                )
                Text("原语言", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TranslationLanguage.entries.forEach { language ->
                        FilterChip(
                            selected = source == language,
                            onClick = {
                                source = language
                                if (target == language) target = TranslationLanguage.Chinese.takeIf { it != language } ?: TranslationLanguage.English
                            },
                            label = { Text(language.label) }
                        )
                    }
                }
                Text("目标语言", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TranslationLanguage.entries.forEach { language ->
                        val enabled = language != source && status.hasModelRoute(source, language)
                        FilterChip(
                            selected = target == language,
                            enabled = enabled,
                            onClick = { if (enabled) target = language },
                            label = { Text(language.label) }
                        )
                    }
                }
                Text("显示方式", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TranslationApplyMode.entries.forEach { item ->
                        FilterChip(
                            selected = mode == item,
                            onClick = { mode = item },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (routeReady && !canTranslate) {
                    TextButton(
                        enabled = !busy,
                        onClick = { onPrepare(source, target) }
                    ) {
                        Text(if (busy) "启动中" else "启动模型")
                    }
                }
                TextButton(
                    enabled = canTranslate && !busy,
                    onClick = { onTranslate(source, target, mode) }
                ) {
                    Text("开始翻译")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun FullScreenVideoHistoryPanel(
    records: List<ConversionRecord>,
    currentRecordId: Long,
    isLandscape: Boolean,
    onSelect: (ConversionRecord) -> Unit,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.36f))
                .clickable(onClick = onDismiss)
        )

        val panelModifier = if (isLandscape) {
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .widthIn(min = 300.dp, max = 380.dp)
                .padding(vertical = 16.dp, horizontal = 14.dp)
        } else {
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .padding(12.dp)
        }

        Surface(
            modifier = panelModifier,
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF202020)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("已转换视频", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                }

                if (records.isEmpty()) {
                    Text("暂无已转换视频", color = Color(0xFFBDBDBD), modifier = Modifier.padding(vertical = 24.dp))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(records, key = { it.id }) { record ->
                            FullScreenVideoHistoryItem(
                                record = record,
                                isCurrent = record.id == currentRecordId,
                                onClick = { onSelect(record) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FullScreenVideoHistoryItem(
    record: ConversionRecord,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var thumbnail by remember(record.sourceUri) { mutableStateOf<ImageBitmap?>(null) }
    val segmentCount = remember(record.segmentsJson, record.resultText) {
        record.subtitleSegmentCount()
    }

    LaunchedEffect(record.sourceUri) {
        thumbnail = withContext(Dispatchers.IO) {
            loadVideoThumbnail(context, Uri.parse(record.sourceUri))?.asImageBitmap()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isCurrent) Color(0xFF303A3A) else Color(0xFF2A2A2A), RoundedCornerShape(6.dp))
            .border(
                BorderStroke(1.dp, if (isCurrent) Color(0xFF14B8A6) else Color(0xFF3A3A3A)),
                RoundedCornerShape(6.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(76.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF171717)),
            contentAlignment = Alignment.Center
        ) {
            val image = thumbnail
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF9FE7DC), modifier = Modifier.size(22.dp))
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(record.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text("${timeLabel(record.createdAt)} · ${segmentCount}段字幕", color = Color(0xFFBDBDBD), style = MaterialTheme.typography.bodySmall)
        }
        if (isCurrent) {
            Text("当前", color = Color(0xFF5EEAD4), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun SubtitleSegmentRow(
    segment: SubtitleSegment,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) Color(0xFF343434) else Color(0xFF242424), RoundedCornerShape(6.dp))
            .border(
                BorderStroke(1.dp, if (selected) Color(0xFFFF5C93) else Color(0xFF343434)),
                RoundedCornerShape(6.dp)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(formatDuration(segment.startMs), color = Color(0xFF9CA3AF), modifier = Modifier.width(56.dp))
        Text(segment.text, color = Color.White, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "编辑", tint = Color(0xFFBDBDBD))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color(0xFFBDBDBD))
        }
    }
}

@Composable
fun ModelCard(ready: Boolean, onImport: () -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = if (ready) Color(0xFFEFFCF6) else Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color(0xFF0F766E))
            Column(modifier = Modifier.weight(1f)) {
                Text(if (ready) "精准语音模型已内置" else "精准模型缺失", fontWeight = FontWeight.SemiBold)
                Text("已内置 sherpa-onnx SenseVoice，普通话和粤语优先使用本地精准识别", color = Color(0xFF64748B))
            }
            Button(onClick = onImport) { Text("备用导入") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceModeSelector(
    selected: PerformanceMode,
    onSelect: (PerformanceMode) -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("性能模式", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                PerformanceMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selected == mode,
                        onClick = { onSelect(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }
            Text(
                selected.description,
                color = Color(0xFF64748B)
            )
        }
    }
}

@Composable
fun ToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    extra: @Composable (() -> Unit)? = null
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(color.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Color(0xFF64748B), style = MaterialTheme.typography.bodyMedium)
            }
            extra?.invoke()
        }
    }
}

@Composable
fun ResultPanel(
    busy: Boolean,
    progress: Float,
    status: String,
    text: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onOpenFull: () -> Unit
) {
    val scrollState = rememberScrollState()

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("转换结果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                IconButton(onClick = onCopy, enabled = text.isNotBlank()) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                }
                IconButton(onClick = onShare, enabled = text.isNotBlank()) {
                    Icon(Icons.Default.Share, contentDescription = "分享")
                }
            }
            Text(status, color = Color(0xFF64748B), style = MaterialTheme.typography.bodyMedium)
            if (busy) LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Surface(
                color = Color(0xFFF1F5F9),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(180.dp)
            ) {
                Text(
                    text.ifBlank { "识别出的文字会显示在这里" },
                    modifier = Modifier
                        .verticalScroll(scrollState)
                        .padding(14.dp),
                    color = if (text.isBlank()) Color(0xFF94A3B8) else Color(0xFF0F172A)
                )
            }
            if (text.isNotBlank()) {
                TextButton(onClick = onOpenFull, modifier = Modifier.align(Alignment.End)) {
                    Text("查看全文")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: ConverterViewModel, padding: PaddingValues) {
    val records by viewModel.records.collectAsState()
    var filter by remember { mutableStateOf<ConversionType?>(null) }
    val filtered = records.filter { filter == null || it.type == filter }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF7FAFC)).padding(padding).padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("历史记录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("自动保存每次转换结果", color = Color(0xFF64748B))
            }
            TextButton(onClick = viewModel::clearAll, enabled = records.isNotEmpty()) { Text("清空") }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("全部") })
            FilterChip(selected = filter == ConversionType.Image, onClick = { filter = ConversionType.Image }, label = { Text("图片") })
            FilterChip(selected = filter == ConversionType.Audio, onClick = { filter = ConversionType.Audio }, label = { Text("语音") })
            FilterChip(selected = filter == ConversionType.Video, onClick = { filter = ConversionType.Video }, label = { Text("视频") })
            FilterChip(selected = filter == ConversionType.VideoAudio, onClick = { filter = ConversionType.VideoAudio }, label = { Text("视频音频") })
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (filtered.isEmpty()) {
                item { Text("暂无历史记录", color = Color(0xFF94A3B8), modifier = Modifier.padding(top = 40.dp)) }
            }
            items(filtered, key = { it.id }) { record ->
                HistoryItem(
                    record = record,
                    onCopy = { clipboard.setText(AnnotatedString(record.resultText.ifBlank { record.message })) },
                    onShare = { shareText(context, record.resultText.ifBlank { record.message }) },
                    onShareAudio = { shareAudio(context, record.audioAssetUri()) },
                    onOpenTextNote = { viewModel.openTextNote(record) },
                    onOpenVideoEdit = { viewModel.openVideoEditor(record) },
                    onDelete = { viewModel.delete(record) }
                )
            }
        }
    }
}

@Composable
fun HistoryItem(
    record: ConversionRecord,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onShareAudio: () -> Unit,
    onOpenTextNote: () -> Unit,
    onOpenVideoEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = record.resultText.isNotBlank() || record.message.isNotBlank(),
                        onClick = onOpenTextNote
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(record.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(typeLabel(record.type), color = Color(0xFF0F766E), style = MaterialTheme.typography.labelMedium)
            }
            Text("${statusLabel(record.status)} · ${timeLabel(record.createdAt)}", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
            Text(
                record.resultText.ifBlank { record.message.ifBlank { "等待转写处理" } },
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                color = Color(0xFF334155)
            )
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (record.type == ConversionType.Video && record.status == ConversionStatus.Success) {
                    TextButton(onClick = onOpenVideoEdit) { Text("编辑字幕") }
                }
                if (record.audioAssetUri().isNotBlank() && record.status == ConversionStatus.Success) {
                    TextButton(onClick = onShareAudio) { Text("分享音频") }
                }
                IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, contentDescription = "复制") }
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "分享") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "删除") }
            }
        }
    }
}

@Composable
fun GoodZhTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = Color(0xFF0F766E),
            secondary = Color(0xFF155E75),
            tertiary = Color(0xFF7C3AED),
            background = Color(0xFFF7FAFC),
            surface = Color.White
        ),
        content = content
    )
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "分享文字"))
}

private fun shareAudio(context: Context, rawUri: String) {
    if (rawUri.isBlank()) return
    val uri = shareableUri(context, rawUri) ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, "音频", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享音频"))
}

private fun shareableUri(context: Context, rawUri: String): Uri? {
    val parsed = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return null
    return if (parsed.scheme == "file") {
        val file = File(parsed.path ?: return null)
        if (!file.exists()) return null
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } else {
        parsed
    }
}

private fun videoAudioOutputName(context: Context, videoUri: Uri?, format: VideoAudioExportFormat): String {
    val rawName = videoUri?.let { displayName(context, it) } ?: "视频音频"
    val baseName = rawName.substringBeforeLast('.', rawName)
        .ifBlank { "视频音频" }
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
    return "$baseName-音频.${format.extension}"
}

private fun buildLiveSpeechPreview(draft: String, partial: String): String {
    val cleanDraft = draft.trim()
    val cleanPartial = partial.trim()
    return when {
        cleanDraft.isBlank() -> cleanPartial
        cleanPartial.isBlank() -> cleanDraft
        cleanDraft.lines().lastOrNull()?.trim() == cleanPartial -> cleanDraft
        else -> "$cleanDraft\n$cleanPartial"
    }.trim()
}

private fun appendRecognizedSpeech(current: String, incoming: String): String {
    val chunk = incoming.trim()
    if (chunk.isBlank()) return current.trim()
    val lines = current.lines().map { it.trim() }.filter { it.isNotBlank() }
    if (lines.lastOrNull() == chunk) return lines.joinToString("\n")
    return if (lines.isEmpty()) chunk else lines.joinToString("\n") + "\n" + chunk
}

private fun ConversionRecord.isOpenableVideoRecord(): Boolean =
    type == ConversionType.Video &&
        status == ConversionStatus.Success &&
        sourceUri.isNotBlank() &&
        (segmentsJson.isNotBlank() || resultText.isNotBlank())

private fun ConversionRecord.audioAssetUri(): String =
    when (type) {
        ConversionType.Audio -> sourceUri.ifBlank { outputUri }
        ConversionType.VideoAudio -> outputUri.ifBlank { sourceUri }
        ConversionType.Video,
        ConversionType.Image -> ""
    }

private fun ConversionRecord.toVideoEditSession(): VideoEditSession? {
    if (!isOpenableVideoRecord()) return null
    val segments = segmentsJson.toSubtitleSegments()
        .ifEmpty { buildSubtitleSegments(resultText, 0L) }
    val translatedSegments = translatedSegmentsJson.toSubtitleSegments()
        .ifEmpty { buildSubtitleSegments(translatedText, 0L) }
    if (segments.isEmpty()) return null
    return VideoEditSession(
        recordId = id,
        title = title,
        videoUri = Uri.parse(sourceUri),
        segments = segments,
        translatedSegments = translatedSegments,
        subtitleDisplayMode = runCatching { SubtitleDisplayMode.valueOf(subtitleDisplayMode) }
            .getOrDefault(if (translatedSegments.isNotEmpty()) SubtitleDisplayMode.Translation else SubtitleDisplayMode.Original)
    )
}

private fun ConversionRecord.subtitleSegmentCount(): Int =
    segmentsJson.toSubtitleSegments().size.takeIf { it > 0 }
        ?: buildSubtitleSegments(resultText, 0L).size

private fun SubtitleDisplayMode.next(): SubtitleDisplayMode = when (this) {
    SubtitleDisplayMode.Original -> SubtitleDisplayMode.Translation
    SubtitleDisplayMode.Translation -> SubtitleDisplayMode.Bilingual
    SubtitleDisplayMode.Bilingual -> SubtitleDisplayMode.Original
}

private fun displaySubtitleText(
    original: SubtitleSegment?,
    translated: SubtitleSegment?,
    mode: SubtitleDisplayMode
): String = when (mode) {
    SubtitleDisplayMode.Original -> original?.text.orEmpty()
    SubtitleDisplayMode.Translation -> translated?.text ?: original?.text.orEmpty()
    SubtitleDisplayMode.Bilingual -> listOfNotNull(original?.text, translated?.text)
        .distinct()
        .joinToString("\n")
}

private fun alignTranslatedSegments(
    segments: List<SubtitleSegment>,
    translatedSegments: List<SubtitleSegment>
): List<SubtitleSegment> {
    if (segments.isEmpty() || translatedSegments.isEmpty()) return emptyList()
    val translatedById = translatedSegments.associateBy { it.id }
    return segments.mapIndexedNotNull { index, segment ->
        val translated = translatedById[segment.id] ?: translatedSegments.getOrNull(index)
        translated?.copy(
            id = segment.id,
            startMs = segment.startMs,
            endMs = segment.endMs
        )
    }
}

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
): List<SubtitleSegment> {
    val segments = ArrayList<SubtitleSegment>()
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
            segments.add(
                segment.copy(
                    id = nextId++,
                    startMs = start,
                    endMs = end
                )
            )
        }
    }
    return segments.sortedBy { it.startMs }
}

private fun List<SubtitleSegment>.shiftedBy(deltaMs: Long): List<SubtitleSegment> =
    map { segment ->
        val start = (segment.startMs + deltaMs).coerceAtLeast(0L)
        val end = (segment.endMs + deltaMs).coerceAtLeast(start + 300L)
        segment.copy(startMs = start, endMs = end)
    }

private fun loadVideoThumbnail(context: Context, uri: Uri): Bitmap? =
    runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 152, 88)
            } else {
                retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } ?: return@runCatching null
            val scaled = Bitmap.createScaledBitmap(frame, 152, 88, true)
            if (scaled != frame) frame.recycle()
            scaled
        }
    }.getOrNull()

private fun buildSubtitleSegments(text: String, durationMs: Long): List<SubtitleSegment> {
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
        SubtitleSegment(
            id = System.currentTimeMillis() + index,
            startMs = cursor,
            endMs = end.coerceAtMost(safeDuration),
            text = sentence
        ).also {
            cursor = it.endMs
        }
    }
}

private fun videoDurationMs(context: Context, uri: Uri): Long =
    runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
    }.getOrDefault(0L)

private fun List<SubtitleSegment>.toJsonString(): String {
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

private fun String.toSubtitleSegments(): List<SubtitleSegment> =
    runCatching {
        val array = JSONArray(this)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    SubtitleSegment(
                        id = item.optLong("id", System.currentTimeMillis() + index),
                        startMs = item.optLong("startMs"),
                        endMs = item.optLong("endMs"),
                        text = item.optString("text")
                    )
                )
            }
        }.filter { it.text.isNotBlank() }
    }.getOrDefault(emptyList())

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun formatSpeed(speed: Float): String =
    "${String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')}x"

private fun normalizeRecognizedText(text: String, language: SpeechLanguage): String =
    when (language) {
        SpeechLanguage.English,
        SpeechLanguage.OtherDialect -> text.replace(Regex("\\s+"), " ").trim()
        SpeechLanguage.Mandarin,
        SpeechLanguage.Japanese,
        SpeechLanguage.Korean,
        SpeechLanguage.Cantonese -> text.replace(Regex("\\s+"), "").trim()
    }

private fun typeLabel(type: ConversionType): String = when (type) {
    ConversionType.Video -> "视频"
    ConversionType.Audio -> "语音"
    ConversionType.Image -> "图片"
    ConversionType.VideoAudio -> "视频音频"
}

private fun statusLabel(status: ConversionStatus): String = when (status) {
    ConversionStatus.Success -> "成功"
    ConversionStatus.Failed -> "失败"
    ConversionStatus.Pending -> "待处理"
}

private fun timeLabel(time: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(time))
