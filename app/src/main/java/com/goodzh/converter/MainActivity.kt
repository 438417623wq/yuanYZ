package com.goodzh.converter

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goodzh.converter.data.ConversionRecord
import com.goodzh.converter.data.ConversionStatus
import com.goodzh.converter.data.ConversionType
import com.goodzh.converter.domain.LocalSpeechEngine
import com.goodzh.converter.domain.ModelManager
import com.goodzh.converter.domain.OcrConverter
import com.goodzh.converter.domain.RecognitionMode
import com.goodzh.converter.domain.SherpaSpeechEngine
import com.goodzh.converter.domain.VideoAudioExtractor
import com.goodzh.converter.domain.displayName
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
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
    val segments: List<SubtitleSegment>
)

data class TextNoteSession(
    val record: ConversionRecord?,
    val title: String,
    val text: String
)

class ConverterViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as GoodZhApp
    private val ocr = OcrConverter(application)
    private val modelManager = ModelManager(application)
    private val audioExtractor = VideoAudioExtractor(application)
    private val speechEngine = LocalSpeechEngine(application)
    private val sherpaEngine = SherpaSpeechEngine(application)

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
    var recognitionMode by mutableStateOf(RecognitionMode.MandarinAccurate)
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
                statusText = "普通话精准模型已内置，可以直接选择视频转换"
            } else if (modelReady) {
                statusText = "备用本地模型已就绪，可以选择极速备用模式"
            }
        }
    }

    fun updateRecognitionMode(mode: RecognitionMode) {
        recognitionMode = mode
        statusText = when (mode) {
            RecognitionMode.MandarinAccurate -> "普通话精准：使用 sherpa-onnx SenseVoice 本地模型"
            RecognitionMode.DialectEnhanced -> "方言增强：自动识别普通话、粤语和中英混合"
            RecognitionMode.FastFallback -> "极速备用：使用 Vosk 小模型，速度快但准确率较低"
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

    fun transcribeVideo(uri: Uri) {
        viewModelScope.launch {
            val name = displayName(getApplication(), uri)
            val mode = recognitionMode
            if (mode != RecognitionMode.FastFallback && !sherpaEngine.isBundledModelReady()) {
                sherpaReady = false
                statusText = "缺少内置精准模型，请重新安装新版 APK"
                app.repository.save(
                    ConversionRecord(
                        type = ConversionType.Video,
                        title = name,
                        sourceUri = uri.toString(),
                        resultText = "",
                        status = ConversionStatus.Failed,
                        message = "缺少 sherpa-onnx 精准模型，未上传云端。"
                    )
                )
                return@launch
            }
            if (mode == RecognitionMode.FastFallback && !modelManager.hasModel() && !modelManager.ensureBundledModelInstalled()) {
                modelReady = false
                statusText = "缺少备用 Vosk 模型，无法使用极速备用模式"
                app.repository.save(
                    ConversionRecord(
                        type = ConversionType.Video,
                        title = name,
                        sourceUri = uri.toString(),
                        resultText = "",
                        status = ConversionStatus.Failed,
                        message = "缺少本地备用模型，未上传云端。"
                    )
                )
                return@launch
            }

            busy = true
            progress = 0f
            currentText = ""
            statusText = "正在本地提取视频音频..."
            runCatching {
                val wav = audioExtractor.extractToWav(uri) { value ->
                    progress = value
                    statusText = "正在本地提取音频 ${(value * 100).toInt()}%"
                }
                statusText = "正在使用${mode.label}模型识别文字..."
                val text = if (mode == RecognitionMode.FastFallback) {
                    speechEngine.transcribe(wav, modelManager.modelDir) { value ->
                        progress = value
                        statusText = "正在极速备用识别 ${(value * 100).toInt()}%"
                    }
                } else {
                    sherpaEngine.transcribe(wav, mode) { value ->
                        progress = value
                        statusText = "正在${mode.label}识别 ${(value * 100).toInt()}%"
                    }
                }
                val finalText = text
                    .replace(Regex("\\s+"), "")
                    .ifBlank { "未识别到语音文字" }
                val durationMs = videoDurationMs(getApplication(), uri)
                wav.delete()
                finalText to buildSubtitleSegments(finalText, durationMs)
            }.onSuccess { result ->
                val (text, segments) = result
                currentText = text
                progress = 1f
                statusText = "视频本地转文字完成：${mode.label}"
                val record = ConversionRecord(
                    type = ConversionType.Video,
                    title = name,
                    sourceUri = uri.toString(),
                    resultText = text,
                    segmentsJson = segments.toJsonString(),
                    status = ConversionStatus.Success
                )
                app.repository.save(record)
                activeVideoEditor = VideoEditSession(
                    recordId = record.id,
                    title = name,
                    videoUri = uri,
                    segments = segments
                )
            }.onFailure { error ->
                currentText = ""
                statusText = "视频本地转文字失败：${error.message ?: "未知错误"}"
                app.repository.save(
                    ConversionRecord(
                        type = ConversionType.Video,
                        title = name,
                        sourceUri = uri.toString(),
                        resultText = "",
                        status = ConversionStatus.Failed,
                        message = statusText
                    )
                )
            }
            busy = false
        }
    }

    fun recognizeImage(uri: Uri) {
        viewModelScope.launch {
            busy = true
            progress = 0f
            statusText = "正在识别图片文字..."
            val name = displayName(getApplication(), uri)
            runCatching { ocr.recognize(uri) }
                .onSuccess { text ->
                    currentText = text.ifBlank { "未识别到文字" }
                    statusText = "图片识别完成"
                    app.repository.save(
                        ConversionRecord(
                            type = ConversionType.Image,
                            title = name,
                            sourceUri = uri.toString(),
                            resultText = currentText,
                            status = ConversionStatus.Success
                        )
                    )
                }
                .onFailure { error ->
                    currentText = ""
                    statusText = "图片识别失败：${error.message ?: "未知错误"}"
                }
            busy = false
        }
    }

    fun importAudio(uri: Uri) {
        viewModelScope.launch {
            val name = displayName(getApplication(), uri)
            currentText = ""
            statusText = "音频文件已导入。下一步会复用本地模型转写音频。"
            app.repository.save(
                ConversionRecord(
                    type = ConversionType.Audio,
                    title = name,
                    sourceUri = uri.toString(),
                    resultText = "",
                    status = ConversionStatus.Pending,
                    message = "已导入本地文件，未上传云端。"
                )
            )
        }
    }

    fun saveSpeechResult(text: String) {
        viewModelScope.launch {
            currentText = text
            statusText = "语音转文字完成"
            app.repository.save(
                ConversionRecord(
                    type = ConversionType.Audio,
                    title = "实时语音 ${timeLabel(System.currentTimeMillis())}",
                    sourceUri = "",
                    resultText = text,
                    status = ConversionStatus.Success
                )
            )
        }
    }

    fun delete(record: ConversionRecord) {
        viewModelScope.launch { app.repository.delete(record) }
    }

    fun clearAll() {
        viewModelScope.launch { app.repository.clear() }
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
        if (record.type != ConversionType.Video || record.status != ConversionStatus.Success || record.sourceUri.isBlank()) return
        val uri = Uri.parse(record.sourceUri)
        val segments = record.segmentsJson.toSubtitleSegments()
            .ifEmpty { buildSubtitleSegments(record.resultText, videoDurationMs(getApplication(), uri)) }
        activeVideoEditor = VideoEditSession(
            recordId = record.id,
            title = record.title,
            videoUri = uri,
            segments = segments
        )
    }

    fun closeVideoEditor() {
        activeVideoEditor = null
    }

    fun saveVideoSubtitles(segments: List<SubtitleSegment>) {
        val session = activeVideoEditor ?: return
        viewModelScope.launch {
            val editedText = segments.joinToString("\n") { it.text }.trim()
            currentText = editedText
            statusText = "字幕修改已保存"
            app.repository.update(
                ConversionRecord(
                    id = session.recordId,
                    type = ConversionType.Video,
                    title = session.title,
                    sourceUri = session.videoUri.toString(),
                    resultText = editedText,
                    segmentsJson = segments.toJsonString(),
                    status = ConversionStatus.Success
                )
            )
            activeVideoEditor = session.copy(segments = segments)
        }
    }
}

@Suppress("UNCHECKED_CAST")
class ConverterViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ConverterViewModel(application) as T
}

@Composable
fun ConverterApp(
    viewModel: ConverterViewModel = viewModel(
        factory = ConverterViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    var selected by remember { mutableStateOf(0) }
    val editorSession = viewModel.activeVideoEditor
    val noteSession = viewModel.activeTextNote

    if (editorSession != null) {
        VideoSubtitleEditorScreen(
            session = editorSession,
            onClose = viewModel::closeVideoEditor,
            onSave = viewModel::saveVideoSubtitles
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
}

@Composable
fun WorkbenchScreen(viewModel: ConverterViewModel, padding: PaddingValues) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var liveSpeech by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::importModel)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::recognizeImage)
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::importAudio)
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.transcribeVideo(it)
        }
    }

    val recognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startSpeech(context, recognizer)
    }

    DisposableEffect(recognizer) {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
                liveSpeech = ""
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                listening = false
            }

            override fun onError(error: Int) {
                listening = false
                liveSpeech = "识别中断，请重试"
            }

            override fun onResults(results: Bundle?) {
                listening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                liveSpeech = text
                if (text.isNotBlank()) viewModel.saveSpeechResult(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                liveSpeech = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        onDispose { recognizer.destroy() }
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
            Text("智能转文字", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("视频、语音、图片统一转换，结果自动进入历史记录", color = Color(0xFF52616B))
        }
        item {
            ModelCard(
                ready = viewModel.sherpaReady,
                onImport = { modelPicker.launch("application/zip") }
            )
        }
        item {
            RecognitionModeSelector(
                selected = viewModel.recognitionMode,
                onSelect = viewModel::updateRecognitionMode
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
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            startSpeech(context, recognizer)
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    extra = {
                        OutlinedButton(onClick = { audioPicker.launch("audio/*") }) {
                            Text("导入音频")
                        }
                    }
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
        item {
            ResultPanel(
                busy = viewModel.busy,
                progress = viewModel.progress,
                status = if (listening) "正在聆听..." else viewModel.statusText,
                text = liveSpeech.ifBlank { viewModel.currentText },
                onCopy = {
                    val text = liveSpeech.ifBlank { viewModel.currentText }
                    if (text.isNotBlank()) clipboard.setText(AnnotatedString(text))
                },
                onShare = {
                    val text = liveSpeech.ifBlank { viewModel.currentText }
                    if (text.isNotBlank()) shareText(context, text)
                },
                onOpenFull = {
                    val text = liveSpeech.ifBlank { viewModel.currentText }
                    viewModel.openCurrentTextNote(text)
                }
            )
        }
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
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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

@Composable
fun VideoSubtitleEditorScreen(
    session: VideoEditSession,
    onClose: () -> Unit,
    onSave: (List<SubtitleSegment>) -> Unit
) {
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var segments by remember(session.recordId) { mutableStateOf(session.segments) }
    var selectedId by remember(session.recordId) { mutableStateOf(session.segments.firstOrNull()?.id) }
    var positionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var videoView by remember { mutableStateOf<VideoView?>(null) }

    val selectedSegment = segments.firstOrNull { it.id == selectedId } ?: segments.firstOrNull()
    val activeSegment = segments.lastOrNull { positionMs.toLong() >= it.startMs } ?: segments.firstOrNull()
    val fullText = segments.joinToString("\n") { it.text }.trim()

    LaunchedEffect(videoView, isPlaying) {
        while (true) {
            val view = videoView
            if (view != null) {
                positionMs = view.currentPosition
                durationMs = view.duration.takeIf { it > 0 } ?: durationMs
                isPlaying = view.isPlaying
            }
            delay(250)
        }
    }

    DisposableEffect(videoView) {
        onDispose {
            videoView?.stopPlayback()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111111))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
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
            Button(onClick = { onSave(segments) }) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("保存")
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
                    VideoView(ctx).apply {
                        setVideoURI(session.videoUri)
                        setOnPreparedListener { player ->
                            durationMs = duration
                            player.isLooping = false
                        }
                        setOnCompletionListener { isPlaying = false }
                        videoView = this
                    }
                },
                update = { view ->
                    if (videoView !== view) videoView = view
                },
                modifier = Modifier.fillMaxSize()
            )

            Text(
                activeSegment?.text.orEmpty(),
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF171717))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${formatDuration(positionMs.toLong())} / ${formatDuration(durationMs.toLong())}", color = Color.White)
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = {
                    val view = videoView ?: return@IconButton
                    if (view.isPlaying) {
                        view.pause()
                        isPlaying = false
                    } else {
                        view.start()
                        isPlaying = true
                    }
                },
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
                            videoView?.seekTo(segment.startMs.toInt())
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

@Composable
fun SubtitleSegmentRow(
    segment: SubtitleSegment,
    selected: Boolean,
    onClick: () -> Unit,
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
        Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFFBDBDBD), modifier = Modifier.size(18.dp))
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
fun RecognitionModeSelector(
    selected: RecognitionMode,
    onSelect: (RecognitionMode) -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("视频识别模式", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                RecognitionMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selected == mode,
                        onClick = { onSelect(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }
            Text(
                when (selected) {
                    RecognitionMode.MandarinAccurate -> "默认推荐，适合普通话课程、访谈、会议和讲解视频。"
                    RecognitionMode.DialectEnhanced -> "适合普通话不标准、粤语、中英混合或口音较重的视频。"
                    RecognitionMode.FastFallback -> "仅作低性能手机备用，准确率低于前两个模式。"
                },
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

private fun startSpeech(context: Context, recognizer: SpeechRecognizer) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINESE.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }
    recognizer.startListening(intent)
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "分享文字"))
}

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

private fun typeLabel(type: ConversionType): String = when (type) {
    ConversionType.Video -> "视频"
    ConversionType.Audio -> "语音"
    ConversionType.Image -> "图片"
}

private fun statusLabel(status: ConversionStatus): String = when (status) {
    ConversionStatus.Success -> "成功"
    ConversionStatus.Failed -> "失败"
    ConversionStatus.Pending -> "待处理"
}

private fun timeLabel(time: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(time))
