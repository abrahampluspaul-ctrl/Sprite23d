package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.BoneConstraint
import com.example.domain.model.BoneNode
import com.example.domain.model.RigPreset
import com.example.ui.theme.GizmoAmber
import com.example.ui.theme.PrecisionCyan
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RigConstraintsTab(
    currentPreset: RigPreset,
    bones: List<BoneNode>,
    selectedBoneId: String?,
    clampedBoneIds: Set<String>,
    onApplyPreset: (RigPreset) -> Unit,
    onSelectBone: (String) -> Unit,
    onUpdateConstraint: (String, (BoneConstraint) -> BoneConstraint) -> Unit,
    onResetBoneConstraint: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedBone = bones.find { it.id == selectedBoneId } ?: bones.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StudioSectionHeader(
            title = "Character Rig Presets",
            badge = currentPreset.badge
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RigPreset.entries.forEach { preset ->
                StudioChoiceChip(
                    label = preset.displayName,
                    selected = preset == currentPreset,
                    onClick = { onApplyPreset(preset) },
                    tag = "rig_preset_${preset.name.lowercase()}",
                    subBadge = preset.badge
                )
            }
        }

        StudioSectionHeader(
            title = "Bone Hierarchy (${bones.size})",
            badge = if (clampedBoneIds.isNotEmpty()) "${clampedBoneIds.size} Clamped" else "Free",
            accent = if (clampedBoneIds.isNotEmpty()) GizmoAmber else PrecisionCyan
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            bones.forEach { bone ->
                val isClamped = clampedBoneIds.contains(bone.id)
                StudioChoiceChip(
                    label = bone.name,
                    selected = bone.id == selectedBone?.id,
                    onClick = { onSelectBone(bone.id) },
                    tag = "bone_chip_${bone.id}",
                    accent = if (isClamped) GizmoAmber else PrecisionCyan,
                    subBadge = if (isClamped) "LIMIT" else null
                )
            }
        }

        if (selectedBone != null) {
            val c = selectedBone.constraint
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioSurfaceElevated, RoundedCornerShape(14.dp))
                    .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedBone.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = StudioTextPrimary
                        )
                        Text(
                            text = "Unity: ${selectedBone.unityName} • UE5: ${selectedBone.ue5Name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioTextSecondary
                        )
                    }
                    Switch(
                        checked = c.enabled,
                        onCheckedChange = { checked ->
                            onUpdateConstraint(selectedBone.id) { it.copy(enabled = checked) }
                        },
                        modifier = Modifier.testTag("bone_constraint_enable_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PrecisionCyan,
                            checkedTrackColor = PrecisionCyan.copy(alpha = 0.35f)
                        )
                    )
                }

                // Rotation Limits (Degrees)
                Text(
                    text = "Rotation Limits (Degrees)",
                    style = MaterialTheme.typography.labelLarge,
                    color = GizmoAmber
                )
                StudioRangeSliderRow(
                    label = "Pitch (X Axis)",
                    rangeText = "${c.rotMinX.roundToInt()}° .. ${c.rotMaxX.roundToInt()}°",
                    values = c.rotMinX..c.rotMaxX,
                    valueRange = -180f..180f,
                    onValueChange = { r ->
                        onUpdateConstraint(selectedBone.id) {
                            it.copy(rotMinX = r.start, rotMaxX = r.endInclusive)
                        }
                    },
                    tag = "slider_rot_x",
                    accent = GizmoAmber
                )
                StudioRangeSliderRow(
                    label = "Yaw (Y Axis)",
                    rangeText = "${c.rotMinY.roundToInt()}° .. ${c.rotMaxY.roundToInt()}°",
                    values = c.rotMinY..c.rotMaxY,
                    valueRange = -180f..180f,
                    onValueChange = { r ->
                        onUpdateConstraint(selectedBone.id) {
                            it.copy(rotMinY = r.start, rotMaxY = r.endInclusive)
                        }
                    },
                    tag = "slider_rot_y",
                    accent = GizmoAmber
                )
                StudioRangeSliderRow(
                    label = "Roll (Z Axis)",
                    rangeText = "${c.rotMinZ.roundToInt()}° .. ${c.rotMaxZ.roundToInt()}°",
                    values = c.rotMinZ..c.rotMaxZ,
                    valueRange = -180f..180f,
                    onValueChange = { r ->
                        onUpdateConstraint(selectedBone.id) {
                            it.copy(rotMinZ = r.start, rotMaxZ = r.endInclusive)
                        }
                    },
                    tag = "slider_rot_z",
                    accent = GizmoAmber
                )

                // Scaling Limits
                Text(
                    text = "Squash & Stretch Scale Bounds",
                    style = MaterialTheme.typography.labelLarge,
                    color = PrecisionCyan
                )
                StudioRangeSliderRow(
                    label = "Scale X Bounds",
                    rangeText = String.format(Locale.US, "%.2fx .. %.2fx", c.scaleMinX, c.scaleMaxX),
                    values = c.scaleMinX..c.scaleMaxX,
                    valueRange = 0.25f..2.0f,
                    onValueChange = { r ->
                        onUpdateConstraint(selectedBone.id) {
                            it.copy(scaleMinX = r.start, scaleMaxX = r.endInclusive)
                        }
                    },
                    tag = "slider_scale_x"
                )
                StudioRangeSliderRow(
                    label = "Scale Y Bounds",
                    rangeText = String.format(Locale.US, "%.2fx .. %.2fx", c.scaleMinY, c.scaleMaxY),
                    values = c.scaleMinY..c.scaleMaxY,
                    valueRange = 0.25f..2.0f,
                    onValueChange = { r ->
                        onUpdateConstraint(selectedBone.id) {
                            it.copy(scaleMinY = r.start, scaleMaxY = r.endInclusive)
                        }
                    },
                    tag = "slider_scale_y"
                )

                // Position Bounds & Locks
                Text(
                    text = "Position Bounds & Axis Locks",
                    style = MaterialTheme.typography.labelLarge,
                    color = PrecisionCyan
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AxisLockButton(
                        axis = "X",
                        locked = c.lockPosX,
                        onClick = { onUpdateConstraint(selectedBone.id) { it.copy(lockPosX = !it.lockPosX) } },
                        modifier = Modifier.weight(1f)
                    )
                    AxisLockButton(
                        axis = "Y",
                        locked = c.lockPosY,
                        onClick = { onUpdateConstraint(selectedBone.id) { it.copy(lockPosY = !it.lockPosY) } },
                        modifier = Modifier.weight(1f)
                    )
                    AxisLockButton(
                        axis = "Z",
                        locked = c.lockPosZ,
                        onClick = { onUpdateConstraint(selectedBone.id) { it.copy(lockPosZ = !it.lockPosZ) } },
                        modifier = Modifier.weight(1f)
                    )
                }

                StudioRangeSliderRow(
                    label = "Vertical Position Limit (Y)",
                    rangeText = String.format(Locale.US, "%.2f .. %.2f m", c.posMinY, c.posMaxY),
                    values = c.posMinY..c.posMaxY,
                    valueRange = -0.50f..0.50f,
                    onValueChange = { r ->
                        onUpdateConstraint(selectedBone.id) {
                            it.copy(posMinY = r.start, posMaxY = r.endInclusive)
                        }
                    },
                    tag = "slider_pos_y"
                )

                OutlinedButton(
                    onClick = onResetBoneConstraint,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reset_bone_constraint_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text("Reset ${selectedBone.name} to ${currentPreset.displayName}")
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AxisLockButton(
    axis: String,
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.testTag("lock_pos_${axis.lowercase()}_btn")
    ) {
        Icon(
            imageVector = if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
            contentDescription = if (locked) "Unlock $axis position" else "Lock $axis position",
            tint = if (locked) GizmoAmber else StudioTextSecondary,
            modifier = Modifier.padding(end = 4.dp)
        )
        Text(
            text = if (locked) "$axis Locked" else "$axis Free",
            style = MaterialTheme.typography.labelSmall,
            color = if (locked) GizmoAmber else StudioTextPrimary
        )
    }
}
