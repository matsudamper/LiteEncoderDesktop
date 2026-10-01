package net.matsudamper.liteencoder.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.matsudamper.liteencoder.ffmpeg.EncodeSettings
import net.matsudamper.liteencoder.ffmpeg.Encoder
import net.matsudamper.liteencoder.ffmpeg.FFmpegLocator
import net.matsudamper.liteencoder.ffmpeg.FFmpegPaths
import net.matsudamper.liteencoder.ffmpeg.VideoInfo
import net.matsudamper.liteencoder.ffmpeg.VideoProbe
import java.io.File

sealed interface FFmpegState {
    data object Checking : FFmpegState
    data object Missing : FFmpegState
    data class Available(val paths: FFmpegPaths) : FFmpegState
}

sealed interface SourceState {
    val file: File

    data class Loading(override val file: File) : SourceState
    data class Loaded(override val file: File, val info: VideoInfo) : SourceState
    data class Error(override val file: File, val message: String) : SourceState
}

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val output: File, val progress: Float) : ExportState
    data class Done(val output: File) : ExportState
    data class Failed(val message: String) : ExportState
}

class AppState(private val scope: CoroutineScope) {
    var ffmpegState: FFmpegState by mutableStateOf(FFmpegState.Checking)
        private set
    var source: SourceState? by mutableStateOf(null)
        private set
    var settings: EncodeSettings by mutableStateOf(EncodeSettings())
    var exportState: ExportState by mutableStateOf(ExportState.Idle)
        private set

    val isExporting: Boolean get() = exportState is ExportState.Running

    private var loadJob: Job? = null
    private var exportJob: Job? = null

    fun checkFFmpeg() {
        ffmpegState = FFmpegState.Checking
        scope.launch {
            val paths = withContext(Dispatchers.IO) { FFmpegLocator.locate() }
            ffmpegState = if (paths != null) FFmpegState.Available(paths) else FFmpegState.Missing
        }
    }

    fun openFile(file: File) {
        val paths = (ffmpegState as? FFmpegState.Available)?.paths ?: return
        if (isExporting) return
        loadJob?.cancel()
        source = SourceState.Loading(file)
        settings = EncodeSettings()
        exportState = ExportState.Idle
        loadJob = scope.launch {
            val result = withContext(Dispatchers.IO) { VideoProbe.probe(paths, file) }
            source = result.fold(
                onSuccess = { SourceState.Loaded(file, it) },
                onFailure = { SourceState.Error(file, it.message ?: "読み込みに失敗しました") },
            )
        }
    }

    fun export(output: File) {
        val paths = (ffmpegState as? FFmpegState.Available)?.paths ?: return
        val loaded = source as? SourceState.Loaded ?: return
        if (isExporting) return
        if (output.absoluteFile == loaded.file.absoluteFile) {
            exportState = ExportState.Failed("入力ファイルと同じファイルには書き出せません")
            return
        }
        exportState = ExportState.Running(output, 0f)
        exportJob = scope.launch {
            val result = Encoder.encode(
                paths = paths,
                input = loaded.file,
                output = output,
                info = loaded.info,
                settings = settings,
                onProgress = { progress ->
                    // エクスポートのJobに紐づけてMainで更新し、キャンセル後に古い進捗で上書きされないようにする
                    withContext(Dispatchers.Main) { exportState = ExportState.Running(output, progress) }
                },
            )
            exportState = result.fold(
                onSuccess = { ExportState.Done(output) },
                onFailure = { ExportState.Failed(it.message ?: "書き出しに失敗しました") },
            )
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        exportState = ExportState.Idle
    }
}
