package net.matsudamper.liteencoder.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitDialogParent
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.awt.Window
import java.awt.datatransfer.DataFlavor
import java.io.File

@Composable
fun App(window: Window) {
    val scope = rememberCoroutineScope()
    val state = remember { AppState(scope) }
    LaunchedEffect(Unit) { state.checkFFmpeg() }

    when (val ffmpeg = state.ffmpegState) {
        FFmpegState.Checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        FFmpegState.Missing -> FFmpegMissingScreen(onRetry = state::checkFFmpeg)

        is FFmpegState.Available -> {
            val dialogSettings = remember(window) {
                FileKitDialogSettings(parent = FileKitDialogParent.awt(window))
            }
            MainScreen(
                state = state,
                onPickFile = {
                    scope.launch {
                        FileKit.openFilePicker(type = FileKitType.Video, dialogSettings = dialogSettings)
                            ?.let { state.openFile(it.file) }
                    }
                },
                onExport = { source ->
                    scope.launch {
                        FileKit.openFileSaver(
                            suggestedName = "${source.nameWithoutExtension}_encoded",
                            extension = "mp4",
                            directory = source.parentFile?.let { io.github.vinceglb.filekit.PlatformFile(it) },
                            dialogSettings = dialogSettings,
                        )?.let { state.export(it.file) }
                    }
                },
                paths = ffmpeg,
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MainScreen(
    state: AppState,
    paths: FFmpegState.Available,
    onPickFile: () -> Unit,
    onExport: (File) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    val dropTarget = remember(state) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                dragging = true
            }

            override fun onExited(event: DragAndDropEvent) {
                dragging = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                dragging = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                dragging = false
                val file = event.awtTransferable.droppedFiles().firstOrNull() ?: return false
                state.openFile(file)
                return true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    !state.isExporting &&
                        event.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                },
                target = dropTarget,
            ),
    ) {
        when (val source = state.source) {
            null -> DropZone(onPickFile = onPickFile, modifier = Modifier.fillMaxSize().padding(24.dp))
            else -> Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
                    when (source) {
                        is SourceState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        is SourceState.Error -> Text(
                            "読み込みに失敗しました\n${source.message}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center),
                        )

                        is SourceState.Loaded -> VideoPreview(
                            paths = paths.paths,
                            file = source.file,
                            info = source.info,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                SidePanel(
                    state = state,
                    source = source,
                    onPickFile = onPickFile,
                    onExport = onExport,
                    modifier = Modifier.width(380.dp).fillMaxHeight(),
                )
            }
        }
        if (dragging) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary)),
                contentAlignment = Alignment.Center,
            ) {
                Text("ドロップして開く", style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

@Composable
private fun DropZone(onPickFile: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.border(
            BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
            RoundedCornerShape(16.dp),
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("動画ファイルをここにドロップ", style = MaterialTheme.typography.headlineSmall)
        Text("または", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onPickFile) {
            Text("ファイルを選択")
        }
    }
}

@Composable
private fun SidePanel(
    state: AppState,
    source: SourceState,
    onPickFile: () -> Unit,
    onExport: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            source.file.name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (source is SourceState.Loaded) {
            val info = source.info
            Text(
                "${info.displayWidth} × ${info.displayHeight} / %.2f fps / ${formatTime(info.durationSeconds)}".format(info.frameRate),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedButton(onClick = onPickFile, enabled = !state.isExporting) {
            Text("別のファイルを開く")
        }
        HorizontalDivider()
        if (source is SourceState.Loaded) {
            EncodeSettingsPanel(
                info = source.info,
                settings = state.settings,
                enabled = !state.isExporting,
                onSettingsChange = { state.settings = it },
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            )
            HorizontalDivider()
            ExportSection(
                exportState = state.exportState,
                onExport = { onExport(source.file) },
                onCancel = state::cancelExport,
            )
        }
    }
}

@Composable
private fun ExportSection(
    exportState: ExportState,
    onExport: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (exportState) {
            is ExportState.Running -> {
                Text("書き出し中… ${(exportState.progress * 100).toInt()}%")
                LinearProgressIndicator(
                    progress = { exportState.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("キャンセル")
                }
                return@Column
            }

            is ExportState.Done -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "書き出し完了: ${exportState.output.name}",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { revealInExplorer(exportState.output) }) {
                    Text("フォルダを開く")
                }
            }

            is ExportState.Failed -> SelectionContainer {
                Text(
                    exportState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            ExportState.Idle -> Unit
        }
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
            Text("書き出し")
        }
    }
}

private fun java.awt.datatransfer.Transferable.droppedFiles(): List<File> {
    if (!isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return emptyList()
    return (getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
        .orEmpty()
        .filterIsInstance<File>()
        .filter { it.isFile }
}

private fun revealInExplorer(file: File) {
    runCatching {
        ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
    }.onFailure {
        file.parentFile?.let { Desktop.getDesktop().open(it) }
    }
}
