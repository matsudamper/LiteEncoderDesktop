package net.matsudamper.liteencoder.ffmpeg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.concurrent.thread

object Encoder {
    /**
     * @param onProgress 0.0〜1.0
     */
    suspend fun encode(
        paths: FFmpegPaths,
        input: File,
        output: File,
        info: VideoInfo,
        settings: EncodeSettings,
        onProgress: (Float) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(buildCommand(paths, input, output, info, settings)).start()
        val errorLines = ArrayDeque<String>()
        val errorReader = thread(isDaemon = true) {
            process.errorStream.bufferedReader().forEachLine { line ->
                synchronized(errorLines) {
                    errorLines.addLast(line)
                    if (errorLines.size > 20) errorLines.removeFirst()
                }
            }
        }
        try {
            val durationUs = (info.durationSeconds * 1_000_000).coerceAtLeast(1.0)
            process.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    currentCoroutineContext().ensureActive()
                    val value = line.substringAfter("out_time_us=", "")
                    if (value.isNotEmpty()) {
                        value.toLongOrNull()?.let { onProgress((it / durationUs).toFloat().coerceIn(0f, 1f)) }
                    }
                }
            }
            val exitCode = process.waitFor()
            errorReader.join(1_000)
            if (exitCode == 0) {
                onProgress(1f)
                Result.success(Unit)
            } else {
                val message = synchronized(errorLines) { errorLines.joinToString("\n") }
                Result.failure(IllegalStateException(message.ifBlank { "ffmpeg exited with $exitCode" }))
            }
        } catch (e: Throwable) {
            process.destroyForcibly()
            process.waitFor()
            output.delete()
            throw e
        }
    }

    private fun buildCommand(
        paths: FFmpegPaths,
        input: File,
        output: File,
        info: VideoInfo,
        settings: EncodeSettings,
    ): List<String> = buildList {
        addAll(listOf(paths.ffmpeg, "-y", "-hide_banner", "-nostdin", "-nostats", "-v", "error", "-progress", "pipe:1"))
        addAll(listOf("-i", input.absolutePath))
        if (settings.resolution != ResolutionPreset.Original) {
            val size = settings.resolution.outputSize(info)
            addAll(listOf("-vf", "scale=${size.width}:${size.height}"))
        }
        settings.frameRate.fps?.let { addAll(listOf("-r", it.toString())) }
        addAll(listOf("-c:v", "libx264", "-preset", "medium", "-pix_fmt", "yuv420p"))
        when (val bitRate = settings.bitRate) {
            is BitRateSetting.Quality -> addAll(listOf("-crf", bitRate.crf.toString()))
            is BitRateSetting.Constant -> addAll(
                listOf(
                    "-b:v", "${bitRate.kbps}k",
                    "-maxrate", "${bitRate.kbps}k",
                    "-bufsize", "${bitRate.kbps * 2}k",
                ),
            )
        }
        addAll(listOf("-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart"))
        add(output.absolutePath)
    }
}
