package net.matsudamper.liteencoder.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import java.io.File

@Immutable
data class MainUiState(
    val content: Content,
    val event: Event,
) {
    sealed interface Content {
        data object CheckingFFmpeg : Content

        data class FFmpegMissing(
            val installCommand: String,
            val isInstallCommandCopied: Boolean,
        ) : Content

        data class Ready(
            val isFileDropEnabled: Boolean,
            val source: Source?,
        ) : Content
    }

    data class Source(
        val fileName: String,
        val isOpenAnotherFileEnabled: Boolean,
        val state: SourceState,
    )

    sealed interface SourceState {
        data object Loading : SourceState

        data class Error(val message: String) : SourceState

        data class Loaded(
            val summary: String,
            val preview: PreviewUiState,
            val settings: EncodeSettingsUiState,
            val export: ExportUiState,
        ) : SourceState
    }

    @Immutable
    interface Event {
        fun onRetryFFmpegCheckClick()
        fun onCopyInstallCommandClick()
        fun onPickFileClick()
        fun onFileDropped(file: File)
    }
}

@Immutable
data class PreviewUiState(
    val frame: ImageBitmap?,
    val positionSeconds: Float,
    val durationSeconds: Float,
    val timeText: String,
    val isPlaying: Boolean,
    val event: Event,
) {
    @Immutable
    interface Event {
        fun onPlayPauseClick()
        fun onSeek(seconds: Float)
    }
}

@Immutable
data class EncodeSettingsUiState(
    val isEnabled: Boolean,
    val resolutionOptions: List<OptionUiState>,
    val outputSizeText: String,
    val frameRateOptions: List<OptionUiState>,
    val bitRateOptions: List<OptionUiState>,
    val sourceBitRateText: String?,
    val quality: QualityUiState?,
    val customBitRateText: String,
    val event: Event,
) {
    data class QualityUiState(
        val crf: Float,
        val label: String,
        val crfRange: ClosedFloatingPointRange<Float>,
        val steps: Int,
    )

    @Immutable
    interface Event {
        fun onQualityChange(crf: Float)
        fun onCustomBitRateChange(text: String)
    }
}

@Immutable
data class OptionUiState(
    val label: String,
    val isSelected: Boolean,
    val isEnabled: Boolean,
    val event: Event,
) {
    @Immutable
    fun interface Event {
        fun onClick()
    }
}

@Immutable
data class ExportUiState(
    val status: Status,
    val event: Event,
) {
    sealed interface Status {
        data object Idle : Status

        data class Running(val progress: Float, val progressText: String) : Status

        data class Done(val message: String) : Status

        data class Failed(val message: String) : Status
    }

    @Immutable
    interface Event {
        fun onExportClick()
        fun onCancelClick()
        fun onRevealOutputClick()
    }
}
