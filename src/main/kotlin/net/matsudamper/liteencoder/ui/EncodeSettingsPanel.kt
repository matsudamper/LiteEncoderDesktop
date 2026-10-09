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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun EncodeSettingsPanel(
    uiState: EncodeSettingsUiState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Section("形式") {
            OptionChips(options = uiState.formatOptions, isEnabled = uiState.isEnabled)
        }

        Section("サイズ（比率維持）") {
            OptionChips(options = uiState.resolutionOptions, isEnabled = uiState.isEnabled)
            Text(uiState.outputSizeText, style = MaterialTheme.typography.bodySmall)
        }

        Section("フレームレート") {
            OptionChips(options = uiState.frameRateOptions, isEnabled = uiState.isEnabled)
        }

        val bitRate = uiState.bitRate
        if (bitRate != null) {
            Section("ビットレート") {
                OptionChips(options = bitRate.options, isEnabled = uiState.isEnabled)
                val sourceBitRateText = bitRate.sourceBitRateText
                if (sourceBitRateText != null) {
                    Text(sourceBitRateText, style = MaterialTheme.typography.bodySmall)
                }
                val quality = bitRate.quality
                if (quality != null) {
                    Text(quality.label, style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = quality.crf,
                        onValueChange = uiState.event::onQualityChange,
                        valueRange = quality.crfRange,
                        steps = quality.steps,
                        enabled = uiState.isEnabled,
                    )
                }
                OutlinedTextField(
                    value = bitRate.customBitRateText,
                    onValueChange = uiState.event::onCustomBitRateChange,
                    label = { Text("カスタム (kbps)") },
                    singleLine = true,
                    enabled = uiState.isEnabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        val volume = uiState.volume
        if (volume != null) {
            Section("音量") {
                Text(volume.label, style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = volume.percent,
                    onValueChange = uiState.event::onVolumeChange,
                    valueRange = volume.percentRange,
                    steps = volume.steps,
                    enabled = uiState.isEnabled,
                )
            }
        }
    }
}

@Composable
private fun OptionChips(options: List<OptionUiState>, isEnabled: Boolean) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option.isSelected,
                onClick = option.event::onClick,
                label = { Text(option.label) },
                enabled = isEnabled && option.isEnabled,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
