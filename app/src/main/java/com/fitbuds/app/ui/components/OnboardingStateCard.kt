@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.fitbuds.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.fitbuds.app.controller.ConnectionPhase

@Composable
fun OnboardingStateCard(
    phase: ConnectionPhase,
    statusMessage: String,
    onRequestPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenDevicePicker: () -> Unit,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content: OnboardingContent? = when (phase) {
        ConnectionPhase.NO_PERMISSION -> OnboardingContent(
            icon = Icons.Rounded.Security,
            title = "需要蓝牙连接权限",
            description = "FitBuds Pro 仅读取系统已配对的蓝牙耳机并建立经典蓝牙 RFCOMM 控制连接，不需要位置或附近扫描权限。",
            primaryLabel = "授予蓝牙权限",
            primaryIcon = Icons.Rounded.Bluetooth,
            onPrimaryClick = onRequestPermission,
            secondaryLabel = "应用权限设置",
            onSecondaryClick = onOpenAppSettings,
        )

        ConnectionPhase.BLUETOOTH_OFF -> OnboardingContent(
            icon = Icons.Rounded.BluetoothDisabled,
            title = "系统蓝牙已关闭",
            description = "请先开启系统蓝牙，并确保您的 EDIFIER FitBuds Pro 已处于配对连接状态。",
            primaryLabel = "开启蓝牙",
            primaryIcon = Icons.Rounded.Bluetooth,
            onPrimaryClick = onEnableBluetooth,
            secondaryLabel = "系统蓝牙设置",
            onSecondaryClick = onOpenBluetoothSettings,
        )

        ConnectionPhase.NO_DEVICE -> OnboardingContent(
            icon = Icons.AutoMirrored.Rounded.BluetoothSearching,
            title = "未发现已配对的 FitBuds",
            description = "请先在系统蓝牙设置中完成与 EDIFIER FitBuds Pro 的配对；如果设备名称不同，您也可以手动从已配对设备列表中选择。",
            primaryLabel = "选择已配对设备",
            primaryIcon = Icons.Rounded.Devices,
            onPrimaryClick = onOpenDevicePicker,
            secondaryLabel = "系统蓝牙设置",
            onSecondaryClick = onOpenBluetoothSettings,
        )

        ConnectionPhase.DISCONNECTED,
        ConnectionPhase.POWERED_OFF -> OnboardingContent(
            icon = Icons.Rounded.BluetoothDisabled,
            title = if (phase == ConnectionPhase.POWERED_OFF) "耳机已关机" else "蓝牙控制会话未连接",
            description = statusMessage.ifBlank { "请确保耳机已开机并与手机保持蓝牙连接，然后点击重新连接。" },
            primaryLabel = "立即重连",
            primaryIcon = Icons.Rounded.Refresh,
            onPrimaryClick = onReconnect,
            secondaryLabel = "切换设备",
            onSecondaryClick = onOpenDevicePicker,
        )

        ConnectionPhase.CONNECTING,
        ConnectionPhase.CONNECTED -> null
    }

    if (content == null) return

    val emblemShape = MaterialShapes.SoftBurst.toShape()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(emblemShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = content.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(26.dp),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = content.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = content.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = content.onPrimaryClick,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = content.primaryIcon,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(18.dp),
                    )
                    Text(content.primaryLabel)
                }

                FilledTonalButton(
                    onClick = content.onSecondaryClick,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(18.dp),
                    )
                    Text(content.secondaryLabel)
                }
            }
        }
    }
}

private data class OnboardingContent(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val primaryLabel: String,
    val primaryIcon: ImageVector,
    val onPrimaryClick: () -> Unit,
    val secondaryLabel: String,
    val onSecondaryClick: () -> Unit,
)
