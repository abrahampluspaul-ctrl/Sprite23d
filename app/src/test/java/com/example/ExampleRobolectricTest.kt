package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.AnimationSettings
import com.example.domain.model.ExportFormat
import com.example.domain.model.ExportOptions
import com.example.domain.model.LocomotionMode
import com.example.domain.model.MaterialConfig
import com.example.domain.model.MeshDensity
import com.example.domain.model.ProceduralTextureType
import com.example.domain.model.RigPreset
import com.example.domain.model.ShaderMode
import com.example.domain.model.SpriteAnalysisResult
import com.example.domain.model.StudioProject
import com.example.domain.model.TargetEngine
import com.example.domain.presets.RigPresets
import com.example.engine.export.ModelExporter
import com.example.engine.mesh.StarterSpriteGenerator
import com.example.engine.mesh.VolumetricMeshBuilder
import com.example.engine.physics.PhysicsAndRigSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SpriteForge 3D", appName)
    }

    @Test
    fun `volumetric mesh builder, physics solver, and exporters produce valid output`() {
        val sprite = StarterSpriteGenerator.generateBitmap("vanguard_knight")
        val analysis = SpriteAnalysisResult(
            characterTitle = "Vanguard Knight",
            archetype = "humanoid",
            summary = "Armored knight with cloak",
            depthProfile = "balanced",
            thicknessScale = 0.34f,
            hasCapeOrCloth = true,
            hasFlowingHair = true,
            hasSoftBodyParts = false,
            suggestedRoughness = 0.35f,
            suggestedMetallic = 0.65f,
            suggestedShader = ShaderMode.PBR_STANDARD,
            suggestedProcedural = ProceduralTextureType.BRUSHED_METAL,
            joints = emptyList()
        )
        val bones = RigPresets.buildBonesForPreset(RigPreset.UNIVERSAL_HUMANOID)
        val mesh = VolumetricMeshBuilder.buildMesh(sprite, analysis, bones, MeshDensity.LOW)

        assertTrue(mesh.vertexCount > 100)
        assertTrue(mesh.triangleCount > 100)
        assertEquals(mesh.vertexCount * 3, mesh.normals.size)

        // Tighten upper_arm_l constraint to verify clamping detection
        val constrainedBones = bones.map {
            if (it.id == "upper_arm_l") {
                it.copy(constraint = it.constraint.copy(rotMinX = -5f, rotMaxX = 5f))
            } else it
        }

        val solver = PhysicsAndRigSolver()
        val physics = RigPresets.defaultPhysicsGroups(analysis)
        val frame = solver.step(
            dtRaw = 0.016f,
            mesh = mesh,
            bones = constrainedBones,
            physicsGroups = physics,
            animSettings = AnimationSettings(
                animationLevel = 1.2f,
                locomotionMode = LocomotionMode.RUN
            ),
            joyX = 0.8f,
            joyY = -0.6f
        )
        assertEquals(mesh.basePositions.size, frame.deformedPositions.size)
        assertTrue(frame.clampedBoneIds.contains("upper_arm_l"))

        val project = StudioProject(
            id = "test-1",
            name = "Vanguard Knight",
            spritePath = "",
            rigPreset = RigPreset.UNIVERSAL_HUMANOID,
            meshDensity = MeshDensity.LOW,
            vertexCount = mesh.vertexCount,
            boneCount = constrainedBones.size,
            analysis = analysis,
            bones = constrainedBones,
            materialConfig = MaterialConfig(shaderMode = ShaderMode.CUSTOM_UNIFORM_LAB),
            physicsGroups = physics,
            animationSettings = AnimationSettings(),
            updatedAt = 0L
        )

        // Verify GLB binary magic header
        val glb = ModelExporter.export(
            project, mesh, sprite,
            ExportOptions(format = ExportFormat.GLB, targetEngine = TargetEngine.UNITY)
        )
        val magic = ByteBuffer.wrap(glb.bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(0x46546C67, magic)

        // Verify embedded glTF 2.0 JSON
        val gltf = ModelExporter.export(
            project, mesh, sprite,
            ExportOptions(format = ExportFormat.GLTF, targetEngine = TargetEngine.UNREAL)
        )
        val gltfStr = gltf.bytes.toString(Charsets.UTF_8)
        assertTrue(gltfStr.contains("\"version\": \"2.0\""))
        assertTrue(gltfStr.contains("\"physicsMeshGroups\""))

        // Verify ASCII FBX 7.4
        val fbx = ModelExporter.export(
            project, mesh, sprite,
            ExportOptions(format = ExportFormat.FBX, targetEngine = TargetEngine.UNREAL)
        )
        val fbxStr = fbx.bytes.toString(Charsets.UTF_8)
        assertTrue(fbxStr.contains("FBXVersion: 7400"))
        assertTrue(fbxStr.contains("LimbNode"))
    }
}
