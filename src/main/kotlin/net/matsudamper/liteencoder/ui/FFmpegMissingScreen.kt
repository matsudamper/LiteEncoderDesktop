package net.matsudamper.liteencoder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
internal fun FFmpegMissingScreen(
    uiState: MainUiState.Content.FFmpegMissing,
    onCopyClick: () -> Unit,
    onRetryClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("ffmpeg が見つかりません", style = MaterialTheme.typography.headlineSmall)
        Text("以下のコマンドをターミナルで実行してインストールしてください。")
        Card(modifier = Modifier.widthIn(max = 600.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(uiState.installCommand, fontFamily = FontFamily.Monospace)
                }
                OutlinedButton(onClick = onCopyClick) {
                    Text(if (uiState.isInstallCommandCopied) "コピーしました" else "コピー")
                }
            }
        }
        Button(onClick = onRetryClick) {
            Text("再チェック")
        }
    }
}
