package net.matsudamper.liteencoder.ffmpeg

import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

data class VideoInfo(
    val displayWidth: Int,
    val displayHeight: Int,
    val frameRate: Double,
    val durationSeconds: Double,
    val bitRateKbps: Int?,
) {
    val shortSide: Int get() = minOf(displayWidth, displayHeight)
}

object VideoProbe {
    fun probe(paths: FFmpegPaths, file: File): Result<VideoInfo> = runCatching {
        val process = ProcessBuilder(
            paths.ffprobe,
            "-v", "error",
            "-select_streams", "v:0",
            "-show_entries",
            "stream=width,height,avg_frame_rate,r_frame_rate,bit_rate:" +
                "stream_side_data=rotation:stream_tags=rotate:format=duration,bit_rate",
            "-of", "flat",
            file.absolutePath,
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { output.ifBlank { "ffprobe failed" } }

        val values = output.lineSequence()
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index < 0) return@mapNotNull null
                line.substring(0, index) to line.substring(index + 1).trim('"')
            }
            .toMap()
        fun find(suffix: String): String? = values.entries
            .firstOrNull { it.key.endsWith(suffix) }
            ?.value
            ?.takeIf { it.isNotBlank() && it != "N/A" }

        val rawWidth = checkNotNull(find("stream.0.width")?.toIntOrNull()) { "動画ストリームが見つかりません" }
        val rawHeight = checkNotNull(find("stream.0.height")?.toIntOrNull()) { "動画ストリームが見つかりません" }
        val rotation = (find(".rotation") ?: find("tags.rotate"))?.toDoubleOrNull()?.roundToInt() ?: 0
        val rotated = abs(rotation) % 180 == 90
        val frameRate = parseRate(find("stream.0.avg_frame_rate"))
            ?: parseRate(find("stream.0.r_frame_rate"))
            ?: 30.0
        val bitRate = (find("stream.0.bit_rate") ?: find("format.bit_rate"))?.toLongOrNull()

        VideoInfo(
            displayWidth = if (rotated) rawHeight else rawWidth,
            displayHeight = if (rotated) rawWidth else rawHeight,
            frameRate = frameRate,
            durationSeconds = find("format.duration")?.toDoubleOrNull() ?: 0.0,
            bitRateKbps = bitRate?.let { (it / 1000).toInt() },
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
