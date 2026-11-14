package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.SpriteAnalysisRepository
import com.example.data.local.AppDatabase
import com.example.data.local.ProjectRepository
import com.example.domain.model.AnimationSettings
import com.example.domain.model.BoneConstraint
import com.example.domain.model.ExportOptions
import com.example.domain.model.LocomotionMode
import com.example.domain.model.MaterialConfig
import com.example.domain.model.MeshDensity
import com.example.domain.model.MeshGroupPhysics
import com.example.domain.model.MeshGroupType
import com.example.domain.model.RigPreset
import com.example.domain.model.SpriteAnalysisResult
import com.example.domain.model.StudioProject
import com.example.domain.model.VolumetricMesh
import com.example.domain.presets.RigPresets
import com.example.engine.export.ExportArtifact
import com.example.engine.export.ModelExporter
import com.example.engine.mesh.StarterSpriteGenerator
import com.example.engine.mesh.VolumetricMeshBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface AnalysisState {
    data object Idle : AnalysisState
    data class Analyzing(val sprite: Bitmap, val stepLabel: String, val preset: RigPreset) : AnalysisState
    data class Ready(
        val sprite: Bitmap,
        val analysis: SpriteAnalysisResult,
        val selectedPreset: RigPreset,
        val selectedDensity: MeshDensity
    ) : AnalysisState
    data class Error(val sprite: Bitmap, val message: String, val preset: RigPreset) : AnalysisState
}

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data object Exporting : ExportUiState
    data class Success(val message: String, val shareIntent: Intent?) : ExportUiState
    data class Error(val message: String) : ExportUiState
}

data class StudioWorkspaceState(
    val project: StudioProject? = null,
    val spriteBitmap: Bitmap? = null,
    val mesh: VolumetricMesh? = null,
    val selectedBoneId: String? = "hips",
    val selectedPhysicsGroup: MeshGroupType = MeshGroupType.CLOTH_CAPE,
    val clampedBoneIds: Set<String> = emptySet(),
    val joyX: Float = 0f,
    val joyY: Float = 0f,
    val resetCameraSignal: Int = 0,
    val windGustSignal: Int = 0,
    val exportOptions: ExportOptions = ExportOptions(),
    val exportState: ExportUiState = ExportUiState.Idle,
    val isBuildingMesh: Boolean = false
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val projectRepo = ProjectRepository(application, database.projectDao())
    private val analysisRepo = SpriteAnalysisRepository()

    val savedProjects: StateFlow<List<StudioProject>> = projectRepo.projects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _analysisState = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysisState: StateFlow<AnalysisState> = _analysisState.asStateFlow()

    private val _workspace = MutableStateFlow(StudioWorkspaceState())
    val workspace: StateFlow<StudioWorkspaceState> = _workspace.asStateFlow()

    private var persistJob: Job? = null
    private var pendingExportArtifact: ExportArtifact? = null

    fun startAnalysisFromUri(uri: Uri, preset: RigPreset = RigPreset.UNIVERSAL_HUMANOID) {
        viewModelScope.launch {
            val bitmap = projectRepo.decodeScaledSpriteFromUri(uri, maxDim = 512)
            if (bitmap == null) {
                return@launch
            }
            runSpriteAnalysis(bitmap, preset)
        }
    }

    fun startAnalysisFromStarter(templateId: String, preset: RigPreset) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                StarterSpriteGenerator.generateBitmap(templateId)
            }
            runSpriteAnalysis(bitmap, preset)
        }
    }

    fun retryAnalysis() {
        val current = _analysisState.value
        when (current) {
            is AnalysisState.Error -> runSpriteAnalysis(current.sprite, current.preset)
            is AnalysisState.Ready -> runSpriteAnalysis(current.sprite, current.selectedPreset)
            else -> Unit
        }
    }

    private fun runSpriteAnalysis(bitmap: Bitmap, preset: RigPreset) {
        viewModelScope.launch {
            _analysisState.value = AnalysisState.Analyzing(
                sprite = bitmap,
                stepLabel = "Extracting alpha contour & silhouette depth...",
                preset = preset
            )
            try {
                _analysisState.value = AnalysisState.Analyzing(
                    sprite = bitmap,
                    stepLabel = "Gemini AI analyzing anatomy, material & physics zones...",
                    preset = preset
                )
                val result = analysisRepo.analyzeSprite(bitmap, preset)
                _analysisState.value = AnalysisState.Ready(
                    sprite = bitmap,
                    analysis = result,
                    selectedPreset = preset,
                    selectedDensity = MeshDensity.BALANCED
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _analysisState.value = AnalysisState.Error(
                    sprite = bitmap,
                    message = e.localizedMessage ?: "AI sprite analysis failed.",
                    preset = preset
                )
            }
        }
    }

    fun updateAnalysisPreset(preset: RigPreset) {
        val current = _analysisState.value
        if (current is AnalysisState.Ready) {
            _analysisState.value = current.copy(selectedPreset = preset)
        }
    }

    fun updateAnalysisDensity(density: MeshDensity) {
        val current = _analysisState.value
        if (current is AnalysisState.Ready) {
            _analysisState.value = current.copy(selectedDensity = density)
        }
    }

    fun buildAndOpenProject(customName: String?, onReady: () -> Unit) {
        val ready = _analysisState.value as? AnalysisState.Ready ?: return
        viewModelScope.launch {
            val projectId = UUID.randomUUID().toString()
            val spritePath = projectRepo.saveSpriteBitmap(projectId, ready.sprite)
            val bones = RigPresets.buildBonesForPreset(ready.selectedPreset, ready.analysis.joints)

            val mesh = withContext(Dispatchers.Default) {
                VolumetricMeshBuilder.buildMesh(ready.sprite, ready.analysis, bones, ready.selectedDensity)
            }

            val initialMaterial = MaterialConfig(
                roughness = ready.analysis.suggestedRoughness,
                metallic = ready.analysis.suggestedMetallic,
                proceduralTexture = ready.analysis.suggestedProcedural,
                shaderMode = ready.analysis.suggestedShader
            )
            val initialPhysics = RigPresets.defaultPhysicsGroups(ready.analysis)

            val project = StudioProject(
                id = projectId,
                name = customName?.takeIf { it.isNotBlank() } ?: ready.analysis.characterTitle,
                spritePath = spritePath,
                rigPreset = ready.selectedPreset,
                meshDensity = ready.selectedDensity,
                vertexCount = mesh.vertexCount,
                boneCount = bones.size,
                analysis = ready.analysis,
                bones = bones,
                materialConfig = initialMaterial,
                physicsGroups = initialPhysics,
                animationSettings = AnimationSettings(),
                updatedAt = System.currentTimeMillis()
            )

            projectRepo.saveProject(project)
            _workspace.value = StudioWorkspaceState(
                project = project,
                spriteBitmap = ready.sprite,
                mesh = mesh,
                selectedBoneId = bones.firstOrNull()?.id
            )
            onReady()
        }
    }

    fun openSavedProject(project: StudioProject, onReady: () -> Unit) {
        viewModelScope.launch {
            val sprite = projectRepo.loadSpriteBitmap(project.spritePath)
                ?: withContext(Dispatchers.Default) { StarterSpriteGenerator.generateBitmap("vanguard_knight") }
            val mesh = withContext(Dispatchers.Default) {
                VolumetricMeshBuilder.buildMesh(sprite, project.analysis, project.bones, project.meshDensity)
            }
            _workspace.value = StudioWorkspaceState(
                project = project,
                spriteBitmap = sprite,
                mesh = mesh,
                selectedBoneId = project.bones.firstOrNull()?.id
            )
            onReady()
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            projectRepo.deleteProject(id)
        }
    }

    fun updateJoystick(x: Float, y: Float) {
        _workspace.update { it.copy(joyX = x.coerceIn(-1f, 1f), joyY = y.coerceIn(-1f, 1f)) }
    }

    fun updateClampedBones(clamped: Set<String>) {
        if (_workspace.value.clampedBoneIds != clamped) {
            _workspace.update { it.copy(clampedBoneIds = clamped) }
        }
    }

    fun selectBone(boneId: String) {
        _workspace.update { it.copy(selectedBoneId = boneId) }
    }

    fun updateBoneConstraint(boneId: String, transform: (BoneConstraint) -> BoneConstraint) {
        updateProject { proj ->
            val updatedBones = proj.bones.map { b ->
                if (b.id == boneId) b.copy(constraint = transform(b.constraint)) else b
            }
            proj.copy(bones = updatedBones, updatedAt = System.currentTimeMillis())
        }
    }

    fun applyRigPresetInWorkspace(preset: RigPreset) {
        val current = _workspace.value
        val proj = current.project ?: return
        val sprite = current.spriteBitmap ?: return
        viewModelScope.launch {
            _workspace.update { it.copy(isBuildingMesh = true) }
            val newBones = RigPresets.buildBonesForPreset(preset, proj.analysis.joints)
            val newMesh = withContext(Dispatchers.Default) {
                VolumetricMeshBuilder.buildMesh(sprite, proj.analysis, newBones, proj.meshDensity)
            }
            val updatedProj = proj.copy(
                rigPreset = preset,
                bones = newBones,
                boneCount = newBones.size,
                vertexCount = newMesh.vertexCount,
                updatedAt = System.currentTimeMillis()
            )
            _workspace.update {
                it.copy(
                    project = updatedProj,
                    mesh = newMesh,
                    selectedBoneId = newBones.firstOrNull()?.id,
                    isBuildingMesh = false
                )
            }
            schedulePersist(updatedProj)
        }
    }

    fun resetSelectedBoneConstraint() {
        val proj = _workspace.value.project ?: return
        val boneId = _workspace.value.selectedBoneId ?: return
        val defaultBones = RigPresets.buildBonesForPreset(proj.rigPreset, proj.analysis.joints)
        val defaultBone = defaultBones.find { it.id == boneId } ?: return
        updateBoneConstraint(boneId) { defaultBone.constraint }
    }

    fun updateMaterial(transform: (MaterialConfig) -> MaterialConfig) {
        updateProject { proj ->
            proj.copy(
                materialConfig = transform(proj.materialConfig),
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun selectPhysicsGroup(groupType: MeshGroupType) {
        _workspace.update { it.copy(selectedPhysicsGroup = groupType) }
    }

    fun updatePhysicsGroup(groupType: MeshGroupType, transform: (MeshGroupPhysics) -> MeshGroupPhysics) {
        updateProject { proj ->
            val updated = proj.physicsGroups.map { pg ->
                if (pg.groupType == groupType) transform(pg) else pg
            }
            proj.copy(physicsGroups = updated, updatedAt = System.currentTimeMillis())
        }
    }

    fun triggerWindGust() {
        _workspace.update { it.copy(windGustSignal = it.windGustSignal + 1) }
    }

    fun updateAnimationSettings(transform: (AnimationSettings) -> AnimationSettings) {
        updateProject { proj ->
            proj.copy(
                animationSettings = transform(proj.animationSettings),
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun setLocomotionMode(mode: LocomotionMode) {
        updateAnimationSettings { it.copy(locomotionMode = mode) }
    }

    fun resetCamera() {
        _workspace.update { it.copy(resetCameraSignal = it.resetCameraSignal + 1) }
    }

    fun updateExportOptions(transform: (ExportOptions) -> ExportOptions) {
        _workspace.update { it.copy(exportOptions = transform(it.exportOptions), exportState = ExportUiState.Idle) }
    }

    fun prepareExportForSaf(onArtifactReady: (fileName: String, mimeType: String) -> Unit) {
        val state = _workspace.value
        val proj = state.project ?: return
        val mesh = state.mesh ?: return
        val sprite = state.spriteBitmap ?: return

        viewModelScope.launch {
            _workspace.update { it.copy(exportState = ExportUiState.Exporting) }
            try {
                val artifact = withContext(Dispatchers.Default) {
                    ModelExporter.export(proj, mesh, sprite, state.exportOptions)
                }
                pendingExportArtifact = artifact
                onArtifactReady(artifact.fileName, artifact.mimeType)
            } catch (e: Exception) {
                _workspace.update {
                    it.copy(exportState = ExportUiState.Error(e.localizedMessage ?: "Export build failed."))
                }
            }
        }
    }

    fun writePendingExportToUri(uri: Uri?) {
        val artifact = pendingExportArtifact
        if (uri == null || artifact == null) {
            _workspace.update { it.copy(exportState = ExportUiState.Idle) }
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(artifact.bytes)
                    } ?: throw IllegalStateException("Unable to open destination file.")
                }
                val shareIntent = buildShareIntent(artifact)
                _workspace.update {
                    it.copy(exportState = ExportUiState.Success("Saved ${artifact.fileName} (${artifact.summary})", shareIntent))
                }
            } catch (e: Exception) {
                _workspace.update {
                    it.copy(exportState = ExportUiState.Error(e.localizedMessage ?: "Failed to save file."))
                }
            }
        }
    }

    fun shareModelDirectly(onLaunchShare: (Intent) -> Unit) {
        val state = _workspace.value
        val proj = state.project ?: return
        val mesh = state.mesh ?: return
        val sprite = state.spriteBitmap ?: return

        viewModelScope.launch {
            _workspace.update { it.copy(exportState = ExportUiState.Exporting) }
            try {
                val artifact = withContext(Dispatchers.Default) {
                    ModelExporter.export(proj, mesh, sprite, state.exportOptions)
                }
                val intent = withContext(Dispatchers.IO) { buildShareIntent(artifact) }
                _workspace.update {
                    it.copy(exportState = ExportUiState.Success("Ready to share: ${artifact.summary}", intent))
                }
                onLaunchShare(intent)
            } catch (e: Exception) {
                _workspace.update {
                    it.copy(exportState = ExportUiState.Error(e.localizedMessage ?: "Failed to prepare share."))
                }
            }
        }
    }

    private fun buildShareIntent(artifact: ExportArtifact): Intent {
        val app = getApplication<Application>()
        val exportDir = File(app.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, artifact.fileName)
        file.writeBytes(artifact.bytes)
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = artifact.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "SpriteForge 3D Export: ${artifact.fileName}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun updateProject(transform: (StudioProject) -> StudioProject) {
        val current = _workspace.value.project ?: return
        val updated = transform(current)
        _workspace.update { it.copy(project = updated) }
        schedulePersist(updated)
    }

    private fun schedulePersist(project: StudioProject) {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(350L)
            projectRepo.saveProject(project)
        }
    }
}
