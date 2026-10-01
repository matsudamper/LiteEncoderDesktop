package net.matsudamper.liteencoder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.matsudamper.liteencoder.ffmpeg.BitRateSetting
import net.matsudamper.liteencoder.ffmpeg.EncodeSettings
import net.matsudamper.liteencoder.ffmpeg.FrameRatePreset
import net.matsudamper.liteencoder.ffmpeg.ResolutionPreset
import net.matsudamper.liteencoder.ffmpeg.VideoInfo
import kotlin.math.roundToInt

@Composable
fun EncodeSettingsPanel(
    info: VideoInfo,
    settings: EncodeSettings,
    enabled: Boolean,
    onSettingsChange: (EncodeSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Section("サイズ（比率維持）") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ResolutionPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = settings.resolution == preset,
                        onClick = { onSettingsChange(settings.copy(resolution = preset)) },
                        label = { Text(preset.label) },
                        enabled = enabled && preset.isAvailableFor(info),
                    )
                }
            }
            val size = settings.resolution.outputSize(info)
            Text("${size.width} × ${size.height}", style = MaterialTheme.typography.bodySmall)
        }

        Section("フレームレート") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FrameRatePreset.entries.forEach { preset ->
                    FilterChip(
                        selected = settings.frameRate == preset,
                        onClick = { onSettingsChange(settings.copy(frameRate = preset)) },
                        label = {
                            Text(
                                if (preset == FrameRatePreset.Original) {
                                    "${preset.label} (%.2f fps)".format(info.frameRate)
                                } else {
                                    preset.label
                                },
                            )
                        },
                        enabled = enabled && preset.isAvailableFor(info),
                    )
                }
            }
        }

        Section("ビットレート") {
            BitRateSelector(
                info = info,
                bitRate = settings.bitRate,
                enabled = enabled,
                onBitRateChange = { onSettingsChange(settings.copy(bitRate = it)) },
            )
        }
    }
}

@Composable
private fun BitRateSelector(
    info: VideoInfo,
    bitRate: BitRateSetting,
    enabled: Boolean,
    onBitRateChange: (BitRateSetting) -> Unit,
) {
    var customText by remember(info) { mutableStateOf("") }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = bitRate is BitRateSetting.Quality,
            onClick = {
                customText = ""
                onBitRateChange(BitRateSetting.Quality(BitRateSetting.DEFAULT_CRF))
            },
            label = { Text("自動（品質指定）") },
            enabled = enabled,
        )
        BitRateSetting.kbpsPresets.forEach { kbps ->
            FilterChip(
                selected = bitRate == BitRateSetting.Constant(kbps),
                onClick = {
                    customText = ""
                    onBitRateChange(BitRateSetting.Constant(kbps))
                },
                label = { Text(formatKbps(kbps)) },
                enabled = enabled,
            )
        }
    }
    info.bitRateKbps?.let {
        Text("元の動画: ${formatKbps(it)}", style = MaterialTheme.typography.bodySmall)
    }
    when (bitRate) {
        is BitRateSetting.Quality -> {
            Text(
                "品質 CRF ${bitRate.crf}（小さいほど高画質・大容量）",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = bitRate.crf.toFloat(),
                onValueChange = { onBitRateChange(BitRateSetting.Quality(it.roundToInt())) },
                valueRange = 16f..35f,
                steps = 35 - 16 - 1,
                enabled = enabled,
            )
        }

        is BitRateSetting.Constant -> Unit
    }
    OutlinedTextField(
        value = customText,
        onValueChange = { text ->
            val digits = text.filter { it.isDigit() }.take(6)
            customText = digits
            digits.toIntOrNull()?.takeIf { it > 0 }?.let { onBitRateChange(BitRateSetting.Constant(it)) }
        },
        label = { Text("カスタム (kbps)") },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

private fun formatKbps(kbps: Int): String {
    return if (kbps >= 1000) "%.1f Mbps".format(kbps / 1000.0) else "$kbps kbps"
}
