package com.example.domain.model

enum class RigPreset(val displayName: String, val badge: String, val description: String) {
    UNIVERSAL_HUMANOID("Universal Humanoid", "Unity + UE5", "Balanced humanoid rig compatible with Unity Mecanim & Unreal Engine 5"),
    UNITY_MECANIM("Unity Mecanim", "Unity", "Unity Humanoid Avatar naming with muscle degree constraints"),
    UE5_MANNEQUIN("UE5 Mannequin", "Unreal 5", "Unreal Engine 5 SK_Mannequin hierarchy and pelvis root alignment"),
    QUADRUPED("Quadruped", "4-Legged", "Four-legged creature rig with spine, tail chain, and paw constraints"),
    CUSTOM("Custom Freeform", "Custom", "Unlocked multi-joint rig with custom per-bone limits")
}

enum class MeshDensity(val label: String, val gridCols: Int, val gridRows: Int) {
    LOW("Game-Ready Low", 20, 24),
    BALANCED("Balanced Mid", 28, 34),
    HIGH("High Detail", 36, 44)
}

enum class MeshGroupType(val id: Int, val displayName: String) {
    TORSO(0, "Torso & Core"),
    HEAD(1, "Head & Helm"),
    LIMBS(2, "Arms & Legs"),
    CLOTH_CAPE(3, "Cape & Cloth"),
    HAIR_PLUME(4, "Hair & Plume"),
    SOFT_ACCESSORY(5, "Soft Body / Tail")
}

data class BoneConstraint(
    val enabled: Boolean = true,
    val rotMinX: Float = -45f,
    val rotMaxX: Float = 45f,
    val rotMinY: Float = -45f,
    val rotMaxY: Float = 45f,
    val rotMinZ: Float = -35f,
    val rotMaxZ: Float = 35f,
    val scaleMinX: Float = 0.85f,
    val scaleMaxX: Float = 1.15f,
    val scaleMinY: Float = 0.85f,
    val scaleMaxY: Float = 1.15f,
    val scaleMinZ: Float = 0.85f,
    val scaleMaxZ: Float = 1.15f,
    val posMinX: Float = -0.15f,
    val posMaxX: Float = 0.15f,
    val posMinY: Float = -0.15f,
    val posMaxY: Float = 0.15f,
    val posMinZ: Float = -0.15f,
    val posMaxZ: Float = 0.15f,
    val lockPosX: Boolean = false,
    val lockPosY: Boolean = false,
    val lockPosZ: Boolean = false
)

data class BoneNode(
    val id: String,
    val name: String,
    val unityName: String,
    val ue5Name: String,
    val parentId: String?,
    val restX: Float,
    val restY: Float,
    val restZ: Float,
    val groupTag: MeshGroupType,
    val constraint: BoneConstraint = BoneConstraint()
)

enum class PhysicsType(val displayName: String) {
    CLOTH("Cloth Sim"),
    HAIR("Hair Strand"),
    SOFT_BODY("Soft Body")
}

data class MeshGroupPhysics(
    val groupType: MeshGroupType,
    val label: String,
    val enabled: Boolean,
    val physicsType: PhysicsType,
    val stiffness: Float = 0.65f,
    val damping: Float = 0.86f,
    val gravityScale: Float = 1.0f,
    val windStrength: Float = 0.35f,
    val mass: Float = 1.0f,
    val collisionRadius: Float = 0.18f
)

enum class ProceduralTextureType(val displayName: String) {
    NONE("Sprite Crisp"),
    BRUSHED_METAL("Brushed Metal"),
    WOVEN_CLOTH("Woven Cloth"),
    SCALED_LEATHER("Scaled Leather"),
    HEX_TECH("Hex Tech Grid"),
    MARBLE_NOISE("Marble Grain")
}

enum class ShaderMode(val displayName: String, val badge: String) {
    PBR_STANDARD("Standard PBR", "PBR"),
    TOON_CEL("Toon Cel-Shaded", "TOON"),
    HOLOGRAM_RIM("Hologram Rim", "HOLO"),
    IRIDESCENT("Iridescent Spec", "IRID"),
    CUSTOM_UNIFORM_LAB("Custom Shader Lab", "GLSL")
}

data class MaterialConfig(
    val albedoTintHex: Long = 0xFFFFFFFFL,
    val exposure: Float = 1.05f,
    val normalStrength: Float = 0.85f,
    val roughness: Float = 0.42f,
    val metallic: Float = 0.18f,
    val proceduralTexture: ProceduralTextureType = ProceduralTextureType.NONE,
    val proceduralScale: Float = 6.0f,
    val proceduralBlend: Float = 0.35f,
    val shaderMode: ShaderMode = ShaderMode.PBR_STANDARD,
    val rimColorHex: Long = 0xFF38BDF8L,
    val rimPower: Float = 2.4f,
    val celBands: Int = 4,
    val emissiveIntensity: Float = 0.12f,
    val uvTiling: Float = 1.0f,
    val uvOffsetX: Float = 0.0f,
    val uvOffsetY: Float = 0.0f,
    val anisotropy: Float = 0.25f,
    val customShaderGain: Float = 1.0f
)

enum class LocomotionMode(val displayName: String) {
    AUTO("Auto Stick"),
    IDLE("Idle"),
    WALK("Walk"),
    RUN("Run"),
    STRAFE("Strafe"),
    JUMP("Jump"),
    TURN("Turn 360")
}

data class AnimationSettings(
    val animationLevel: Float = 1.0f,
    val playbackSpeed: Float = 1.0f,
    val locomotionMode: LocomotionMode = LocomotionMode.AUTO,
    val showSkeleton: Boolean = true,
    val showConstraintGizmos: Boolean = true,
    val showPhysicsVectors: Boolean = false
)

enum class ExportFormat(val extension: String, val mimeType: String, val displayName: String) {
    GLB("glb", "model/gltf-binary", "Binary glTF 2.0 (.glb)"),
    GLTF("gltf", "model/gltf+json", "glTF 2.0 JSON (.gltf)"),
    FBX("fbx", "application/octet-stream", "ASCII FBX 7.4 (.fbx)")
}

enum class TargetEngine(val displayName: String) {
    UNITY("Unity 6 / LTS"),
    UNREAL("Unreal Engine 5"),
    UNIVERSAL("Universal / Godot")
}

data class ExportOptions(
    val format: ExportFormat = ExportFormat.GLB,
    val targetEngine: TargetEngine = TargetEngine.UNITY,
    val includeTextures: Boolean = true,
    val includeRigAndConstraints: Boolean = true,
    val includeAnimations: Boolean = true,
    val includePhysics: Boolean = true
)

data class JointLandmark(
    val id: String,
    val normX: Float,
    val normY: Float,
    val zOffset: Float
)

data class SpriteAnalysisResult(
    val characterTitle: String,
    val archetype: String,
    val summary: String,
    val depthProfile: String,
    val thicknessScale: Float,
    val hasCapeOrCloth: Boolean,
    val hasFlowingHair: Boolean,
    val hasSoftBodyParts: Boolean,
    val suggestedRoughness: Float,
    val suggestedMetallic: Float,
    val suggestedShader: ShaderMode,
    val suggestedProcedural: ProceduralTextureType,
    val joints: List<JointLandmark>
)

class VolumetricMesh(
    val basePositions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val jointIndices: IntArray,
    val jointWeights: FloatArray,
    val groupTags: IntArray,
    val indices: IntArray
) {
    val vertexCount: Int get() = basePositions.size / 3
    val triangleCount: Int get() = indices.size / 3
}

data class StudioProject(
    val id: String,
    val name: String,
    val spritePath: String,
    val rigPreset: RigPreset,
    val meshDensity: MeshDensity,
    val vertexCount: Int,
    val boneCount: Int,
    val analysis: SpriteAnalysisResult,
    val bones: List<BoneNode>,
    val materialConfig: MaterialConfig,
    val physicsGroups: List<MeshGroupPhysics>,
    val animationSettings: AnimationSettings,
    val updatedAt: Long
)
