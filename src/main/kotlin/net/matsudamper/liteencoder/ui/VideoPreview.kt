package net.matsudamper.liteencoder.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
internal fun VideoPreview(
    uiState: PreviewUiState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val frame = uiState.frame
            val errorMessage = uiState.errorMessage
            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            } else if (frame != null) {
                Image(
                    bitmap = frame,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CircularProgressIndicator()
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalButton(onClick = uiState.event::onPlayPauseClick) {
                Text(if (uiState.isPlaying) "一時停止" else "再生")
            }
            Slider(
                value = uiState.positionSeconds,
                onValueChange = uiState.event::onSeek,
                valueRange = 0f..uiState.durationSeconds,
                modifier = Modifier.weight(1f),
            )
            Text(uiState.timeText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
