package net.matsudamper.liteencoder.ui

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.matsudamper.liteencoder.ffmpeg.BitRateSetting
import net.matsudamper.liteencoder.ffmpeg.EncodeSettings
import net.matsudamper.liteencoder.ffmpeg.Encoder
import net.matsudamper.liteencoder.ffmpeg.FFmpegLocator
import net.matsudamper.liteencoder.ffmpeg.FFmpegPaths
import net.matsudamper.liteencoder.ffmpeg.FrameDecoder
import net.matsudamper.liteencoder.ffmpeg.FrameRatePreset
import net.matsudamper.liteencoder.ffmpeg.ResolutionPreset
import net.matsudamper.liteencoder.ffmpeg.Size
import net.matsudamper.liteencoder.ffmpeg.SizeEstimator
import net.matsudamper.liteencoder.ffmpeg.VideoInfo
import net.matsudamper.liteencoder.ffmpeg.VideoProbe
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.nio.file.Files
import kotlin.math.roundToInt

private const val PREVIEW_MAX_WIDTH = 960
private const val PREVIEW_MAX_HEIGHT = 540
private const val MIN_CRF = 16
private const val MAX_CRF = 35

@Stable
class MainViewModel(
    private val fileDialogs: FileDialogs,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val viewModelState = MutableStateFlow(
        ViewModelState(
            ffmpeg = FFmpegState.Checking,
            isInstallCommandCopied = false,
            source = null,
            settings = EncodeSettings.Initial,
            customBitRateText = "",
            export = ExportState.Idle,
            preview = PreviewState.Initial,
            sizeEstimate = SizeEstimateState.None,
        ),
    )

    private var loadJob: Job? = null
    private var exportJob: Job? = null
    private var playJob: Job? = null
    private var seekJob: Job? = null

    private val mainEvent = object : MainUiState.Event {
        override fun onRetryFFmpegCheckClick() {
            checkFFmpeg()
        }

        override fun onCopyInstallCommandClick() {
            Toolkit.getDefaultToolkit().systemClipboard
                .setContents(StringSelection(FFmpegLocator.WINGET_INSTALL_COMMAND), null)
            viewModelState.update { it.copy(isInstallCommandCopied = true) }
        }

        override fun onPickFileClick() {
            scope.launch {
                val file = fileDialogs.pickVideo()
                if (file != null) {
                    openFile(file)
                }
            }
        }

        override fun onFileDropped(file: File) {
            openFile(file)
        }
    }

    private val previewEvent = object : PreviewUiState.Event {
        override fun onPlayPauseClick() {
            if (viewModelState.value.preview.isPlaying) {
                stopPlayback()
            } else {
                startPlayback()
            }
        }

        override fun onSeek(seconds: Float) {
            stopPlayback()
            viewModelState.update { it.copy(preview = it.preview.copy(positionSeconds = seconds.toDouble())) }
            // スライダー操作中に連続でデコードしないよう少し待つ
            showFrameAt(seconds.toDouble(), debounceMillis = 80)
        }
    }

    private val settingsEvent = object : EncodeSettingsUiState.Event {
        override fun onQualityChange(crf: Float) {
            viewModelState.update {
                it.copy(settings = it.settings.copy(bitRate = BitRateSetting.Quality(crf.roundToInt())))
            }
        }

        override fun onCustomBitRateChange(text: String) {
            val digits = text.filter { it.isDigit() }.take(6)
            val kbps = digits.toIntOrNull()?.takeIf { it > 0 }
            // 空欄のまま以前のカスタム値で書き出されないよう、空欄なら自動（品質指定）に戻す
            val bitRate = if (kbps != null) {
                BitRateSetting.Constant(kbps)
            } else {
                BitRateSetting.Quality(BitRateSetting.DEFAULT_CRF)
            }
            viewModelState.update {
                it.copy(customBitRateText = digits, settings = it.settings.copy(bitRate = bitRate))
            }
        }
    }

    private val exportEvent = object : ExportUiState.Event {
        override fun onExportClick() {
            val loaded = viewModelState.value.source as? SourceState.Loaded ?: return
            scope.launch {
                val output = fileDialogs.pickExportDestination(
                    suggestedName = "${loaded.file.nameWithoutExtension}_encoded",
                    directory = loaded.file.parentFile,
                )
                if (output != null) {
                    export(output)
                }
            }
        }

        override fun onCancelClick() {
            exportJob?.cancel()
            exportJob = null
            viewModelState.update { it.copy(export = ExportState.Idle) }
        }

        override fun onRevealOutputClick() {
            val done = viewModelState.value.export as? ExportState.Done ?: return
            revealInExplorer(done.output)
        }
    }

    private val resolutionOptionEvents = ResolutionPreset.entries.associateWith { preset ->
        OptionUiState.Event {
            viewModelState.update { it.copy(settings = it.settings.copy(resolution = preset)) }
        }
    }

    private val frameRateOptionEvents = FrameRatePreset.entries.associateWith { preset ->
        OptionUiState.Event {
            viewModelState.update { it.copy(settings = it.settings.copy(frameRate = preset)) }
        }
    }

    private val qualityOptionEvent = OptionUiState.Event {
        viewModelState.update {
            it.copy(
                customBitRateText = "",
                settings = it.settings.copy(bitRate = BitRateSetting.Quality(BitRateSetting.DEFAULT_CRF)),
            )
        }
    }

    private val kbpsOptionEvents = BitRateSetting.kbpsPresets.associateWith { kbps ->
        OptionUiState.Event {
            viewModelState.update {
                it.copy(
                    customBitRateText = "",
                    settings = it.settings.copy(bitRate = BitRateSetting.Constant(kbps)),
                )
            }
        }
    }

    val uiState: StateFlow<MainUiState> = viewModelState
        .map { createUiState(it) }
        .stateIn(scope, SharingStarted.Eagerly, createUiState(viewModelState.value))

    init {
        checkFFmpeg()
        scope.launch {
            viewModelState
                .map { state ->
                    val loaded = state.source as? SourceState.Loaded
                    if (loaded != null) EstimateInput(loaded.file, loaded.info, state.settings) else null
                }
                .distinctUntilChanged()
                .collectLatest { input -> estimateSize(input) }
        }
    }

    fun dispose() {
        scope.cancel()
    }

    private fun checkFFmpeg() {
        viewModelState.update { it.copy(ffmpeg = FFmpegState.Checking) }
        scope.launch {
            val paths = withContext(Dispatchers.IO) { FFmpegLocator.locate() }
            viewModelState.update {
                it.copy(ffmpeg = if (paths != null) FFmpegState.Available(paths) else FFmpegState.Missing)
            }
        }
    }

    private fun openFile(file: File) {
        val paths = (viewModelState.value.ffmpeg as? FFmpegState.Available)?.paths ?: return
        if (viewModelState.value.export is ExportState.Running) return
        loadJob?.cancel()
        stopPlayback()
        seekJob?.cancel()
        viewModelState.update {
            it.copy(
                source = SourceState.Loading(file),
                settings = EncodeSettings.Initial,
                customBitRateText = "",
                export = ExportState.Idle,
                preview = PreviewState.Initial,
            )
        }
        loadJob = scope.launch {
            val result = VideoProbe.probe(paths, file)
            viewModelState.update { state ->
                state.copy(
                    source = result.fold(
                        onSuccess = { SourceState.Loaded(file, it, previewSize(it)) },
                        onFailure = { SourceState.Error(file, it.message ?: "読み込みに失敗しました") },
                    ),
                )
            }
            if (result.isSuccess) {
                showFrameAt(0.0, debounceMillis = 0)
            }
        }
    }

    private fun export(output: File) {
        val paths = (viewModelState.value.ffmpeg as? FFmpegState.Available)?.paths ?: return
        val loaded = viewModelState.value.source as? SourceState.Loaded ?: return
        if (viewModelState.value.export is ExportState.Running) return
        if (isSameFile(output, loaded.file)) {
            viewModelState.update { it.copy(export = ExportState.Failed("入力ファイルと同じファイルには書き出せません")) }
            return
        }
        viewModelState.update { it.copy(export = ExportState.Running(0f)) }
        exportJob = scope.launch {
            val result = Encoder.encode(
                paths = paths,
                input = loaded.file,
                output = output,
                info = loaded.info,
                settings = viewModelState.value.settings,
                onProgressRatio = { progress ->
                    // エクスポートのJobに紐づけてMainで更新し、キャンセル後に古い進捗で上書きされないようにする
                    withContext(Dispatchers.Main) {
                        viewModelState.update { it.copy(export = ExportState.Running(progress)) }
                    }
                },
            )
            viewModelState.update { state ->
                state.copy(
                    export = result.fold(
                        onSuccess = { ExportState.Done(output) },
                        onFailure = { ExportState.Failed(it.message ?: "書き出しに失敗しました") },
                    ),
                )
            }
        }
    }

    private suspend fun estimateSize(input: EstimateInput?) {
        if (input == null) {
            viewModelState.update { it.copy(sizeEstimate = SizeEstimateState.None) }
            return
        }
        when (val bitRate = input.settings.bitRate) {
            is BitRateSetting.Constant -> {
                val bytes = SizeEstimator.estimateConstantBitRateBytes(input.info, bitRate.kbps)
                viewModelState.update { it.copy(sizeEstimate = SizeEstimateState.Estimated(bytes)) }
            }

            is BitRateSetting.Quality -> {
                val paths = (viewModelState.value.ffmpeg as? FFmpegState.Available)?.paths ?: return
                viewModelState.update { it.copy(sizeEstimate = SizeEstimateState.Calculating) }
                // スライダー操作中に毎回サンプルエンコードしないよう少し待つ
                delay(500)
                val result = SizeEstimator.estimateBySamplingBytes(paths, input.file, input.info, input.settings)
                viewModelState.update {
                    it.copy(
                        sizeEstimate = result.fold(
                            onSuccess = { bytes -> SizeEstimateState.Estimated(bytes) },
                            onFailure = { SizeEstimateState.Failed },
                        ),
                    )
                }
            }
        }
    }

    private fun startPlayback() {
        val paths = (viewModelState.value.ffmpeg as? FFmpegState.Available)?.paths ?: return
        val loaded = viewModelState.value.source as? SourceState.Loaded ?: return
        seekJob?.cancel()
        val duration = loaded.info.durationSeconds
        val position = viewModelState.value.preview.positionSeconds
        val start = if (position >= duration - 0.1) 0.0 else position
        viewModelState.update { it.copy(preview = it.preview.copy(isPlaying = true)) }
        playJob = scope.launch {
            collectPreviewFrames(
                FrameDecoder.decode(
                    paths = paths,
                    file = loaded.file,
                    startSeconds = start,
                    size = loaded.previewSize,
                    frameRate = loaded.info.frameRate,
                    throttleToPlaybackSpeed = true,
                    maxFrames = null,
                ),
            ) { preview, frame ->
                preview.copy(frame = frame.image, positionSeconds = frame.positionSeconds.coerceAtMost(duration))
            }
            viewModelState.update { it.copy(preview = it.preview.copy(isPlaying = false)) }
        }
    }

    private fun stopPlayback() {
        playJob?.cancel()
        playJob = null
        viewModelState.update { it.copy(preview = it.preview.copy(isPlaying = false)) }
    }

    private fun showFrameAt(seconds: Double, debounceMillis: Long) {
        val paths = (viewModelState.value.ffmpeg as? FFmpegState.Available)?.paths ?: return
        val loaded = viewModelState.value.source as? SourceState.Loaded ?: return
        seekJob?.cancel()
        seekJob = scope.launch {
            delay(debounceMillis)
            collectPreviewFrames(
                FrameDecoder.decode(
                    paths = paths,
                    file = loaded.file,
                    startSeconds = seconds,
                    size = loaded.previewSize,
                    frameRate = loaded.info.frameRate,
                    throttleToPlaybackSpeed = false,
                    maxFrames = 1,
                ),
            ) { preview, frame ->
                preview.copy(frame = frame.image)
            }
        }
    }

    private suspend fun collectPreviewFrames(
        frames: Flow<FrameDecoder.Frame>,
        applyFrame: (PreviewState, FrameDecoder.Frame) -> PreviewState,
    ) {
        try {
            frames.collect { frame ->
                viewModelState.update { it.copy(preview = applyFrame(it.preview, frame).copy(errorMessage = null)) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            viewModelState.update {
                it.copy(preview = it.preview.copy(isPlaying = false, errorMessage = e.message ?: "プレビューを表示できません"))
            }
        }
    }

    private fun createUiState(state: ViewModelState): MainUiState {
        val content = when (val ffmpeg = state.ffmpeg) {
            FFmpegState.Checking -> MainUiState.Content.CheckingFFmpeg
            FFmpegState.Missing -> MainUiState.Content.FFmpegMissing(
                installCommand = FFmpegLocator.WINGET_INSTALL_COMMAND,
                isInstallCommandCopied = state.isInstallCommandCopied,
            )

            is FFmpegState.Available -> MainUiState.Content.Ready(
                isFileDropEnabled = state.export !is ExportState.Running,
                source = state.source?.let { createSourceUiState(state, it) },
            )
        }
        return MainUiState(content = content, event = mainEvent)
    }

    private fun createSourceUiState(state: ViewModelState, source: SourceState): MainUiState.Source {
        val sourceState = when (source) {
            is SourceState.Loading -> MainUiState.SourceState.Loading
            is SourceState.Error -> MainUiState.SourceState.Error(source.message)
            is SourceState.Loaded -> MainUiState.SourceState.Loaded(
                summary = "${source.info.displayWidth} × ${source.info.displayHeight} / %.2f fps / %s".format(
                    source.info.frameRate,
                    formatTime(source.info.durationSeconds),
                ),
                preview = createPreviewUiState(state.preview, source.info),
                settings = createSettingsUiState(state, source.info),
                export = ExportUiState(
                    status = createExportStatus(state.export),
                    estimatedSizeText = createEstimatedSizeText(state.sizeEstimate),
                    event = exportEvent,
                ),
            )
        }
        return MainUiState.Source(
            fileName = source.file.name,
            isOpenAnotherFileEnabled = state.export !is ExportState.Running,
            state = sourceState,
        )
    }

    private fun createPreviewUiState(preview: PreviewState, info: VideoInfo): PreviewUiState {
        return PreviewUiState(
            frame = preview.frame,
            errorMessage = preview.errorMessage,
            positionSeconds = preview.positionSeconds.toFloat(),
            durationSeconds = info.durationSeconds.toFloat().coerceAtLeast(0.01f),
            timeText = "${formatTime(preview.positionSeconds)} / ${formatTime(info.durationSeconds)}",
            isPlaying = preview.isPlaying,
            event = previewEvent,
        )
    }

    private fun createSettingsUiState(state: ViewModelState, info: VideoInfo): EncodeSettingsUiState {
        val settings = state.settings
        val outputSize = settings.resolution.outputSize(info)
        val bitRate = settings.bitRate
        return EncodeSettingsUiState(
            isEnabled = state.export !is ExportState.Running,
            resolutionOptions = ResolutionPreset.entries.map { preset ->
                OptionUiState(
                    label = preset.label,
                    isSelected = settings.resolution == preset,
                    isEnabled = preset.isAvailableFor(info),
                    event = resolutionOptionEvents.getValue(preset),
                )
            },
            outputSizeText = "${outputSize.width} × ${outputSize.height}",
            frameRateOptions = FrameRatePreset.entries.map { preset ->
                OptionUiState(
                    label = if (preset == FrameRatePreset.Original) {
                        "${preset.label} (%.2f fps)".format(info.frameRate)
                    } else {
                        preset.label
                    },
                    isSelected = settings.frameRate == preset,
                    isEnabled = preset.isAvailableFor(info),
                    event = frameRateOptionEvents.getValue(preset),
                )
            },
            bitRateOptions = buildList {
                add(
                    OptionUiState(
                        label = "自動（品質指定）",
                        isSelected = bitRate is BitRateSetting.Quality,
                        isEnabled = true,
                        event = qualityOptionEvent,
                    ),
                )
                BitRateSetting.kbpsPresets.forEach { kbps ->
                    add(
                        OptionUiState(
                            label = formatKbps(kbps),
                            isSelected = bitRate == BitRateSetting.Constant(kbps),
                            isEnabled = true,
                            event = kbpsOptionEvents.getValue(kbps),
                        ),
                    )
                }
            },
            sourceBitRateText = info.bitRateKbps?.let { "元の動画: ${formatKbps(it)}" },
            quality = when (bitRate) {
                is BitRateSetting.Quality -> EncodeSettingsUiState.QualityUiState(
                    crf = bitRate.crf.toFloat(),
                    label = "品質 CRF ${bitRate.crf}（小さいほど高画質・大容量）",
                    crfRange = MIN_CRF.toFloat()..MAX_CRF.toFloat(),
                    steps = MAX_CRF - MIN_CRF - 1,
                )

                is BitRateSetting.Constant -> null
            },
            customBitRateText = state.customBitRateText,
            event = settingsEvent,
        )
    }

    private fun createExportStatus(export: ExportState): ExportUiState.Status {
        return when (export) {
            ExportState.Idle -> ExportUiState.Status.Idle
            is ExportState.Running -> ExportUiState.Status.Running(
                progress = export.progress,
                progressText = "書き出し中… ${(export.progress * 100).toInt()}%",
            )

            is ExportState.Done -> ExportUiState.Status.Done("書き出し完了: ${export.output.name}")
            is ExportState.Failed -> ExportUiState.Status.Failed(export.message)
        }
    }

    private fun createEstimatedSizeText(sizeEstimate: SizeEstimateState): String? {
        return when (sizeEstimate) {
            SizeEstimateState.None -> null
            SizeEstimateState.Calculating -> "推定サイズ: 計算中…"
            is SizeEstimateState.Estimated -> "推定サイズ: 約 ${formatBytes(sizeEstimate.bytes)}"
            SizeEstimateState.Failed -> "推定サイズ: 計算できませんでした"
        }
    }

    // ジャンクションやUNCパスなど別名経由でも入力ファイルを上書きしないよう、実体で比較する
    private fun isSameFile(a: File, b: File): Boolean {
        return if (a.exists() && b.exists()) {
            Files.isSameFile(a.toPath(), b.toPath())
        } else {
            a.absoluteFile == b.absoluteFile
        }
    }

    private fun revealInExplorer(file: File) {
        runCatching {
            ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
        }.onFailure {
            val parent = file.parentFile
            if (parent != null) {
                Desktop.getDesktop().open(parent)
            }
        }
    }

    private data class ViewModelState(
        val ffmpeg: FFmpegState,
        val isInstallCommandCopied: Boolean,
        val source: SourceState?,
        val settings: EncodeSettings,
        val customBitRateText: String,
        val export: ExportState,
        val preview: PreviewState,
        val sizeEstimate: SizeEstimateState,
    )

    private data class EstimateInput(
        val file: File,
        val info: VideoInfo,
        val settings: EncodeSettings,
    )

    private sealed interface SizeEstimateState {
        data object None : SizeEstimateState
        data object Calculating : SizeEstimateState
        data class Estimated(val bytes: Long) : SizeEstimateState
        data object Failed : SizeEstimateState
    }

    private sealed interface FFmpegState {
        data object Checking : FFmpegState
        data object Missing : FFmpegState
        data class Available(val paths: FFmpegPaths) : FFmpegState
    }

    private sealed interface SourceState {
        val file: File

        data class Loading(override val file: File) : SourceState
        data class Loaded(override val file: File, val info: VideoInfo, val previewSize: Size) : SourceState
        data class Error(override val file: File, val message: String) : SourceState
    }

    private sealed interface ExportState {
        data object Idle : ExportState
        data class Running(val progress: Float) : ExportState
        data class Done(val output: File) : ExportState
        data class Failed(val message: String) : ExportState
    }

    private data class PreviewState(
        val frame: ImageBitmap?,
        val positionSeconds: Double,
        val isPlaying: Boolean,
        val errorMessage: String?,
    ) {
        companion object {
            val Initial = PreviewState(frame = null, positionSeconds = 0.0, isPlaying = false, errorMessage = null)
        }
    }
}

private fun previewSize(info: VideoInfo): Size {
    val scale = minOf(
        1.0,
        PREVIEW_MAX_WIDTH.toDouble() / info.displayWidth,
        PREVIEW_MAX_HEIGHT.toDouble() / info.displayHeight,
    )
    return Size(
        width = ((info.displayWidth * scale).roundToInt() / 2 * 2).coerceAtLeast(2),
        height = ((info.displayHeight * scale).roundToInt() / 2 * 2).coerceAtLeast(2),
    )
}

private fun formatTime(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun formatBytes(bytes: Long): String {
    val megabytes = bytes / 1_000_000.0
    return when {
        megabytes >= 1000 -> "%.2f GB".format(megabytes / 1000)
        megabytes >= 100 -> "%.0f MB".format(megabytes)
        else -> "%.1f MB".format(megabytes)
    }
}

private fun formatKbps(kbps: Int): String {
    return if (kbps >= 1000) "%.1f Mbps".format(kbps / 1000.0) else "$kbps kbps"
}
