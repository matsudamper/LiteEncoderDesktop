package net.matsudamper.liteencoder.ffmpeg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

private const val SAMPLE_RATE = 48000
private const val CHANNELS = 2
private const val BYTES_PER_SAMPLE = 2

object AudioPlayer {
    /**
     * 再生が終わるかキャンセルされるまで中断する。
     * 再生速度は SourceDataLine への書き込みがブロックされることで保たれる。
     */
    suspend fun play(paths: FFmpegPaths, file: File, startSeconds: Double) = withContext(Dispatchers.IO) {
        val format = AudioFormat(SAMPLE_RATE.toFloat(), BYTES_PER_SAMPLE * 8, CHANNELS, true, false)
        val process = ProcessBuilder(
            paths.ffmpeg, "-v", "error", "-nostdin",
            "-ss", String.format(Locale.US, "%.3f", startSeconds), "-i", file.absolutePath,
            "-vn", "-sn",
            "-f", "s16le", "-acodec", "pcm_s16le", "-ar", SAMPLE_RATE.toString(), "-ac", CHANNELS.toString(),
            "pipe:1",
        ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val line = AudioSystem.getSourceDataLine(format)
        try {
            destroyOnCancellation(process) {
                line.open(format)
                line.start()
                val input = process.inputStream
                val buffer = ByteArray(SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE / 10)
                while (true) {
                    val read = try {
                        input.readNBytes(buffer, 0, buffer.size)
                    } catch (e: IOException) {
                        // キャンセルでプロセスを破棄した場合はストリームが閉じられるので、キャンセルとして扱う
                        currentCoroutineContext().ensureActive()
                        throw e
                    }
                    if (read <= 0) break
                    currentCoroutineContext().ensureActive()
                    line.write(buffer, 0, read - read % format.frameSize)
                }
                line.drain()
            }
        } finally {
            line.stop()
            line.flush()
            line.close()
            process.destroyForcibly()
        }
    }
}
