package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.MeshDensity
import com.example.domain.model.RigPreset
import com.example.ui.components.StudioChoiceChip
import com.example.ui.components.StudioSectionHeader
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.ExportEmerald
import com.example.ui.theme.GizmoAmber
import com.example.ui.theme.PrecisionCyan
import com.example.ui.theme.ShaderViolet
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.ui.viewmodel.AnalysisState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpriteAnalysisScreen(
    state: AnalysisState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onUpdatePreset: (RigPreset) -> Unit,
    onUpdateDensity: (MeshDensity) -> Unit,
    onBuildAndOpen: (String?) -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    Scaffold(
        containerColor = StudioObsidian,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "AI Sprite Volumetric Analysis",
                        style = MaterialTheme.typography.titleLarge,
                        color = StudioTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("analysis_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = context.getString(R.string.cd_back),
                            tint = StudioTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioSurface)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (state) {
                    AnalysisState.Idle -> {
                        Text(
                            text = "Select a 2D sprite from the Studio Hub to start.",
                            color = StudioTextSecondary
                        )
                    }

                    is AnalysisState.Analyzing -> {
                        SpriteLandmarkPreview(sprite = state.sprite, joints = emptyList())
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(StudioSurfaceElevated, RoundedCornerShape(14.dp))
                                .border(1.dp, PrecisionCyan.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = PrecisionCyan)
                            Text(
                                text = state.stepLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = StudioTextPrimary
                            )
                            Text(
                                text = "Inferring 3D depth curve, bone joint coordinates, PBR material properties, and cloth/hair physics zones...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = StudioTextSecondary
                            )
                        }
                    }

                    is AnalysisState.Error -> {
                        SpriteLandmarkPreview(sprite = state.sprite, joints = emptyList())
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AlertCoral.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                                .border(1.dp, AlertCoral, RoundedCornerShape(14.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = AlertCoral
                                )
                                Text(
                                    text = "Sprite Analysis Failed",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = AlertCoral
                                )
                            }
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = StudioTextPrimary
                            )
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AlertCoral,
                                    contentColor = StudioObsidian
                                ),
                                modifier = Modifier.testTag("retry_analysis_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Text("Retry AI Analysis")
                            }
                        }
                    }

                    is AnalysisState.Ready -> {
                        var customName by remember(state.analysis.characterTitle) {
                            mutableStateOf(state.analysis.characterTitle)
                        }
                        var isBuilding by remember { mutableStateOf(false) }

                        SpriteLandmarkPreview(
                            sprite = state.sprite,
                            joints = state.analysis.joints
                        )

                        // AI Diagnostic Summary Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(StudioSurfaceElevated, RoundedCornerShape(14.dp))
                                .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StudioSectionHeader(
                                title = "AI Volumetric Profile",
                                badge = "${state.analysis.archetype.uppercase()} • ${state.analysis.depthProfile.uppercase()}",
                                accent = ExportEmerald
                            )
                            Text(
                                text = state.analysis.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = StudioTextSecondary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DetectionBadge("Joints: ${state.analysis.joints.size}", PrecisionCyan)
                                if (state.analysis.hasCapeOrCloth) DetectionBadge("Cloth Sim", ExportEmerald)
                                if (state.analysis.hasFlowingHair) DetectionBadge("Hair Sim", ShaderViolet)
                                if (state.analysis.hasSoftBodyParts) DetectionBadge("Soft Body", GizmoAmber)
                            }
                        }

                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            label = { Text("3D Model Name") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrecisionCyan,
                                unfocusedBorderColor = StudioBorder,
                                focusedTextColor = StudioTextPrimary,
                                unfocusedTextColor = StudioTextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("model_name_input")
                        )

                        StudioSectionHeader(title = "Target Rigging Preset", badge = state.selectedPreset.badge)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RigPreset.entries.forEach { preset ->
                                StudioChoiceChip(
                                    label = preset.displayName,
                                    selected = state.selectedPreset == preset,
                                    onClick = { onUpdatePreset(preset) },
                                    tag = "analysis_preset_${preset.name.lowercase()}",
                                    subBadge = preset.badge
                                )
                            }
                        }

                        StudioSectionHeader(title = "Volumetric Mesh Density", badge = state.selectedDensity.label)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MeshDensity.entries.forEach { density ->
                                StudioChoiceChip(
                                    label = density.label,
                                    selected = state.selectedDensity == density,
                                    onClick = { onUpdateDensity(density) },
                                    tag = "analysis_density_${density.name.lowercase()}"
                                )
                            }
                        }

                        Button(
                            onClick = {
                                isBuilding = true
                                onBuildAndOpen(customName)
                            },
                            enabled = !isBuilding,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrecisionCyan,
                                contentColor = StudioObsidian
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("generate_3d_model_btn")
                        ) {
                            if (isBuilding) {
                                CircularProgressIndicator(
                                    color = StudioObsidian,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.AutoFixHigh,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                            Text(
                                text = "Build 3D Mesh & Enter Test Studio",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SpriteLandmarkPreview(
    sprite: android.graphics.Bitmap,
    joints: List<com.example.domain.model.JointLandmark>
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(236.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(StudioSurface)
            .border(1.dp, StudioBorder, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(204.dp)) {
            Image(
                bitmap = sprite.asImageBitmap(),
                contentDescription = "Uploaded 2D sprite preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            if (joints.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    joints.forEach { j ->
                        val p = Offset(j.normX * size.width, j.normY * size.height)
                        drawCircle(color = GizmoAmber, radius = 5.dp.toPx(), center = p)
                        drawCircle(
                            color = PrecisionCyan,
                            radius = 7.dp.toPx(),
                            center = p,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetectionBadge(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .border(0.5.dp, color.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
