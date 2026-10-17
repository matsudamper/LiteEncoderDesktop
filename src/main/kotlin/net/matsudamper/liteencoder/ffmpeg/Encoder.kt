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
    const val AUDIO_BIT_RATE_KBPS = 128

    suspend fun encode(
        paths: FFmpegPaths,
        input: File,
        output: File,
        info: VideoInfo,
        settings: EncodeSettings,
        onProgressRatio: suspend (Float) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        // キャンセル・失敗時に既存の出力ファイルを壊さないよう、一時ファイルに書き出してから置き換える
        val temp = File(output.parentFile, ".${output.name}.${System.currentTimeMillis()}.tmp")
        try {
            when (settings.format) {
                OutputFormat.Mp4 -> runFFmpeg(
                    command = buildMp4Command(paths, input, temp, info, settings),
                    durationSeconds = info.durationSeconds,
                    onProgressRatio = onProgressRatio,
                )

                OutputFormat.Gif -> encodeGif(paths, input, temp, info, settings, onProgressRatio)
            }
            Files.move(temp.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
            onProgressRatio(1f)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            temp.delete()
        }
    }

    // split→palettegen の1パスだと palettegen が入力末尾に達するまで全フレームをメモリに溜めるため、パレットを先に作る2パスにする
    private suspend fun encodeGif(
        paths: FFmpegPaths,
        input: File,
        output: File,
        info: VideoInfo,
        settings: EncodeSettings,
        onProgressRatio: suspend (Float) -> Unit,
    ) {
        val palette = File.createTempFile("liteencoder_palette", ".png")
        try {
            runFFmpeg(
                command = buildList {
                    addAll(commandPrefix(paths))
                    addAll(listOf("-i", input.absolutePath))
                    addAll(listOf("-vf", "${gifBaseFilter(info, settings)},palettegen"))
                    addAll(listOf("-update", "1", "-frames:v", "1", palette.absolutePath))
                },
                durationSeconds = info.durationSeconds,
                // palettegen は入力を読み切るまで出力しないため進捗が取れない
                onProgressRatio = {},
            )
            runFFmpeg(
                command = buildList {
                    addAll(commandPrefix(paths))
                    addAll(listOf("-i", input.absolutePath, "-i", palette.absolutePath))
                    addAll(listOf("-lavfi", "${gifBaseFilter(info, settings)}[video];[video][1:v]paletteuse"))
                    addAll(listOf("-an", "-loop", "0", "-f", "gif", output.absolutePath))
                },
                durationSeconds = info.durationSeconds,
                onProgressRatio = onProgressRatio,
            )
        } finally {
            palette.delete()
        }
    }

    private suspend fun runFFmpeg(
        command: List<String>,
        durationSeconds: Double,
        onProgressRatio: suspend (Float) -> Unit,
    ) {
        val process = ProcessBuilder(command).start()
        try {
            destroyOnCancellation(process) {
                val errorLines = ArrayDeque<String>()
                val errorReader = thread(isDaemon = true) {
                    process.errorStream.bufferedReader().forEachLine { line ->
                        synchronized(errorLines) {
                            errorLines.addLast(line)
                            if (errorLines.size > 20) errorLines.removeFirst()
                        }
                    }
                }
                val durationUs = (durationSeconds * 1_000_000).coerceAtLeast(1.0)
                process.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        currentCoroutineContext().ensureActive()
                        val outTimeUs = line.substringAfter("out_time_us=", "").toLongOrNull()
                        if (outTimeUs != null) {
                            onProgressRatio((outTimeUs / durationUs).toFloat().coerceIn(0f, 1f))
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                val exitCode = process.waitFor()
                errorReader.join(1_000)
                if (exitCode != 0) {
                    val message = synchronized(errorLines) { errorLines.joinToString("\n") }
                    error(message.ifBlank { "ffmpeg exited with $exitCode" })
                }
            }
        } finally {
            process.destroyForcibly()
            process.waitFor()
        }
    }

    private fun commandPrefix(paths: FFmpegPaths): List<String> {
        return listOf(paths.ffmpeg, "-y", "-hide_banner", "-nostdin", "-nostats", "-v", "error", "-progress", "pipe:1")
    }

    private fun buildMp4Command(
        paths: FFmpegPaths,
        input: File,
        output: File,
        info: VideoInfo,
        settings: EncodeSettings,
    ): List<String> = buildList {
        addAll(commandPrefix(paths))
        addAll(listOf("-i", input.absolutePath))
        addAll(h264OutputArgs(info, settings))
        if (settings.volumePercent != EncodeSettings.DEFAULT_VOLUME_PERCENT) {
            addAll(listOf("-af", "volume=${settings.volumePercent / 100.0}"))
        }
        addAll(listOf("-c:a", "aac", "-b:a", "${AUDIO_BIT_RATE_KBPS}k", "-movflags", "+faststart", "-f", "mp4"))
        add(output.absolutePath)
    }

    /**
     * 容量推定のサンプルエンコード用。GIFは短い区間だけを扱う前提で1パスにしている。
     */
    internal fun sampleVideoOutputArgs(info: VideoInfo, settings: EncodeSettings): List<String> {
        return when (settings.format) {
            OutputFormat.Mp4 -> h264OutputArgs(info, settings) + listOf("-f", "h264")
            OutputFormat.Gif -> listOf(
                "-vf", "${gifBaseFilter(info, settings)},split[a][b];[a]palettegen[p];[b][p]paletteuse",
                "-f", "gif",
            )
        }
    }

    private fun gifBaseFilter(info: VideoInfo, settings: EncodeSettings): String {
        val size = settings.resolution.outputSize(info)
        return buildList {
            val fps = settings.frameRate.fps
            if (fps != null) {
                add("fps=$fps")
            }
            add("scale=${size.width}:${size.height}:flags=lanczos")
        }.joinToString(",")
    }

    private fun h264OutputArgs(info: VideoInfo, settings: EncodeSettings): List<String> = buildList {
        // 元のサイズでも奇数サイズはlibx264/yuv420pで扱えないため、常に偶数サイズへスケールする
        val size = settings.resolution.outputSize(info)
        addAll(listOf("-vf", "scale=${size.width}:${size.height}"))
        val fps = settings.frameRate.fps
        if (fps != null) {
            addAll(listOf("-r", fps.toString()))
        }
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
    }
}
