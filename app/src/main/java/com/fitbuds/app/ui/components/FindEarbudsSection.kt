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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitbuds.app.protocol.BatteryStatus
import com.fitbuds.app.protocol.FindEarTarget

@Composable
fun FindEarbudsSection(
    battery: BatteryStatus?,
    canFindBase: Boolean,
    isPlaying: Boolean,
    onFindEarbuds: (FindEarTarget) -> Unit,
    onStopFindEarbuds: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingConfirmTarget by remember { mutableStateOf<FindEarTarget?>(null) }

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
                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    text = "找回耳机",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = CircleShape,
                    color = if (isPlaying) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (isPlaying) {
                            LoadingIndicator(
                                modifier = Modifier.size(14.dp),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                        Text(
                            text = if (isPlaying) "播放中" else "立体声提示音",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isPlaying) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            Text(
                text = "向 FitBuds 立体声音频输出播放约 3 秒提示音，播放前请先摘下耳机。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val targets = FindEarTarget.entries
            ButtonGroup(
                overflowIndicator = { menuState ->
                    ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                },
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                targets.forEachIndexed { index, target ->
                    val available = !isPlaying &&
                        canFindBase &&
                        (battery == null || (battery.present and target.earsMask) == target.earsMask)
                    customItem(
                        buttonGroupContent = {
                            val shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                targets.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            }
                            val interactionSource = remember(target) { MutableInteractionSource() }
                            FilledTonalToggleButton(
                                checked = false,
                                onCheckedChange = { pendingConfirmTarget = target },
                                enabled = available,
                                shapes = shapes,
                                interactionSource = interactionSource,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = this@ButtonGroup.run {
                                    Modifier
                                        .weight(1f)
                                        .animateWidth(interactionSource)
                                },
                            ) {
                                Text(
                                    text = target.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        menuContent = { menuState ->
                            DropdownMenuItem(
                                text = { Text("找回${target.label}") },
                                enabled = available,
                                onClick = {
                                    menuState.dismiss()
                                    pendingConfirmTarget = target
                                },
                            )
                        },
                    )
                }
            }

            AnimatedVisibility(visible = isPlaying) {
                FilledTonalButton(
                    onClick = onStopFindEarbuds,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("停止播放提示音")
                }
            }
        }
    }

    pendingConfirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingConfirmTarget = null },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            },
            title = { Text("找回${target.label}") },
            text = {
                Text("提示音可能很响。请先摘下两只耳机，再点击“播放提示音”。确认框将在 8 秒内有效。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingConfirmTarget = null
                        onFindEarbuds(target)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text("播放提示音")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmTarget = null }) {
                    Text("取消")
                }
            },
        )
    }
}
