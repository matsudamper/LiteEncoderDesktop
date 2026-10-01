package net.matsudamper.liteencoder.ffmpeg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * プロセスの標準出力の読み取りはブロッキングでキャンセルに反応しないため、
 * キャンセル時にプロセスを破棄してストリームを閉じ、読み取りを抜けさせる。
 */
internal suspend fun <T> destroyOnCancellation(process: Process, block: suspend () -> T): T = coroutineScope {
    val destroyer = launch(Dispatchers.IO) {
        try {
            awaitCancellation()
        } finally {
            process.destroyForcibly()
        }
    }
    try {
        block()
    } finally {
        destroyer.cancel()
    }
}
