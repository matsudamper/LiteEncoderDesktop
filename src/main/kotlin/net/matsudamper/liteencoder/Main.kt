package net.matsudamper.liteencoder

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.vinceglb.filekit.FileKit
import net.matsudamper.liteencoder.ui.MainScreenRoot

fun main() {
    FileKit.init(appId = "LiteEncoderDesktop")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "LiteEncoderDesktop",
            state = rememberWindowState(size = DpSize(1280.dp, 800.dp)),
        ) {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreenRoot(window = window)
                }
            }
        }
    }
}
