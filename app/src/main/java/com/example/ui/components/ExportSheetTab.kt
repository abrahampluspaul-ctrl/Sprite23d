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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.ExportFormat
import com.example.domain.model.ExportOptions
import com.example.domain.model.TargetEngine
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.ExportEmerald
import com.example.ui.theme.PrecisionCyan
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.ui.viewmodel.ExportUiState

@Composable
fun ExportSheetTab(
    options: ExportOptions,
    exportState: ExportUiState,
    vertexCount: Int,
    boneCount: Int,
    activePhysicsCount: Int,
    onUpdateOptions: ((ExportOptions) -> ExportOptions) -> Unit,
    onSaveToDevice: () -> Unit,
    onShareModel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StudioSectionHeader(
            title = "Target Game Engine",
            badge = options.targetEngine.displayName
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TargetEngine.entries.forEach { engine ->
                StudioChoiceChip(
                    label = engine.displayName,
                    selected = options.targetEngine == engine,
                    onClick = { onUpdateOptions { it.copy(targetEngine = engine) } },
                    tag = "engine_target_${engine.name.lowercase()}"
                )
            }
        }

        StudioSectionHeader(
            title = "Export Format",
            badge = ".${options.format.extension.uppercase()}",
            accent = ExportEmerald
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExportFormat.entries.forEach { fmt ->
                StudioChoiceChip(
                    label = fmt.displayName,
                    selected = options.format == fmt,
                    onClick = { onUpdateOptions { it.copy(format = fmt) } },
                    tag = "export_format_${fmt.name.lowercase()}",
                    accent = ExportEmerald
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioSurfaceElevated, RoundedCornerShape(14.dp))
                .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Included Export Data ($vertexCount verts • $boneCount bones • $activePhysicsCount phys groups)",
                style = MaterialTheme.typography.labelSmall,
                color = PrecisionCyan,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            ExportToggleRow(
                label = "PBR Textures (Albedo, Normal, Metallic/Roughness)",
                checked = options.includeTextures,
                onCheckedChange = { v -> onUpdateOptions { it.copy(includeTextures = v) } },
                tag = "exp_opt_textures"
            )
            ExportToggleRow(
                label = "Skeletal Rig & Bone Constraints (Rot/Scale/Pos Limits)",
                checked = options.includeRigAndConstraints,
                onCheckedChange = { v -> onUpdateOptions { it.copy(includeRigAndConstraints = v) } },
                tag = "exp_opt_rig"
            )
            ExportToggleRow(
                label = "Omnidirectional Locomotion Animation Sampler",
                checked = options.includeAnimations,
                onCheckedChange = { v -> onUpdateOptions { it.copy(includeAnimations = v) } },
                tag = "exp_opt_anims"
            )
            ExportToggleRow(
                label = "Cloth / Hair / Soft-Body Physics Group Metadata",
                checked = options.includePhysics,
                onCheckedChange = { v -> onUpdateOptions { it.copy(includePhysics = v) } },
                tag = "exp_opt_physics"
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onSaveToDevice,
                enabled = exportState !is ExportUiState.Exporting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ExportEmerald,
                    contentColor = StudioObsidian
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("save_export_btn")
            ) {
                if (exportState is ExportUiState.Exporting) {
                    CircularProgressIndicator(
                        color = StudioObsidian,
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                Text("Save .${options.format.extension.uppercase()}")
            }

            OutlinedButton(
                onClick = onShareModel,
                enabled = exportState !is ExportUiState.Exporting,
                modifier = Modifier
                    .weight(1f)
                    .testTag("share_export_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text("Share")
            }
        }

        when (exportState) {
            is ExportUiState.Success -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ExportEmerald.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                        .border(1.dp, ExportEmerald, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = ExportEmerald
                    )
                    Text(
                        text = exportState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = StudioTextPrimary
                    )
                }
            }
            is ExportUiState.Error -> {
                Text(
                    text = exportState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlertCoral,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AlertCoral.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .border(1.dp, AlertCoral, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                )
            }
            else -> Unit
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ExportToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = StudioTextSecondary,
            modifier = Modifier.weight(1f)
        )
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag),
            colors = CheckboxDefaults.colors(
                checkedColor = ExportEmerald,
                uncheckedColor = StudioTextSecondary
            )
        )
    }
}
