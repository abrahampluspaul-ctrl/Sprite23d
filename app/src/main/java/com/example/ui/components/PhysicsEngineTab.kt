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
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.MeshGroupPhysics
import com.example.domain.model.MeshGroupType
import com.example.domain.model.PhysicsType
import com.example.ui.theme.ExportEmerald
import com.example.ui.theme.PrecisionCyan
import com.example.ui.theme.ShaderViolet
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import java.util.Locale

@Composable
fun PhysicsEngineTab(
    physicsGroups: List<MeshGroupPhysics>,
    selectedGroup: MeshGroupType,
    showPhysicsVectors: Boolean,
    onSelectGroup: (MeshGroupType) -> Unit,
    onUpdateGroup: (MeshGroupType, (MeshGroupPhysics) -> MeshGroupPhysics) -> Unit,
    onTogglePhysicsVectors: (Boolean) -> Unit,
    onTriggerWindGust: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = physicsGroups.find { it.groupType == selectedGroup } ?: physicsGroups.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StudioSectionHeader(
            title = "Mesh Group Physics",
            badge = "${physicsGroups.count { it.enabled }} Active",
            accent = ExportEmerald
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            physicsGroups.forEach { pg ->
                StudioChoiceChip(
                    label = pg.label,
                    selected = pg.groupType == active?.groupType,
                    onClick = { onSelectGroup(pg.groupType) },
                    tag = "physics_group_${pg.groupType.name.lowercase()}",
                    accent = if (pg.enabled) ExportEmerald else PrecisionCyan,
                    subBadge = if (pg.enabled) pg.physicsType.name else "OFF"
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onTriggerWindGust,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrecisionCyan,
                    contentColor = StudioObsidian
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("trigger_wind_gust_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Air,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text("Trigger Wind Gust")
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(StudioSurfaceElevated, RoundedCornerShape(10.dp))
                    .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Trails",
                    style = MaterialTheme.typography.labelLarge,
                    color = StudioTextSecondary,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Switch(
                    checked = showPhysicsVectors,
                    onCheckedChange = onTogglePhysicsVectors,
                    modifier = Modifier.testTag("physics_vectors_switch"),
                    colors = SwitchDefaults.colors(checkedThumbColor = ShaderViolet)
                )
            }
        }

        if (active != null) {
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
                            text = active.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = StudioTextPrimary
                        )
                        Text(
                            text = "Verlet Spring-Damper • Exportable Metadata",
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioTextSecondary
                        )
                    }
                    Switch(
                        checked = active.enabled,
                        onCheckedChange = { enabled ->
                            onUpdateGroup(active.groupType) { it.copy(enabled = enabled) }
                        },
                        modifier = Modifier.testTag("physics_group_enable_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ExportEmerald,
                            checkedTrackColor = ExportEmerald.copy(alpha = 0.35f)
                        )
                    )
                }

                Text(
                    text = "Dynamics Solver Type",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StudioTextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PhysicsType.entries.forEach { pType ->
                        StudioChoiceChip(
                            label = pType.displayName,
                            selected = active.physicsType == pType,
                            onClick = { onUpdateGroup(active.groupType) { it.copy(physicsType = pType) } },
                            tag = "physics_type_${pType.name.lowercase()}",
                            accent = ExportEmerald
                        )
                    }
                }

                StudioSliderRow(
                    label = "Spring Stiffness",
                    valueText = String.format(Locale.US, "%.0f%%", active.stiffness * 100f),
                    value = active.stiffness,
                    valueRange = 0.15f..0.96f,
                    onValueChange = { v -> onUpdateGroup(active.groupType) { it.copy(stiffness = v) } },
                    tag = "slider_phys_stiffness",
                    accent = ExportEmerald
                )
                StudioSliderRow(
                    label = "Velocity Damping",
                    valueText = String.format(Locale.US, "%.2f", active.damping),
                    value = active.damping,
                    valueRange = 0.60f..0.96f,
                    onValueChange = { v -> onUpdateGroup(active.groupType) { it.copy(damping = v) } },
                    tag = "slider_phys_damping",
                    accent = ExportEmerald
                )
                StudioSliderRow(
                    label = "Gravity Scale",
                    valueText = String.format(Locale.US, "%.2fx", active.gravityScale),
                    value = active.gravityScale,
                    valueRange = 0f..2.2f,
                    onValueChange = { v -> onUpdateGroup(active.groupType) { it.copy(gravityScale = v) } },
                    tag = "slider_phys_gravity",
                    accent = ExportEmerald
                )
                StudioSliderRow(
                    label = "Wind Turbulence",
                    valueText = String.format(Locale.US, "%.0f%%", active.windStrength * 100f),
                    value = active.windStrength,
                    valueRange = 0f..1.5f,
                    onValueChange = { v -> onUpdateGroup(active.groupType) { it.copy(windStrength = v) } },
                    tag = "slider_phys_wind",
                    accent = ExportEmerald
                )
                StudioSliderRow(
                    label = "Vertex Mass",
                    valueText = String.format(Locale.US, "%.2f kg", active.mass),
                    value = active.mass,
                    valueRange = 0.3f..2.5f,
                    onValueChange = { v -> onUpdateGroup(active.groupType) { it.copy(mass = v) } },
                    tag = "slider_phys_mass",
                    accent = ExportEmerald
                )
                StudioSliderRow(
                    label = "Torso Collision Radius",
                    valueText = String.format(Locale.US, "%.2f m", active.collisionRadius),
                    value = active.collisionRadius,
                    valueRange = 0.05f..0.40f,
                    onValueChange = { v -> onUpdateGroup(active.groupType) { it.copy(collisionRadius = v) } },
                    tag = "slider_phys_collision",
                    accent = ExportEmerald
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
