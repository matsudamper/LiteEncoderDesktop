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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.awt.Window
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File

@Composable
fun MainScreenRoot(window: Window) {
    val viewModel = remember(window) { MainViewModel(FileKitDialogs(window)) }
    DisposableEffect(viewModel) {
        onDispose { viewModel.dispose() }
    }
    val uiState by viewModel.uiState.collectAsState()
    MainScreen(uiState = uiState)
}

@Composable
internal fun MainScreen(uiState: MainUiState) {
    when (val content = uiState.content) {
        MainUiState.Content.CheckingFFmpeg -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is MainUiState.Content.FFmpegMissing -> FFmpegMissingScreen(
            uiState = content,
            onCopyClick = uiState.event::onCopyInstallCommandClick,
            onRetryClick = uiState.event::onRetryFFmpegCheckClick,
        )

        is MainUiState.Content.Ready -> ReadyScreen(
            uiState = content,
            mainEvent = uiState.event,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ReadyScreen(
    uiState: MainUiState.Content.Ready,
    mainEvent: MainUiState.Event,
) {
    var isDragging by remember { mutableStateOf(false) }
    val dropTarget = remember(mainEvent) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                isDragging = true
            }

            override fun onExited(event: DragAndDropEvent) {
                isDragging = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                isDragging = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                isDragging = false
                val file = event.awtTransferable.droppedFiles().firstOrNull()
                return if (file != null) {
                    mainEvent.onFileDropped(file)
                    true
                } else {
                    false
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .dragAndDropTarget(
                shouldStartDragAndDrop = { dragEvent ->
                    uiState.isFileDropEnabled &&
                        dragEvent.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                },
                target = dropTarget,
            ),
    ) {
        val source = uiState.source
        if (source == null) {
            DropZone(onPickFileClick = mainEvent::onPickFileClick, modifier = Modifier.fillMaxSize().padding(24.dp))
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
                    when (val sourceState = source.state) {
                        MainUiState.SourceState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        is MainUiState.SourceState.Error -> Text(
                            "読み込みに失敗しました\n${sourceState.message}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center),
                        )

                        is MainUiState.SourceState.Loaded -> VideoPreview(
                            uiState = sourceState.preview,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                SidePanel(
                    source = source,
                    onPickFileClick = mainEvent::onPickFileClick,
                    modifier = Modifier.width(380.dp).fillMaxHeight(),
                )
            }
        }
        if (isDragging) {
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
private fun DropZone(onPickFileClick: () -> Unit, modifier: Modifier = Modifier) {
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
        Button(onClick = onPickFileClick) {
            Text("ファイルを選択")
        }
    }
}

@Composable
private fun SidePanel(
    source: MainUiState.Source,
    onPickFileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            source.fileName,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val sourceState = source.state
        if (sourceState is MainUiState.SourceState.Loaded) {
            Text(sourceState.summary, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton(onClick = onPickFileClick, enabled = source.isOpenAnotherFileEnabled) {
            Text("別のファイルを開く")
        }
        HorizontalDivider()
        if (sourceState is MainUiState.SourceState.Loaded) {
            EncodeSettingsPanel(
                uiState = sourceState.settings,
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            )
            HorizontalDivider()
            ExportSection(uiState = sourceState.export)
        }
    }
}

@Composable
private fun ExportSection(uiState: ExportUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (val status = uiState.status) {
            is ExportUiState.Status.Running -> {
                Text(status.progressText)
                LinearProgressIndicator(
                    progress = { status.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = uiState.event::onCancelClick, modifier = Modifier.fillMaxWidth()) {
                    Text("キャンセル")
                }
            }

            is ExportUiState.Status.Done -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        status.message,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = uiState.event::onRevealOutputClick) {
                        Text("フォルダを開く")
                    }
                }
                ExportButton(onClick = uiState.event::onExportClick)
            }

            is ExportUiState.Status.Failed -> {
                SelectionContainer {
                    Text(
                        status.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ExportButton(onClick = uiState.event::onExportClick)
            }

            ExportUiState.Status.Idle -> ExportButton(onClick = uiState.event::onExportClick)
        }
    }
}

@Composable
private fun ExportButton(onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text("書き出し")
    }
}

private fun Transferable.droppedFiles(): List<File> {
    if (!isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return listOf()
    return (getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
        .orEmpty()
        .filterIsInstance<File>()
        .filter { it.isFile }
}
