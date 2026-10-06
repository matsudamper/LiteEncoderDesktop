package net.matsudamper.liteencoder.ffmpeg

object SizeEstimator {
    // MP4コンテナのオーバーヘッド分として少し上乗せする
    private const val CONTAINER_OVERHEAD_RATIO = 1.01

    fun estimateConstantBitRateBytes(info: VideoInfo, videoKbps: Int): Long {
        val totalKbps = videoKbps + audioKbps(info)
        return (totalKbps * 1000.0 / 8 * info.durationSeconds * CONTAINER_OVERHEAD_RATIO).toLong()
    }

    private fun audioKbps(info: VideoInfo): Int = if (info.hasAudio) Encoder.AUDIO_BIT_RATE_KBPS else 0
}
