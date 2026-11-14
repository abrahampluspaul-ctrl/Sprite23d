package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.RigPreset
import com.example.domain.model.StudioProject
import com.example.engine.mesh.StarterSpriteGenerator
import com.example.ui.components.StudioChoiceChip
import com.example.ui.components.StudioSectionHeader
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

@Composable
fun StudioHubScreen(
    projects: List<StudioProject>,
    onImportSpriteUri: (android.net.Uri, RigPreset) -> Unit,
    onSelectStarter: (String, RigPreset) -> Unit,
    onOpenProject: (StudioProject) -> Unit,
    onDeleteProject: (String) -> Unit
) {
    var defaultPreset by remember { mutableStateOf(RigPreset.UNIVERSAL_HUMANOID) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                onImportSpriteUri(uri, defaultPreset)
            }
        }
    )

    val starterPreviews = remember {
        StarterSpriteGenerator.templates.associate { t ->
            t.id to StarterSpriteGenerator.generateBitmap(t.id).asImageBitmap()
        }
    }

    Scaffold(
        containerColor = StudioObsidian,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(StudioObsidian, ColorMixHeader, StudioObsidian)
                    )
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "SpriteForge 3D",
                                style = MaterialTheme.typography.headlineMedium,
                                color = StudioTextPrimary
                            )
                            Text(
                                text = "2D Sprite to Volumetric Rigged 3D Studio",
                                style = MaterialTheme.typography.bodyMedium,
                                color = StudioTextSecondary
                            )
                        }
                        Text(
                            text = "LOCAL STUDIO",
                            style = MaterialTheme.typography.labelSmall,
                            color = ExportEmerald,
                            modifier = Modifier
                                .background(ExportEmerald.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                                .border(1.dp, ExportEmerald.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // Hero Import Card
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(StudioSurfaceElevated, StudioSurface)
                                )
                            )
                            .border(1.dp, PrecisionCyan.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = PrecisionCyan,
                                modifier = Modifier.size(26.dp)
                            )
                            Column {
                                Text(
                                    text = "Convert 2D Sprite to 3D Model",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = StudioTextPrimary
                                )
                                Text(
                                    text = "AI volumetric inflation, UV texture mapping, auto-rigging & physics",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = StudioTextSecondary
                                )
                            }
                        }

                        Text(
                            text = "Default Engine Rig Standard",
                            style = MaterialTheme.typography.labelLarge,
                            color = StudioTextSecondary
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
                                    selected = defaultPreset == preset,
                                    onClick = { defaultPreset = preset },
                                    tag = "hub_preset_${preset.name.lowercase()}",
                                    subBadge = preset.badge
                                )
                            }
                        }

                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrecisionCyan,
                                contentColor = StudioObsidian
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("import_sprite_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "Import 2D Sprite from Gallery",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                // Starter Sprites
                item {
                    StudioSectionHeader(
                        title = "Starter Reference Sprites",
                        badge = "INSTANT AI CONVERT",
                        accent = GizmoAmber
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StarterSpriteGenerator.templates.forEach { tpl ->
                            Column(
                                modifier = Modifier
                                    .width(172.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(StudioSurfaceElevated)
                                    .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
                                    .clickable { onSelectStarter(tpl.id, tpl.defaultPreset) }
                                    .testTag("starter_sprite_${tpl.id}")
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(104.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(StudioObsidian)
                                        .border(1.dp, StudioBorder, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    starterPreviews[tpl.id]?.let { img ->
                                        Image(
                                            bitmap = img,
                                            contentDescription = tpl.title,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.size(92.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = tpl.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = StudioTextPrimary
                                )
                                Text(
                                    text = tpl.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = StudioTextSecondary,
                                    maxLines = 2
                                )
                                Text(
                                    text = tpl.defaultPreset.badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PrecisionCyan
                                )
                            }
                        }
                    }
                }

                // Saved 3D Projects
                item {
                    StudioSectionHeader(
                        title = "Saved 3D Models (${projects.size})",
                        badge = "ROOM LOCAL DB",
                        accent = ShaderViolet
                    )
                }

                if (projects.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(StudioSurface, RoundedCornerShape(14.dp))
                                .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ViewInAr,
                                contentDescription = null,
                                tint = PrecisionCyan,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "No converted 3D models saved yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = StudioTextPrimary
                            )
                            Text(
                                text = "Import a 2D sprite above or tap a starter character to enter the 3D Rigging, Material & Physics Studio.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = StudioTextSecondary
                            )
                        }
                    }
                } else {
                    items(projects, key = { it.id }) { proj ->
                        SavedProjectCard(
                            project = proj,
                            onOpen = { onOpenProject(proj) },
                            onDelete = { onDeleteProject(proj.id) }
                        )
                    }
                }
            }
        }
    }
}

private val ColorMixHeader = androidx.compose.ui.graphics.Color(0xFF0E1522)

@Composable
private fun SavedProjectCard(
    project: StudioProject,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val thumb = remember(project.spritePath) {
        runCatching { BitmapFactory.decodeFile(project.spritePath)?.asImageBitmap() }.getOrNull()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StudioSurfaceElevated)
            .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .testTag("saved_project_card_${project.id}")
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(StudioObsidian)
                .border(1.dp, StudioBorder, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb,
                    contentDescription = project.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(60.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.ViewInAr,
                    contentDescription = project.name,
                    tint = PrecisionCyan
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = project.name,
                style = MaterialTheme.typography.titleMedium,
                color = StudioTextPrimary
            )
            Text(
                text = "${project.rigPreset.displayName} • ${project.materialConfig.shaderMode.badge}",
                style = MaterialTheme.typography.bodyMedium,
                color = StudioTextSecondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${project.vertexCount} verts",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrecisionCyan
                )
                Text(
                    text = "${project.boneCount} bones",
                    style = MaterialTheme.typography.labelSmall,
                    color = GizmoAmber
                )
                Text(
                    text = "${project.physicsGroups.count { it.enabled }} phys",
                    style = MaterialTheme.typography.labelSmall,
                    color = ExportEmerald
                )
            }
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("delete_project_${project.id}")
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Delete ${project.name}",
                tint = StudioTextSecondary
            )
        }
    }
}
