package net.matsudamper.liteencoder.ffmpeg

import java.io.File
import java.util.concurrent.TimeUnit

data class FFmpegPaths(
    val ffmpeg: String,
    val ffprobe: String,
)

object FFmpegLocator {
    const val WINGET_INSTALL_COMMAND = "winget install --id Gyan.FFmpeg -e"

    fun locate(): FFmpegPaths? {
        val candidates = buildList {
            add(FFmpegPaths(ffmpeg = "ffmpeg", ffprobe = "ffprobe"))
            // winget直後は起動中プロセスのPATHが更新されないため、wingetのLinksディレクトリも探す
            System.getenv("LOCALAPPDATA")?.let { localAppData ->
                val links = File(localAppData, "Microsoft\\WinGet\\Links")
                add(
                    FFmpegPaths(
                        ffmpeg = File(links, "ffmpeg.exe").absolutePath,
                        ffprobe = File(links, "ffprobe.exe").absolutePath,
                    ),
                )
            }
        }
        return candidates.firstOrNull { isExecutable(it.ffmpeg) && isExecutable(it.ffprobe) }
    }

    private fun isExecutable(command: String): Boolean {
        return runCatching {
            val process = ProcessBuilder(command, "-version")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                false
            } else {
                process.exitValue() == 0
            }
        }.getOrDefault(false)
    }
}
