package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.LocomotionMode
import com.example.engine.render.Sprite3DViewport
import com.example.ui.components.ExportSheetTab
import com.example.ui.components.MaterialShaderTab
import com.example.ui.components.OmnidirectionalJoystick
import com.example.ui.components.PhysicsEngineTab
import com.example.ui.components.RigConstraintsTab
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
import com.example.ui.viewmodel.StudioViewModel
import com.example.ui.viewmodel.StudioWorkspaceState
import java.util.Locale
import kotlin.math.roundToInt

private enum class StudioTab(val title: String, val icon: ImageVector) {
    RIG("Rig", Icons.Rounded.AccessibilityNew),
    MATERIALS("Materials", Icons.Rounded.Palette),
    PHYSICS("Physics", Icons.Rounded.Waves),
    EXPORT("Export", Icons.Rounded.FileUpload)
}

@Composable
fun StudioWorkspaceScreen(
    workspace: StudioWorkspaceState,
    viewModel: StudioViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val project = workspace.project
    if (project == null) {
        onBack()
        return
    }

    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var fullScreenViewport by remember { mutableStateOf(false) }

    val safExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(workspace.exportOptions.format.mimeType),
        onResult = { uri -> viewModel.writePendingExportToUri(uri) }
    )

    Scaffold(
        containerColor = StudioObsidian,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWide = maxWidth >= 720.dp

            if (isWide && !fullScreenViewport) {
                // Tablet / Foldable Side-by-Side Supporting Pane
                Row(modifier = Modifier.fillMaxSize()) {
                    ViewportPane(
                        workspace = workspace,
                        viewModel = viewModel,
                        fullScreenViewport = false,
                        onToggleFullScreen = { fullScreenViewport = !fullScreenViewport },
                        onBack = onBack,
                        modifier = Modifier
                            .weight(0.60f)
                            .fillMaxHeight()
                    )

                    InspectorDock(
                        workspace = workspace,
                        viewModel = viewModel,
                        selectedTab = StudioTab.entries[selectedTabIndex],
                        onSelectTab = { selectedTabIndex = it.ordinal },
                        onLaunchSafExport = { fileName -> safExportLauncher.launch(fileName) },
                        onLaunchShare = { intent ->
                            context.startActivity(Intent.createChooser(intent, "Share 3D Model"))
                        },
                        modifier = Modifier
                            .weight(0.40f)
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    )
                }
            } else {
                // Handheld Phone Layout: Top 3D Viewport + Bottom Tabbed Studio Dock
                Column(modifier = Modifier.fillMaxSize()) {
                    ViewportPane(
                        workspace = workspace,
                        viewModel = viewModel,
                        fullScreenViewport = fullScreenViewport,
                        onToggleFullScreen = { fullScreenViewport = !fullScreenViewport },
                        onBack = onBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(if (fullScreenViewport) 1f else 0.54f)
                    )

                    if (!fullScreenViewport) {
                        InspectorDock(
                            workspace = workspace,
                            viewModel = viewModel,
                            selectedTab = StudioTab.entries[selectedTabIndex],
                            onSelectTab = { selectedTabIndex = it.ordinal },
                            onLaunchSafExport = { fileName -> safExportLauncher.launch(fileName) },
                            onLaunchShare = { intent ->
                                context.startActivity(Intent.createChooser(intent, "Share 3D Model"))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.46f)
                                .navigationBarsPadding()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewportPane(
    workspace: StudioWorkspaceState,
    viewModel: StudioViewModel,
    fullScreenViewport: Boolean,
    onToggleFullScreen: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val project = workspace.project ?: return
    val anim = project.animationSettings
    var showAnimPanel by remember { mutableStateOf(false) }

    Box(modifier = modifier.background(StudioObsidian)) {
        Sprite3DViewport(
            mesh = workspace.mesh,
            spriteBitmap = workspace.spriteBitmap,
            bones = project.bones,
            selectedBoneId = workspace.selectedBoneId,
            physicsGroups = project.physicsGroups,
            materialConfig = project.materialConfig,
            animationSettings = anim,
            joyX = workspace.joyX,
            joyY = workspace.joyY,
            resetCameraSignal = workspace.resetCameraSignal,
            windGustSignal = workspace.windGustSignal,
            onClampedBonesUpdated = viewModel::updateClampedBones,
            modifier = Modifier.fillMaxSize()
        )

        // Top HUD Overlay (Safe Drawing Insets for Notch / Status Bar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(StudioSurface.copy(alpha = 0.85f))
                    .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                    .padding(end = 10.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("studio_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = context.getString(R.string.cd_back),
                        tint = StudioTextPrimary
                    )
                }
                Column {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = StudioTextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = "${project.vertexCount}v • ${project.bones.size}b • ${project.materialConfig.shaderMode.badge}",
                        style = MaterialTheme.typography.labelSmall,
                        color = PrecisionCyan
                    )
                }
            }

            // Viewport Quick Toggles
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(StudioSurface.copy(alpha = 0.85f))
                    .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 4.dp)
            ) {
                IconButton(
                    onClick = { viewModel.updateAnimationSettings { it.copy(showSkeleton = !it.showSkeleton) } },
                    modifier = Modifier.testTag("toggle_skeleton_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccessibilityNew,
                        contentDescription = context.getString(R.string.cd_skeleton_toggle),
                        tint = if (anim.showSkeleton) PrecisionCyan else StudioTextSecondary
                    )
                }
                IconButton(
                    onClick = { viewModel.updateAnimationSettings { it.copy(showConstraintGizmos = !it.showConstraintGizmos) } },
                    modifier = Modifier.testTag("toggle_gizmos_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Radar,
                        contentDescription = context.getString(R.string.cd_gizmo_toggle),
                        tint = if (anim.showConstraintGizmos) GizmoAmber else StudioTextSecondary
                    )
                }
                IconButton(
                    onClick = { viewModel.resetCamera() },
                    modifier = Modifier.testTag("reset_camera_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CenterFocusStrong,
                        contentDescription = context.getString(R.string.cd_reset_camera),
                        tint = StudioTextPrimary
                    )
                }
                IconButton(
                    onClick = onToggleFullScreen,
                    modifier = Modifier.testTag("toggle_fullscreen_btn")
                ) {
                    Icon(
                        imageVector = if (fullScreenViewport) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        contentDescription = if (fullScreenViewport) "Exit full screen" else "Expand viewport",
                        tint = StudioTextPrimary
                    )
                }
            }
        }

        // Bottom-Left: Omnidirectional 360° Joystick
        OmnidirectionalJoystick(
            joyX = workspace.joyX,
            joyY = workspace.joyY,
            onVectorChanged = viewModel::updateJoystick,
            size = 112.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .then(if (fullScreenViewport) Modifier.navigationBarsPadding() else Modifier)
                .padding(start = 12.dp, bottom = 10.dp)
        )

        // Bottom-Right: Animation Levels & Locomotion Controls HUD
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .then(if (fullScreenViewport) Modifier.navigationBarsPadding() else Modifier)
                .padding(end = 12.dp, bottom = 10.dp)
        ) {
            if (showAnimPanel) {
                Column(
                    modifier = Modifier
                        .width(214.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(StudioSurface.copy(alpha = 0.94f))
                        .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Anim Level", style = MaterialTheme.typography.labelSmall, color = StudioTextSecondary)
                        Text("${(anim.animationLevel * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = PrecisionCyan)
                    }
                    Slider(
                        value = anim.animationLevel,
                        onValueChange = { v -> viewModel.updateAnimationSettings { it.copy(animationLevel = v) } },
                        valueRange = 0f..1.5f,
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("slider_anim_level"),
                        colors = SliderDefaults.colors(thumbColor = PrecisionCyan, activeTrackColor = PrecisionCyan)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Speed", style = MaterialTheme.typography.labelSmall, color = StudioTextSecondary)
                        Text(String.format(Locale.US, "%.2fx", anim.playbackSpeed), style = MaterialTheme.typography.labelSmall, color = GizmoAmber)
                    }
                    Slider(
                        value = anim.playbackSpeed,
                        onValueChange = { v -> viewModel.updateAnimationSettings { it.copy(playbackSpeed = v) } },
                        valueRange = 0.25f..2.0f,
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("slider_anim_speed"),
                        colors = SliderDefaults.colors(thumbColor = GizmoAmber, activeTrackColor = GizmoAmber)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LocomotionMode.entries.forEach { mode ->
                            val selected = anim.locomotionMode == mode
                            Text(
                                text = mode.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) StudioObsidian else StudioTextPrimary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) PrecisionCyan else StudioSurfaceElevated)
                                    .clickable { viewModel.setLocomotionMode(mode) }
                                    .testTag("locomotion_mode_${mode.name.lowercase()}")
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(StudioSurface.copy(alpha = 0.9f))
                    .border(1.dp, PrecisionCyan.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .clickable { showAnimPanel = !showAnimPanel }
                    .testTag("anim_hud_toggle_btn")
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Animation,
                    contentDescription = "Animation level controls",
                    tint = PrecisionCyan,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "${anim.locomotionMode.displayName} • ${(anim.animationLevel * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = StudioTextPrimary
                )
            }
        }
    }
}

@Composable
private fun InspectorDock(
    workspace: StudioWorkspaceState,
    viewModel: StudioViewModel,
    selectedTab: StudioTab,
    onSelectTab: (StudioTab) -> Unit,
    onLaunchSafExport: (String) -> Unit,
    onLaunchShare: (Intent) -> Unit,
    modifier: Modifier = Modifier
) {
    val project = workspace.project ?: return

    Column(
        modifier = modifier
            .background(StudioSurface)
            .border(1.dp, StudioBorder)
    ) {
        // Tab Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioObsidian)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StudioTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                val accent = when (tab) {
                    StudioTab.RIG -> PrecisionCyan
                    StudioTab.MATERIALS -> ShaderViolet
                    StudioTab.PHYSICS -> ExportEmerald
                    StudioTab.EXPORT -> GizmoAmber
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accent.copy(alpha = 0.16f) else StudioSurface)
                        .border(1.dp, if (isSelected) accent else StudioBorder, RoundedCornerShape(10.dp))
                        .clickable { onSelectTab(tab) }
                        .testTag("studio_tab_${tab.name.lowercase()}")
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        tint = if (isSelected) accent else StudioTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) StudioTextPrimary else StudioTextSecondary,
                        modifier = Modifier.padding(start = 4.dp),
                        maxLines = 1
                    )
                }
            }
        }

        // Tab Content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                StudioTab.RIG -> RigConstraintsTab(
                    currentPreset = project.rigPreset,
                    bones = project.bones,
                    selectedBoneId = workspace.selectedBoneId,
                    clampedBoneIds = workspace.clampedBoneIds,
                    onApplyPreset = viewModel::applyRigPresetInWorkspace,
                    onSelectBone = viewModel::selectBone,
                    onUpdateConstraint = viewModel::updateBoneConstraint,
                    onResetBoneConstraint = viewModel::resetSelectedBoneConstraint
                )
                StudioTab.MATERIALS -> MaterialShaderTab(
                    material = project.materialConfig,
                    onUpdateMaterial = viewModel::updateMaterial
                )
                StudioTab.PHYSICS -> PhysicsEngineTab(
                    physicsGroups = project.physicsGroups,
                    selectedGroup = workspace.selectedPhysicsGroup,
                    showPhysicsVectors = project.animationSettings.showPhysicsVectors,
                    onSelectGroup = viewModel::selectPhysicsGroup,
                    onUpdateGroup = viewModel::updatePhysicsGroup,
                    onTogglePhysicsVectors = { show ->
                        viewModel.updateAnimationSettings { it.copy(showPhysicsVectors = show) }
                    },
                    onTriggerWindGust = viewModel::triggerWindGust
                )
                StudioTab.EXPORT -> ExportSheetTab(
                    options = workspace.exportOptions,
                    exportState = workspace.exportState,
                    vertexCount = project.vertexCount,
                    boneCount = project.bones.size,
                    activePhysicsCount = project.physicsGroups.count { it.enabled },
                    onUpdateOptions = viewModel::updateExportOptions,
                    onSaveToDevice = {
                        viewModel.prepareExportForSaf { fileName, _ ->
                            onLaunchSafExport(fileName)
                        }
                    },
                    onShareModel = {
                        viewModel.shareModelDirectly(onLaunchShare)
                    }
                )
            }
        }
    }
}
