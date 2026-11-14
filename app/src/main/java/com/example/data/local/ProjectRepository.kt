package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.domain.model.MeshDensity
import com.example.domain.model.RigPreset
import com.example.domain.model.StudioProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

class ProjectRepository(
    private val context: Context,
    private val dao: ProjectDao
) {
    private val modelsDir: File by lazy {
        File(context.filesDir, "models").apply { mkdirs() }
    }

    val projects: Flow<List<StudioProject>> = dao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun saveProject(project: StudioProject) {
        dao.upsert(project.toEntity())
    }

    suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
        File(modelsDir, "$id.png").delete()
    }

    suspend fun saveSpriteBitmap(projectId: String, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val file = File(modelsDir, "$projectId.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        file.absolutePath
    }

    suspend fun loadSpriteBitmap(path: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext null
        BitmapFactory.decodeFile(file.absolutePath)
    }

    suspend fun decodeScaledSpriteFromUri(uri: Uri, maxDim: Int = 512): Bitmap? =
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val raw = BitmapFactory.decodeStream(stream) ?: return@withContext null
                val scale = maxDim.toFloat() / max(raw.width, raw.height).toFloat()
                if (scale >= 1f) {
                    raw.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    val w = max(32, (raw.width * scale).roundToInt())
                    val h = max(32, (raw.height * scale).roundToInt())
                    Bitmap.createScaledBitmap(raw, w, h, true)
                }
            }
        }

    private fun ProjectEntity.toDomain(): StudioProject {
        return StudioProject(
            id = id,
            name = name,
            spritePath = spritePath,
            rigPreset = runCatching { RigPreset.valueOf(rigPresetName) }
                .getOrDefault(RigPreset.UNIVERSAL_HUMANOID),
            meshDensity = runCatching { MeshDensity.valueOf(meshDensityName) }
                .getOrDefault(MeshDensity.BALANCED),
            vertexCount = vertexCount,
            boneCount = boneCount,
            analysis = ProjectSerialization.jsonToAnalysis(analysisJson),
            bones = ProjectSerialization.jsonToBones(bonesJson),
            materialConfig = ProjectSerialization.jsonToMaterial(materialJson),
            physicsGroups = ProjectSerialization.jsonToPhysics(physicsJson),
            animationSettings = ProjectSerialization.jsonToAnimation(animationJson),
            updatedAt = updatedAt
        )
    }

    private fun StudioProject.toEntity(): ProjectEntity {
        return ProjectEntity(
            id = id,
            name = name,
            spritePath = spritePath,
            rigPresetName = rigPreset.name,
            meshDensityName = meshDensity.name,
            vertexCount = vertexCount,
            boneCount = boneCount,
            analysisJson = ProjectSerialization.analysisToJson(analysis),
            bonesJson = ProjectSerialization.bonesToJson(bones),
            materialJson = ProjectSerialization.materialToJson(materialConfig),
            physicsJson = ProjectSerialization.physicsToJson(physicsGroups),
            animationJson = ProjectSerialization.animationToJson(animationSettings),
            updatedAt = updatedAt
        )
    }
}
