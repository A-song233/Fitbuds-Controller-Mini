@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.fitbuds.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.NoiseAware
import androidx.compose.material.icons.rounded.NoiseControlOff
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitbuds.app.protocol.BatteryStatus
import com.fitbuds.app.protocol.FitBudsCommand
import com.fitbuds.app.protocol.FitBudsProtocol
import com.fitbuds.app.protocol.NoiseMode

@Composable
fun NoiseControlSection(
    activeMode: NoiseMode?,
    battery: BatteryStatus?,
    controlsEnabled: Boolean,
    onSelectMode: (NoiseMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentMask = battery?.present ?: 3
    val singleEarOnly = battery != null && battery.present != 0 && battery.present != 3

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.NoiseAware,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "降噪与环境声",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = CircleShape,
                    color = if (activeMode != null) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                ) {
                    Text(
                        text = activeMode?.label ?: "未同步",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (activeMode != null) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            AnimatedVisibility(visible = singleEarOnly) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                ) {
                    Text(
                        text = "当前为${battery?.wearMode?.label ?: "单耳"}状态，仅支持切换环境声与降噪关",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            val ancModes = listOf(NoiseMode.DEEP, NoiseMode.WIDE, NoiseMode.WIND)
            NoiseConnectedRow(
                modes = ancModes,
                activeMode = activeMode,
                presentMask = presentMask,
                hasBatteryPayload = battery != null,
                controlsEnabled = controlsEnabled,
                onSelectMode = onSelectMode,
            )

            val transparencyAndOff = listOf(NoiseMode.AMBIENT, NoiseMode.OFF)
            NoiseConnectedRow(
                modes = transparencyAndOff,
                activeMode = activeMode,
                presentMask = presentMask,
                hasBatteryPayload = battery != null,
                controlsEnabled = controlsEnabled,
                onSelectMode = onSelectMode,
            )
        }
    }
}

@Composable
private fun NoiseConnectedRow(
    modes: List<NoiseMode>,
    activeMode: NoiseMode?,
    presentMask: Int,
    hasBatteryPayload: Boolean,
    controlsEnabled: Boolean,
    onSelectMode: (NoiseMode) -> Unit,
) {
    ButtonGroup(
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        modes.forEachIndexed { index, mode ->
            val allowedByWearMode = !hasBatteryPayload ||
                FitBudsProtocol.isCommandAllowed(FitBudsCommand.SetNoiseMode(mode), presentMask)
            val itemEnabled = controlsEnabled && allowedByWearMode
            val checked = activeMode == mode

            customItem(
                buttonGroupContent = {
                    val shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }
                    val interactionSource = remember(mode) { MutableInteractionSource() }
                    FilledTonalToggleButton(
                        checked = checked,
                        onCheckedChange = { onSelectMode(mode) },
                        enabled = itemEnabled,
                        shapes = shapes,
                        interactionSource = interactionSource,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = this@ButtonGroup.run {
                            Modifier
                                .weight(1f)
                                .animateWidth(interactionSource)
                        },
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = noiseIcon(mode),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = mode.shortLabel,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                menuContent = { menuState ->
                    DropdownMenuItem(
                        text = { Text(mode.label) },
                        leadingIcon = {
                            Icon(imageVector = noiseIcon(mode), contentDescription = null)
                        },
                        enabled = itemEnabled,
                        onClick = {
                            menuState.dismiss()
                            onSelectMode(mode)
                        },
                    )
                },
            )
        }
    }
}

private fun noiseIcon(mode: NoiseMode): ImageVector = when (mode) {
    NoiseMode.DEEP -> Icons.Rounded.NoiseAware
    NoiseMode.WIDE -> Icons.Rounded.SurroundSound
    NoiseMode.WIND -> Icons.Rounded.Air
    NoiseMode.AMBIENT -> Icons.Rounded.Hearing
    NoiseMode.OFF -> Icons.Rounded.NoiseControlOff
}
