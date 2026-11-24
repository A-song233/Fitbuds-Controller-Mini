@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.fitbuds.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitbuds.app.controller.ConnectionPhase
import com.fitbuds.app.controller.FitBudsUiState

@Composable
fun BatteryHeroCard(
    uiState: FitBudsUiState,
    modifier: Modifier = Modifier,
) {
    val battery = uiState.battery
    val wearModeLabel = when {
        battery != null -> battery.wearMode.label
        uiState.phase == ConnectionPhase.CONNECTING -> "正在握手"
        uiState.phase == ConnectionPhase.POWERED_OFF -> "已关机"
        uiState.phase == ConnectionPhase.DISCONNECTED -> "已断开"
        else -> "等待数据"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                HeroDeviceEmblem(
                    showConnectingSpinner = uiState.phase == ConnectionPhase.CONNECTING ||
                        (uiState.phase == ConnectionPhase.CONNECTED &&
                            uiState.battery == null &&
                            uiState.isRefreshing &&
                            !uiState.isPullRefreshing),
                    phase = uiState.phase,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = uiState.connectedDevice?.name ?: "EDIFIER FitBuds Pro",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = uiState.connectedDevice?.address ?: "经典蓝牙 RFCOMM 控制通道",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = when {
                        battery != null && battery.present != 0 ->
                            MaterialTheme.colorScheme.secondaryContainer
                        uiState.phase == ConnectionPhase.POWERED_OFF ->
                            MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                ) {
                    AnimatedContent(
                        targetState = wearModeLabel,
                        label = "WearModeBadge",
                    ) { label ->
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = when {
                                battery != null && battery.present != 0 ->
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                uiState.phase == ConnectionPhase.POWERED_OFF ->
                                    MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BatteryDialTile(
                    label = "左耳",
                    level = battery?.left,
                    present = battery?.isLeftPresent ?: false,
                    hasBatteryPayload = battery != null,
                    accentColor = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f),
                )
                BatteryDialTile(
                    label = "右耳",
                    level = battery?.right,
                    present = battery?.isRightPresent ?: false,
                    hasBatteryPayload = battery != null,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f),
                )
                BatteryDialTile(
                    label = "充电仓",
                    level = battery?.caseLevel,
                    present = true,
                    hasBatteryPayload = battery != null,
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f),
                )
            }

            val showStatusStrip = uiState.statusMessage.isNotBlank() ||
                uiState.isRefreshing ||
                uiState.isWaitingSettingSlot ||
                uiState.pendingSettingsCount > 0

            AnimatedVisibility(visible = showStatusStrip) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when {
                            uiState.isWaitingSettingSlot -> {
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            uiState.phase == ConnectionPhase.CONNECTING -> {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.BluetoothSearching,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            uiState.isFindAudioPlaying ||
                                (uiState.isRefreshing && !uiState.isPullRefreshing && uiState.battery != null) -> {
                                LoadingIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }

                            uiState.phase == ConnectionPhase.POWERED_OFF -> {
                                Icon(
                                    imageVector = Icons.Rounded.PowerSettingsNew,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            uiState.phase == ConnectionPhase.DISCONNECTED -> {
                                Icon(
                                    imageVector = Icons.Rounded.BluetoothDisabled,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            else -> {
                                Icon(
                                    imageVector = Icons.Rounded.BluetoothConnected,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        Text(
                            text = when {
                                uiState.statusMessage.isNotBlank() -> uiState.statusMessage
                                uiState.isRefreshing -> "正在同步耳机状态…"
                                else -> "已连接并保持长连接复用"
                            },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        if (uiState.pendingSettingsCount > 0) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = "待确认 ${uiState.pendingSettingsCount}",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroDeviceEmblem(
    showConnectingSpinner: Boolean,
    phase: ConnectionPhase,
) {
    val emblemShape = MaterialShapes.Cookie9Sided.toShape()
    if (showConnectingSpinner) {
        ContainedLoadingIndicator(
            modifier = Modifier.size(52.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            indicatorColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        val (containerColor, iconTint, icon) = when (phase) {
            ConnectionPhase.CONNECTED -> Triple(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
                Icons.Rounded.Headphones,
            )

            ConnectionPhase.POWERED_OFF -> Triple(
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
                Icons.Rounded.PowerSettingsNew,
            )

            else -> Triple(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.colorScheme.onSurfaceVariant,
                Icons.Rounded.Headphones,
            )
        }
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(emblemShape)
                .background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun BatteryDialTile(
    label: String,
    level: Int?,
    present: Boolean,
    hasBatteryPayload: Boolean,
    accentColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    val isLow = level != null && level <= 20
    val activeColor = if (isLow) MaterialTheme.colorScheme.error else accentColor
    val effectiveTrackColor = if (isLow) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
    } else {
        trackColor
    }

    val animatedProgress by animateFloatAsState(
        targetValue = (level ?: 0) / 100f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "BatteryDialProgress_$label",
    )

    val density = LocalDensity.current
    val indicatorStroke = with(density) {
        Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
    }
    val trackStroke = with(density) {
        Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
    }

    val subText = when {
        !hasBatteryPayload -> "待同步"
        level != null && isLow -> "电量偏低"
        level != null -> "在线"
        present -> "未上报"
        else -> "未连接"
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.size(76.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularWavyProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(76.dp),
                    color = activeColor,
                    trackColor = effectiveTrackColor,
                    stroke = indicatorStroke,
                    trackStroke = trackStroke,
                    gapSize = if (level != null) 4.dp else 0.dp,
                    amplitude = { p -> if (level != null && p > 0.02f) 1f else 0f },
                    wavelength = 18.dp,
                )

                Text(
                    text = level?.let { "$it%" } ?: "--",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = if (level != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    if (isLow) {
                        Icon(
                            imageVector = Icons.Rounded.BatteryAlert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(12.dp),
                        )
                    } else if (level == 100) {
                        Icon(
                            imageVector = Icons.Rounded.BatteryChargingFull,
                            contentDescription = null,
                            tint = activeColor,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    Text(
                        text = subText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isLow) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
