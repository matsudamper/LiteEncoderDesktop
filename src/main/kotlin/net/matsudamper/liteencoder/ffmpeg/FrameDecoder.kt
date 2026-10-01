package net.matsudamper.liteencoder.ffmpeg

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.File
import java.util.Locale

object FrameDecoder {
    data class Frame(
        val image: ImageBitmap,
        val positionSeconds: Double,
    )

    fun decode(
        paths: FFmpegPaths,
        file: File,
        startSeconds: Double,
        size: Size,
        frameRate: Double,
        throttleToPlaybackSpeed: Boolean,
        maxFrames: Int?,
    ): Flow<Frame> = flow {
        val command = buildList {
            addAll(listOf(paths.ffmpeg, "-v", "error", "-nostdin"))
            if (throttleToPlaybackSpeed) add("-re")
            addAll(listOf("-ss", String.format(Locale.US, "%.3f", startSeconds), "-i", file.absolutePath))
            addAll(listOf("-an", "-sn", "-vf", "scale=${size.width}:${size.height}"))
            if (maxFrames != null) {
                addAll(listOf("-frames:v", maxFrames.toString()))
            }
            addAll(listOf("-f", "rawvideo", "-pix_fmt", "rgba", "pipe:1"))
        }
        val process = ProcessBuilder(command)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        try {
            destroyOnCancellation(process) {
                val input = DataInputStream(process.inputStream.buffered(size.width * size.height * 4))
                val imageInfo = ImageInfo(size.width, size.height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
                var index = 0
                while (true) {
                    val bytes = ByteArray(size.width * size.height * 4)
                    try {
                        input.readFully(bytes)
                    } catch (_: EOFException) {
                        break
                    } catch (e: IOException) {
                        // キャンセルでプロセスを破棄した場合はストリームが閉じられるので、キャンセルとして扱う
                        currentCoroutineContext().ensureActive()
                        throw e
                    }
                    val image = Image.makeRaster(imageInfo, bytes, size.width * 4).toComposeImageBitmap()
                    emit(Frame(image = image, positionSeconds = startSeconds + index / frameRate))
                    index++
                }
                val exitCode = process.waitFor()
                currentCoroutineContext().ensureActive()
                check(exitCode == 0 || index > 0) { "プレビューのデコードに失敗しました (exit code: $exitCode)" }
            }
        } finally {
            process.destroyForcibly()
        }
    }.flowOn(Dispatchers.IO)
}
