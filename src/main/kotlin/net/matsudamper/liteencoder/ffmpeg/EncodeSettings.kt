package net.matsudamper.liteencoder.ffmpeg

import java.io.File
import kotlin.math.roundToInt

enum class OutputFormat(val label: String, val extension: String, val supportsConstantBitRate: Boolean) {
    WebP("webp", "webp", supportsConstantBitRate = false),
    Mp4("mp4", "mp4", supportsConstantBitRate = true),
    ;

    fun isSameFormatAs(file: File): Boolean = file.extension.equals(extension, ignoreCase = true)

    companion object {
        fun defaultFor(input: File): OutputFormat = entries.firstOrNull { it.isSameFormatAs(input) } ?: entries.first()
    }
}

enum class ResolutionPreset(val label: String, val shortSide: Int?) {
    Original("元のサイズ", null),
    P2160("2160p (4K)", 2160),
    P1440("1440p", 1440),
    P1080("1080p", 1080),
    P720("720p", 720),
    P480("480p", 480),
    P360("360p", 360),
    ;

    fun isAvailableFor(info: VideoInfo): Boolean = shortSide == null || shortSide <= info.shortSide

    // libx264(yuv420p)は奇数サイズを扱えないため偶数に丸める
    fun outputSize(info: VideoInfo): Size {
        val target = shortSide ?: return Size(info.displayWidth.toEven(), info.displayHeight.toEven())
        val scale = target.toDouble() / info.shortSide
        return Size(
            width = (info.displayWidth * scale).roundToInt().toEven(),
            height = (info.displayHeight * scale).roundToInt().toEven(),
        )
    }

    private fun Int.toEven(): Int = (this / 2 * 2).coerceAtLeast(2)
}

data class Size(val width: Int, val height: Int)

enum class FrameRatePreset(val label: String, val fps: Int?) {
    Original("元のまま", null),
    Fps60("60 fps", 60),
    Fps30("30 fps", 30),
    Fps24("24 fps", 24),
    Fps15("15 fps", 15),
    ;

    fun isAvailableFor(info: VideoInfo): Boolean = fps == null || fps <= info.frameRate + 0.5
}

sealed interface BitRateSetting {
    data class Quality(val crf: Int) : BitRateSetting

    data class Constant(val kbps: Int) : BitRateSetting

    companion object {
        const val DEFAULT_CRF = 23
        val kbpsPresets = listOf(1_000, 2_500, 5_000, 8_000, 12_000, 20_000)
    }
}

data class EncodeSettings(
    val format: OutputFormat,
    val resolution: ResolutionPreset,
    val frameRate: FrameRatePreset,
    val bitRate: BitRateSetting,
) {
    companion object {
        val Initial = EncodeSettings(
            format = OutputFormat.entries.first(),
            resolution = ResolutionPreset.Original,
            frameRate = FrameRatePreset.Original,
            bitRate = BitRateSetting.Quality(BitRateSetting.DEFAULT_CRF),
        )
    }
}
