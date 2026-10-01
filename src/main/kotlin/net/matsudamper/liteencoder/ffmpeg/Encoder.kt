package net.matsudamper.liteencoder.ffmpeg

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.concurrent.thread

object Encoder {
    /**
     * 一時ファイルに書き出し、成功した場合のみ[output]へ置き換える。
     * キャンセル・失敗時に既存の[output]を壊さないため。
     *
     * @param onProgress 0.0〜1.0。エンコード処理のコルーチン上で呼ばれる
     */
    suspend fun encode(
        paths: FFmpegPaths,
        input: File,
        output: File,
        info: VideoInfo,
        settings: EncodeSettings,
        onProgress: suspend (Float) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val temp = File(output.parentFile, ".${output.name}.${System.currentTimeMillis()}.tmp")
        var process: Process? = null
        try {
            process = ProcessBuilder(buildCommand(paths, input, temp, info, settings)).start()
            val errorLines = ArrayDeque<String>()
            val errorReader = thread(isDaemon = true) {
                process.errorStream.bufferedReader().forEachLine { line ->
                    synchronized(errorLines) {
                        errorLines.addLast(line)
                        if (errorLines.size > 20) errorLines.removeFirst()
                    }
                }
            }
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
            if (exitCode != 0) {
                val message = synchronized(errorLines) { errorLines.joinToString("\n") }
                error(message.ifBlank { "ffmpeg exited with $exitCode" })
            }
            Files.move(temp.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
            onProgress(1f)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            process?.let {
                it.destroyForcibly()
                it.waitFor()
            }
            temp.delete()
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
        // 元のサイズでも奇数サイズはlibx264/yuv420pで扱えないため、常に偶数サイズへスケールする
        val size = settings.resolution.outputSize(info)
        addAll(listOf("-vf", "scale=${size.width}:${size.height}"))
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
        addAll(listOf("-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", "-f", "mp4"))
        add(output.absolutePath)
    }
}
