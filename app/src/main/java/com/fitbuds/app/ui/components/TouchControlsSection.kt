@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.fitbuds.app.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitbuds.app.protocol.EarSide
import com.fitbuds.app.protocol.FitBudsProtocol
import com.fitbuds.app.protocol.GestureAction
import com.fitbuds.app.protocol.GestureSetting
import com.fitbuds.app.protocol.GestureSlot
import com.fitbuds.app.protocol.NoiseMode

@Composable
fun TouchControlsSection(
    gestures: Map<GestureSlot, GestureSetting>,
    controlsEnabled: Boolean,
    onSetGesture: (GestureSetting) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedEar by remember { mutableStateOf(EarSide.LEFT) }
    var editingSlot by remember { mutableStateOf<GestureSlot?>(null) }

    val slotsForEar = remember(selectedEar) { GestureSlot.forEar(selectedEar) }

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
                    imageVector = Icons.Rounded.TouchApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "操控设置",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = "${selectedEar.label}手势",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ButtonGroup(
                overflowIndicator = { menuState ->
                    ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                },
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                EarSide.entries.forEachIndexed { index, ear ->
                    val checked = selectedEar == ear
                    customItem(
                        buttonGroupContent = {
                            val shapes = if (index == 0) {
                                ButtonGroupDefaults.connectedLeadingButtonShapes()
                            } else {
                                ButtonGroupDefaults.connectedTrailingButtonShapes()
                            }
                            val interactionSource = remember(ear) { MutableInteractionSource() }
                            FilledTonalToggleButton(
                                checked = checked,
                                onCheckedChange = { selectedEar = ear },
                                shapes = shapes,
                                interactionSource = interactionSource,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = this@ButtonGroup.run {
                                    Modifier
                                        .weight(1f)
                                        .animateWidth(interactionSource)
                                },
                            ) {
                                Text(
                                    text = ear.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                )
                            }
                        },
                        menuContent = { menuState ->
                            DropdownMenuItem(
                                text = { Text(ear.label) },
                                onClick = {
                                    menuState.dismiss()
                                    selectedEar = ear
                                },
                            )
                        },
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                slotsForEar.forEachIndexed { index, slot ->
                    val setting = gestures[slot]
                    SegmentedListItem(
                        onClick = { editingSlot = slot },
                        enabled = controlsEnabled,
                        shapes = ListItemDefaults.segmentedShapes(
                            index = index,
                            count = slotsForEar.size,
                        ),
                        colors = ListItemDefaults.segmentedColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        supportingContent = {
                            Text(
                                text = setting?.summary() ?: "未同步",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                            )
                        },
                    ) {
                        Text(slot.label)
                    }
                }
            }
        }
    }

    editingSlot?.let { slot ->
        GestureEditorSheet(
            slot = slot,
            currentSetting = gestures[slot],
            onSave = { newSetting ->
                editingSlot = null
                onSetGesture(newSetting)
            },
            onDismiss = { editingSlot = null },
        )
    }
}

@Composable
private fun GestureEditorSheet(
    slot: GestureSlot,
    currentSetting: GestureSetting?,
    onSave: (GestureSetting) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickingCycle by remember(slot) { mutableStateOf(false) }
    var cycleMask by remember(slot, currentSetting) {
        val existing = currentSetting?.cycleMask ?: 0
        mutableIntStateOf(
            if (FitBudsProtocol.isValidAncCycle(existing)) existing else FitBudsProtocol.DEFAULT_ANC_CYCLE,
        )
    }

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
            if (!pickingCycle) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${slot.ear.label} · ${slot.label}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "选择该触控手势对应的耳机操作",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val actions = GestureAction.entries
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(actions, key = { _, action -> action.code }) { index, action ->
                        val isSelected = currentSetting?.action == action
                        SegmentedListItem(
                            selected = isSelected,
                            onClick = {
                                if (action == GestureAction.ANC_CYCLE) {
                                    pickingCycle = true
                                } else {
                                    onSave(GestureSetting(slot = slot, action = action))
                                }
                            },
                            shapes = ListItemDefaults.segmentedShapes(index = index, count = actions.size),
                            colors = ListItemDefaults.segmentedColors(),
                            supportingContent = if (action == GestureAction.ANC_CYCLE) {
                                {
                                    val desc = if (isSelected && currentSetting != null) {
                                        "当前：${FitBudsProtocol.ancCycleModes(currentSetting.cycleMask).joinToString(" / ") { it.shortLabel }}"
                                    } else {
                                        "勾选 2 至 3 种降噪或环境声模式循环切换"
                                    }
                                    Text(desc)
                                }
                            } else {
                                null
                            },
                            trailingContent = {
                                if (action == GestureAction.ANC_CYCLE) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = "配置降噪循环模式",
                                    )
                                } else if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "已选中",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                        ) {
                            Text(action.label)
                        }
                    }
                }
            } else {
                val selectedCount = FitBudsProtocol.cycleCount(cycleMask)
                val valid = FitBudsProtocol.isValidAncCycle(cycleMask)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = { pickingCycle = false }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回动作列表",
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "降噪循环 · ${slot.ear.label}${slot.label}",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "请选择 2 至 3 种切换模式（已选 $selectedCount 种）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (valid) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }

                val modes = NoiseMode.entries
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    modes.forEachIndexed { index, mode ->
                        val bit = 1 shl index
                        val checked = (cycleMask and bit) != 0
                        SegmentedListItem(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                cycleMask = if (isChecked) {
                                    cycleMask or bit
                                } else {
                                    cycleMask and bit.inv()
                                }
                            },
                            shapes = ListItemDefaults.segmentedShapes(index = index, count = modes.size),
                            colors = ListItemDefaults.segmentedColors(),
                            trailingContent = {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = null,
                                )
                            },
                        ) {
                            Text(mode.label)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilledTonalButton(
                        onClick = { pickingCycle = false },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("上一步")
                    }
                    Button(
                        onClick = {
                            onSave(
                                GestureSetting(
                                    slot = slot,
                                    action = GestureAction.ANC_CYCLE,
                                    cycleMask = cycleMask,
                                ),
                            )
                        },
                        enabled = valid,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("保存设置")
                    }
                }
            }
        }
    }
}
