package net.matsudamper.liteencoder.ffmpeg

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

data class VideoInfo(
    val displayWidth: Int,
    val displayHeight: Int,
    val frameRate: Double,
    val durationSeconds: Double,
    val bitRateKbps: Int?,
    val hasAudio: Boolean,
) {
    val shortSide: Int get() = minOf(displayWidth, displayHeight)
}

object VideoProbe {
    suspend fun probe(paths: FFmpegPaths, file: File): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            Result.success(parse(readProbeOutput(paths, file)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun readProbeOutput(paths: FFmpegPaths, file: File): String {
        val process = ProcessBuilder(
            paths.ffprobe,
            "-v", "error",
            "-show_entries",
            "stream=codec_type,width,height,avg_frame_rate,r_frame_rate,bit_rate:" +
                "stream_side_data=rotation:stream_tags=rotate:format=duration,bit_rate",
            "-of", "flat",
            file.absolutePath,
        ).redirectErrorStream(true).start()
        try {
            return destroyOnCancellation(process) {
                val output = process.inputStream.bufferedReader().readText()
                check(process.waitFor() == 0) { output.ifBlank { "ffprobe failed" } }
                output
            }
        } finally {
            process.destroyForcibly()
        }
    }

    private fun parse(output: String): VideoInfo {
        val values = output.lineSequence()
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index < 0) return@mapNotNull null
                line.substring(0, index) to line.substring(index + 1).trim('"')
            }
            .toMap()
        fun find(predicate: (String) -> Boolean): String? = values.entries
            .firstOrNull { predicate(it.key) }
            ?.value
            ?.takeIf { it.isNotBlank() && it != "N/A" }

        val codecTypes = values.entries.filter { it.key.endsWith(".codec_type") }
        val videoStreamPrefix = checkNotNull(codecTypes.firstOrNull { it.value == "video" }) { "動画ストリームが見つかりません" }
            .key
            .removeSuffix("codec_type")
        fun findVideo(name: String): String? = find { it == videoStreamPrefix + name }

        val rawWidth = checkNotNull(findVideo("width")?.toIntOrNull()) { "動画ストリームが見つかりません" }
        val rawHeight = checkNotNull(findVideo("height")?.toIntOrNull()) { "動画ストリームが見つかりません" }
        val rotation = (
            find { it.startsWith(videoStreamPrefix) && it.endsWith(".rotation") } ?: findVideo("tags.rotate")
            )?.toDoubleOrNull()?.roundToInt() ?: 0
        val rotated = abs(rotation) % 180 == 90
        val frameRate = parseRate(findVideo("avg_frame_rate"))
            ?: parseRate(findVideo("r_frame_rate"))
            ?: 30.0
        val bitRate = (findVideo("bit_rate") ?: find { it == "format.bit_rate" })?.toLongOrNull()

        return VideoInfo(
            displayWidth = if (rotated) rawHeight else rawWidth,
            displayHeight = if (rotated) rawWidth else rawHeight,
            frameRate = frameRate,
            durationSeconds = find { it == "format.duration" }?.toDoubleOrNull() ?: 0.0,
            bitRateKbps = bitRate?.let { (it / 1000).toInt() },
            hasAudio = codecTypes.any { it.value == "audio" },
        )
    }

    private fun parseRate(value: String?): Double? {
        value ?: return null
        val parts = value.split('/')
        val rate = when (parts.size) {
            1 -> parts[0].toDoubleOrNull()
            2 -> {
                val num = parts[0].toDoubleOrNull() ?: return null
                val den = parts[1].toDoubleOrNull() ?: return null
                if (den == 0.0) null else num / den
            }
            else -> null
        }
        return rate?.takeIf { it > 0 }
    }
}
