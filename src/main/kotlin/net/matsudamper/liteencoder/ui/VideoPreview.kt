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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.matsudamper.liteencoder.ffmpeg.FFmpegPaths
import net.matsudamper.liteencoder.ffmpeg.FrameDecoder
import net.matsudamper.liteencoder.ffmpeg.Size
import net.matsudamper.liteencoder.ffmpeg.VideoInfo
import java.io.File
import kotlin.math.roundToInt

private const val PREVIEW_MAX_WIDTH = 960
private const val PREVIEW_MAX_HEIGHT = 540

@Composable
fun VideoPreview(
    paths: FFmpegPaths,
    file: File,
    info: VideoInfo,
    modifier: Modifier = Modifier,
) {
    val previewSize = remember(info) { previewSize(info) }
    val duration = info.durationSeconds
    var position by remember(file) { mutableStateOf(0.0) }
    var playing by remember(file) { mutableStateOf(false) }
    var frame by remember(file) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(file, playing) {
        if (!playing) return@LaunchedEffect
        val start = if (position >= duration - 0.1) 0.0 else position
        FrameDecoder.decode(
            paths = paths,
            file = file,
            startSeconds = start,
            size = previewSize,
            frameRate = info.frameRate,
            throttleToPlaybackSpeed = true,
        ).collect {
            frame = it.image
            position = it.positionSeconds.coerceAtMost(duration)
        }
        playing = false
    }
    LaunchedEffect(file, position, playing) {
        if (playing) return@LaunchedEffect
        // スライダー操作中に連続でデコードしないよう少し待つ
        delay(80)
        FrameDecoder.decode(
            paths = paths,
            file = file,
            startSeconds = position,
            size = previewSize,
            frameRate = info.frameRate,
            throttleToPlaybackSpeed = false,
            maxFrames = 1,
        ).collect { frame = it.image }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val currentFrame = frame
            if (currentFrame != null) {
                Image(
                    bitmap = currentFrame,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
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
            FilledTonalButton(onClick = { playing = !playing }) {
                Text(if (playing) "一時停止" else "再生")
            }
            Slider(
                value = position.toFloat(),
                onValueChange = {
                    playing = false
                    position = it.toDouble()
                },
                valueRange = 0f..duration.toFloat().coerceAtLeast(0.01f),
                modifier = Modifier.weight(1f),
            )
            Text(
                "${formatTime(position)} / ${formatTime(duration)}",
                style = MaterialTheme.typography.bodyMedium,
            )
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

internal fun formatTime(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
