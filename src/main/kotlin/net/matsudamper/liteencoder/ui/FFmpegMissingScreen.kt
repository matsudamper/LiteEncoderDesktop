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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import net.matsudamper.liteencoder.ffmpeg.FFmpegLocator
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun FFmpegMissingScreen(onRetry: () -> Unit) {
    var copied by remember { mutableStateOf(false) }
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
                    Text(FFmpegLocator.WINGET_INSTALL_COMMAND, fontFamily = FontFamily.Monospace)
                }
                OutlinedButton(
                    onClick = {
                        Toolkit.getDefaultToolkit().systemClipboard
                            .setContents(StringSelection(FFmpegLocator.WINGET_INSTALL_COMMAND), null)
                        copied = true
                    },
                ) {
                    Text(if (copied) "コピーしました" else "コピー")
                }
            }
        }
        Button(onClick = onRetry) {
            Text("再チェック")
        }
    }
}
