package net.matsudamper.liteencoder.ffmpeg

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object SizeEstimator {
    private const val SAMPLE_COUNT = 3
    private const val SAMPLE_SECONDS = 2.0

    // コンテナのオーバーヘッド分として少し上乗せする
    private const val CONTAINER_OVERHEAD_RATIO = 1.01

    fun estimateConstantBitRateBytes(info: VideoInfo, settings: EncodeSettings, videoKbps: Int): Long {
        val totalKbps = videoKbps + Encoder.audioBitRateKbps(info, settings)
        return (totalKbps * 1000.0 / 8 * info.durationSeconds * CONTAINER_OVERHEAD_RATIO).toLong()
    }

    /**
     * CRFは映像の内容で容量が決まるため、数か所を実際にエンコードして全体の長さへ外挿する。
     */
    suspend fun estimateBySamplingBytes(
        paths: FFmpegPaths,
        input: File,
        info: VideoInfo,
        settings: EncodeSettings,
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val samples = sampleRanges(info.durationSeconds)
            var sampledBytes = 0L
            for (sample in samples) {
                sampledBytes += encodeSampleBytes(paths, input, info, settings, sample)
            }
            val sampledSeconds = samples.sumOf { it.durationSeconds }
            val videoBytesPerSecond = sampledBytes / sampledSeconds
            val audioBytesPerSecond = Encoder.audioBitRateKbps(info, settings) * 1000.0 / 8
            val totalBytes = (videoBytesPerSecond + audioBytesPerSecond) * info.durationSeconds * CONTAINER_OVERHEAD_RATIO
            Result.success(totalBytes.toLong())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sampleRanges(durationSeconds: Double): List<SampleRange> {
        check(durationSeconds > 0) { "動画の長さが取得できません" }
        if (durationSeconds <= SAMPLE_COUNT * SAMPLE_SECONDS * 2) {
            return listOf(SampleRange(startSeconds = 0.0, durationSeconds = durationSeconds))
        }
        return (1..SAMPLE_COUNT).map { index ->
            val center = durationSeconds * index / (SAMPLE_COUNT + 1)
            SampleRange(startSeconds = center - SAMPLE_SECONDS / 2, durationSeconds = SAMPLE_SECONDS)
        }
    }

    private suspend fun encodeSampleBytes(
        paths: FFmpegPaths,
        input: File,
        info: VideoInfo,
        settings: EncodeSettings,
        sample: SampleRange,
    ): Long {
        val command = buildList {
            addAll(listOf(paths.ffmpeg, "-v", "error", "-nostdin"))
            addAll(listOf("-ss", String.format(Locale.US, "%.3f", sample.startSeconds)))
            addAll(listOf("-t", String.format(Locale.US, "%.3f", sample.durationSeconds)))
            addAll(listOf("-i", input.absolutePath))
            addAll(Encoder.videoOutputArgs(info, settings))
            val sampleMuxer = when (settings.format) {
                OutputFormat.WebP -> "webp"
                OutputFormat.Mp4 -> "h264"
            }
            addAll(listOf("-an", "-sn", "-f", sampleMuxer, "pipe:1"))
        }
        val process = ProcessBuilder(command)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        try {
            return destroyOnCancellation(process) {
                val bytes = process.inputStream.use { stream ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        total += read
                    }
                    total
                }
                val exitCode = process.waitFor()
                currentCoroutineContext().ensureActive()
                check(exitCode == 0) { "容量の推定に失敗しました (exit code: $exitCode)" }
                bytes
            }
        } finally {
            process.destroyForcibly()
        }
    }

    private data class SampleRange(
        val startSeconds: Double,
        val durationSeconds: Double,
    )
}
