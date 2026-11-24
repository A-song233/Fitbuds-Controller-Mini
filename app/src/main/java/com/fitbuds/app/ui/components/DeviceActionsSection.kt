@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.fitbuds.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.fitbuds.app.protocol.FitBudsProtocol
import java.text.DateFormat
import java.util.Date

@Composable
fun DeviceActionsSection(
    gameModeEnabled: Boolean?,
    timerMinutes: Int?,
    controlsEnabled: Boolean,
    isRefreshing: Boolean,
    showInlineRefreshSpinner: Boolean,
    lastUpdatedMillis: Long?,
    onToggleGameMode: (Boolean) -> Unit,
    onSetTimerMinutes: (Int) -> Unit,
    onRefresh: () -> Unit,
    onPowerOffConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPowerOffDialog by remember { mutableStateOf(false) }
    var showTimerSheet by remember { mutableStateOf(false) }

    val updatedText = remember(lastUpdatedMillis) {
        if (lastUpdatedMillis == null) {
            "手动立即读取电量与降噪 / 音效 / 操控 / 定时状态"
        } else {
            val formatted = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(lastUpdatedMillis))
            "上次同步 $formatted · 每 45 秒自动检查电量"
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "快捷控制与电源",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )

        SegmentedListItem(
            checked = gameModeEnabled == true,
            onCheckedChange = { checked -> onToggleGameMode(checked) },
            enabled = controlsEnabled,
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 4),
            colors = ListItemDefaults.segmentedColors(),
            leadingContent = {
                ShapedIconBadge(
                    icon = Icons.Rounded.SportsEsports,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            },
            supportingContent = {
                Text(
                    text = when (gameModeEnabled) {
                        true -> "低延迟游戏模式已开启"
                        false -> "低延迟游戏模式已关闭"
                        null -> "切换低延迟无线传输"
                    },
                )
            },
            trailingContent = {
                Switch(
                    checked = gameModeEnabled == true,
                    onCheckedChange = null,
                    enabled = controlsEnabled,
                )
            },
        ) {
            Text("游戏模式")
        }

        SegmentedListItem(
            onClick = { showTimerSheet = true },
            enabled = controlsEnabled,
            shapes = ListItemDefaults.segmentedShapes(index = 1, count = 4),
            colors = ListItemDefaults.segmentedColors(),
            leadingContent = {
                ShapedIconBadge(
                    icon = Icons.Rounded.Timer,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            },
            supportingContent = {
                Text(
                    text = timerMinutes?.let { FitBudsProtocol.formatTimerMinutes(it) } ?: "未同步",
                )
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                )
            },
        ) {
            Text("定时关机")
        }

        SegmentedListItem(
            onClick = onRefresh,
            enabled = !isRefreshing,
            shapes = ListItemDefaults.segmentedShapes(index = 2, count = 4),
            colors = ListItemDefaults.segmentedColors(),
            leadingContent = {
                ShapedIconBadge(
                    icon = Icons.Rounded.Refresh,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            },
            supportingContent = {
                Text(updatedText)
            },
            trailingContent = {
                if (showInlineRefreshSpinner) {
                    LoadingIndicator(
                        modifier = Modifier.size(28.dp),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            },
        ) {
            Text("刷新电量与设置")
        }

        SegmentedListItem(
            onClick = { showPowerOffDialog = true },
            enabled = controlsEnabled,
            shapes = ListItemDefaults.segmentedShapes(index = 3, count = 4),
            colors = ListItemDefaults.segmentedColors(),
            leadingContent = {
                ShapedIconBadge(
                    icon = Icons.Rounded.PowerSettingsNew,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            },
            supportingContent = {
                Text("向耳机发送关机指令并断开蓝牙连接")
            },
        ) {
            Text(
                text = "耳机关机",
                color = if (controlsEnabled) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        }
    }

    if (showTimerSheet) {
        AutoPowerOffTimerSheet(
            currentMinutes = timerMinutes,
            onSelectMinutes = { minutes ->
                showTimerSheet = false
                onSetTimerMinutes(minutes)
            },
            onDismiss = { showTimerSheet = false },
        )
    }

    if (showPowerOffDialog) {
        AlertDialog(
            onDismissRequest = { showPowerOffDialog = false },
            icon = {
                ShapedIconBadge(
                    icon = Icons.Rounded.PowerSettingsNew,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            },
            title = { Text("关闭当前连接的耳机？") },
            text = {
                Text("发送关机指令后，FitBuds Pro 将立即关机并断开蓝牙 RFCOMM 会话。如需再次控制，请重新取出并连接耳机。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPowerOffDialog = false
                        onPowerOffConfirmed()
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("确认关机")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPowerOffDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun AutoPowerOffTimerSheet(
    currentMinutes: Int?,
    onSelectMinutes: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val choices = FitBudsProtocol.TIMER_CHOICES

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "定时关机",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "设置耳机自动关机倒计时，或关闭定时关机",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                choices.forEachIndexed { index, minutes ->
                    val isSelected = currentMinutes == minutes
                    SegmentedListItem(
                        selected = isSelected,
                        onClick = { onSelectMinutes(minutes) },
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = choices.size),
                        colors = ListItemDefaults.segmentedColors(),
                        trailingContent = {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "已选中",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    ) {
                        Text(FitBudsProtocol.formatTimerMinutes(minutes))
                    }
                }
            }
        }
    }
}

@Composable
private fun ShapedIconBadge(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
) {
    val badgeShape = MaterialShapes.Clover4Leaf.toShape()
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(badgeShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
    }
}
