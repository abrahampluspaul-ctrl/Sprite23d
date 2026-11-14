package com.example.domain.presets

import com.example.domain.model.BoneConstraint
import com.example.domain.model.BoneNode
import com.example.domain.model.JointLandmark
import com.example.domain.model.MeshGroupPhysics
import com.example.domain.model.MeshGroupType
import com.example.domain.model.PhysicsType
import com.example.domain.model.RigPreset
import com.example.domain.model.SpriteAnalysisResult

object RigPresets {

    fun buildBonesForPreset(
        preset: RigPreset,
        landmarks: List<JointLandmark> = emptyList()
    ): List<BoneNode> {
        val landmarkMap = landmarks.associateBy { it.id.lowercase() }

        fun lm(key: String, defX: Float, defY: Float, defZ: Float = 0f): Triple<Float, Float, Float> {
            val found = landmarkMap[key.lowercase()]
            return if (found != null) {
                val wx = (found.normX - 0.5f) * 1.15f
                val wy = (0.5f - found.normY) * 1.45f
                Triple(wx, wy, found.zOffset.coerceIn(-0.25f, 0.25f))
            } else {
                Triple(defX, defY, defZ)
            }
        }

        if (preset == RigPreset.QUADRUPED) {
            val pelvis = lm("hips", -0.18f, -0.05f)
            val spine = lm("spine", 0.0f, 0.0f)
            val chest = lm("chest", 0.18f, 0.04f)
            val neck = lm("neck", 0.30f, 0.16f)
            val head = lm("head", 0.40f, 0.28f, 0.03f)
            val frontL = lm("arm_l", 0.18f, -0.22f, 0.09f)
            val frontR = lm("arm_r", 0.18f, -0.22f, -0.09f)
            val pawFL = lm("hand_l", 0.22f, -0.52f, 0.09f)
            val pawFR = lm("hand_r", 0.22f, -0.52f, -0.09f)
            val hindL = lm("leg_l", -0.20f, -0.22f, 0.09f)
            val hindR = lm("leg_r", -0.20f, -0.22f, -0.09f)
            val pawHL = lm("foot_l", -0.24f, -0.52f, 0.09f)
            val pawHR = lm("foot_r", -0.24f, -0.52f, -0.09f)
            val tailBase = lm("cape", -0.34f, 0.04f, -0.04f)
            val tailTip = lm("hair", -0.52f, 0.14f, -0.08f)

            return listOf(
                bone("hips", "Pelvis", "Pelvis", "pelvis", null, pelvis, MeshGroupType.TORSO,
                    BoneConstraint(rotMinX = -25f, rotMaxX = 25f, rotMinY = -35f, rotMaxY = 35f, rotMinZ = -20f, rotMaxZ = 20f)),
                bone("spine", "Spine", "Spine", "spine_01", "hips", spine, MeshGroupType.TORSO,
                    BoneConstraint(rotMinX = -20f, rotMaxX = 25f, rotMinY = -30f, rotMaxY = 30f, rotMinZ = -20f, rotMaxZ = 20f)),
                bone("chest", "Chest", "Chest", "spine_03", "spine", chest, MeshGroupType.TORSO,
                    BoneConstraint(rotMinX = -20f, rotMaxX = 25f, rotMinY = -25f, rotMaxY = 25f, rotMinZ = -18f, rotMaxZ = 18f)),
                bone("neck", "Neck", "Neck", "neck_01", "chest", neck, MeshGroupType.HEAD,
                    BoneConstraint(rotMinX = -35f, rotMaxX = 35f, rotMinY = -45f, rotMaxY = 45f, rotMinZ = -25f, rotMaxZ = 25f)),
                bone("head", "Head", "Head", "head", "neck", head, MeshGroupType.HEAD,
                    BoneConstraint(rotMinX = -35f, rotMaxX = 35f, rotMinY = -55f, rotMaxY = 55f, rotMinZ = -30f, rotMaxZ = 30f)),
                bone("front_l", "FrontShoulder_L", "LeftUpperArm", "upperarm_l", "chest", frontL, MeshGroupType.LIMBS,
                    BoneConstraint(rotMinX = -70f, rotMaxX = 65f, rotMinY = -25f, rotMaxY = 25f, rotMinZ = -20f, rotMaxZ = 20f)),
                bone("paw_fl", "FrontPaw_L", "LeftHand", "hand_l", "front_l", pawFL, MeshGroupType.LIMBS,
                    BoneConstraint(rotMinX = -85f, rotMaxX = 25f, rotMinY = -15f, rotMaxY = 15f, rotMinZ = -15f, rotMaxZ = 15f)),
                bone("front_r", "FrontShoulder_R", "RightUpperArm", "upperarm_r", "chest", frontR, MeshGroupType.LIMBS,
                    BoneConstraint(rotMinX = -70f, rotMaxX = 65f, rotMinY = -25f, rotMaxY = 25f, rotMinZ = -20f, rotMaxZ = 20f)),
                bone("paw_fr", "FrontPaw_R", "RightHand", "hand_r", "front_r", pawFR, MeshGroupType.LIMBS,
                    BoneConstraint(rotMinX = -85f, rotMaxX = 25f, rotMinY = -15f, rotMaxY = 15f, rotMinZ = -15f, rotMaxZ = 15f)),
                bone("hind_l", "HindHip_L", "LeftUpperLeg", "thigh_l", "hips", hindL, MeshGroupType.LIMBS,
                    BoneConstraint(rotMinX = -75f, rotMaxX = 55f, rotMinY = -20f, rotMaxY = 20f, rotMinZ = -20f, rotMaxZ = 20f)),
                bone("paw_hl", "HindPaw_L", "LeftFoot", "foot_l", "hind_l", pawHL, MeshGroupType.LIMBS,
                    BoneConstraint(rotMinX = -20f, rotMaxX = 95f, rotMinY = -15f, rotMaxY = 15f, rotMinZ = -15f, rotMaxZ = 15f)),
                bone("hind_r", "HindHip_R", "RightUpperLeg", "thigh_r", "hips", hindR, MeshGroupType.LIMBS,
                    BoneConstraint(rotMinX = -75f, rotMaxX = 55f, rotMinY = -20f, rotMaxY = 20f, rotMinZ = -20f, rotMaxZ = 20f)),
                bone("paw_hr", "HindPaw_R", "RightFoot", "foot_r", "hind_r", pawHR, MeshGroupType.LIMBS,
                    BoneConstraint(rotMinX = -20f, rotMaxX = 95f, rotMinY = -15f, rotMaxY = 15f, rotMinZ = -15f, rotMaxZ = 15f)),
                bone("tail_base", "Tail_01", "TailBase", "tail_01", "hips", tailBase, MeshGroupType.SOFT_ACCESSORY,
                    BoneConstraint(rotMinX = -45f, rotMaxX = 45f, rotMinY = -65f, rotMaxY = 65f, rotMinZ = -45f, rotMaxZ = 45f)),
                bone("tail_tip", "Tail_02", "TailTip", "tail_02", "tail_base", tailTip, MeshGroupType.SOFT_ACCESSORY,
                    BoneConstraint(rotMinX = -60f, rotMaxX = 60f, rotMinY = -75f, rotMaxY = 75f, rotMinZ = -55f, rotMaxZ = 55f))
            )
        }

        val unlocked = preset == RigPreset.CUSTOM
        val rotLimitLarge = if (unlocked) 160f else 85f
        val rotLimitSpine = if (unlocked) 120f else 35f

        val hips = lm("hips", 0.0f, -0.04f)
        val spine = lm("spine", 0.0f, 0.12f)
        val chest = lm("chest", 0.0f, 0.28f)
        val neck = lm("neck", 0.0f, 0.42f)
        val head = lm("head", 0.0f, 0.54f)

        val upperArmL = lm("arm_l", -0.22f, 0.28f)
        val forearmL = lm("forearm_l", -0.32f, 0.08f)
        val handL = lm("hand_l", -0.38f, -0.08f)

        val upperArmR = lm("arm_r", 0.22f, 0.28f)
        val forearmR = lm("forearm_r", 0.32f, 0.08f)
        val handR = lm("hand_r", 0.38f, -0.08f)

        val thighL = lm("leg_l", -0.14f, -0.10f)
        val shinL = lm("shin_l", -0.15f, -0.36f)
        val footL = lm("foot_l", -0.16f, -0.60f, 0.04f)

        val thighR = lm("leg_r", 0.14f, -0.10f)
        val shinR = lm("shin_r", 0.15f, -0.36f)
        val footR = lm("foot_r", 0.16f, -0.60f, 0.04f)

        val capeRoot = lm("cape", 0.0f, 0.20f, -0.10f)
        val hairPlume = lm("hair", 0.0f, 0.62f, -0.06f)

        fun label(universal: String, unity: String, ue5: String): String = when (preset) {
            RigPreset.UNITY_MECANIM -> unity
            RigPreset.UE5_MANNEQUIN -> ue5
            else -> universal
        }

        return listOf(
            bone("hips", label("Hips", "Hips", "pelvis"), "Hips", "pelvis", null, hips, MeshGroupType.TORSO,
                BoneConstraint(rotMinX = -rotLimitSpine, rotMaxX = rotLimitSpine, rotMinY = -60f, rotMaxY = 60f, rotMinZ = -25f, rotMaxZ = 25f)),
            bone("spine", label("Spine", "Spine", "spine_01"), "Spine", "spine_01", "hips", spine, MeshGroupType.TORSO,
                BoneConstraint(rotMinX = -rotLimitSpine, rotMaxX = rotLimitSpine, rotMinY = -40f, rotMaxY = 40f, rotMinZ = -30f, rotMaxZ = 30f)),
            bone("chest", label("Chest", "Chest", "spine_03"), "Chest", "spine_03", "spine", chest, MeshGroupType.TORSO,
                BoneConstraint(rotMinX = -rotLimitSpine, rotMaxX = rotLimitSpine, rotMinY = -35f, rotMaxY = 35f, rotMinZ = -25f, rotMaxZ = 25f)),
            bone("neck", label("Neck", "Neck", "neck_01"), "Neck", "neck_01", "chest", neck, MeshGroupType.HEAD,
                BoneConstraint(rotMinX = -35f, rotMaxX = 35f, rotMinY = -50f, rotMaxY = 50f, rotMinZ = -30f, rotMaxZ = 30f)),
            bone("head", label("Head", "Head", "head"), "Head", "head", "neck", head, MeshGroupType.HEAD,
                BoneConstraint(rotMinX = -40f, rotMaxX = 40f, rotMinY = -65f, rotMaxY = 65f, rotMinZ = -35f, rotMaxZ = 35f)),
            bone("upper_arm_l", label("UpperArm_L", "LeftUpperArm", "upperarm_l"), "LeftUpperArm", "upperarm_l", "chest", upperArmL, MeshGroupType.LIMBS,
                BoneConstraint(rotMinX = -rotLimitLarge, rotMaxX = rotLimitLarge, rotMinY = -60f, rotMaxY = 75f, rotMinZ = -80f, rotMaxZ = 80f)),
            bone("forearm_l", label("Forearm_L", "LeftLowerArm", "lowerarm_l"), "LeftLowerArm", "lowerarm_l", "upper_arm_l", forearmL, MeshGroupType.LIMBS,
                BoneConstraint(rotMinX = -135f, rotMaxX = 10f, rotMinY = -20f, rotMaxY = 20f, rotMinZ = -15f, rotMaxZ = 15f)),
            bone("hand_l", label("Hand_L", "LeftHand", "hand_l"), "LeftHand", "hand_l", "forearm_l", handL, MeshGroupType.LIMBS,
                BoneConstraint(rotMinX = -65f, rotMaxX = 65f, rotMinY = -35f, rotMaxY = 35f, rotMinZ = -45f, rotMaxZ = 45f)),
            bone("upper_arm_r", label("UpperArm_R", "RightUpperArm", "upperarm_r"), "RightUpperArm", "upperarm_r", "chest", upperArmR, MeshGroupType.LIMBS,
                BoneConstraint(rotMinX = -rotLimitLarge, rotMaxX = rotLimitLarge, rotMinY = -75f, rotMaxY = 60f, rotMinZ = -80f, rotMaxZ = 80f)),
            bone("forearm_r", label("Forearm_R", "RightLowerArm", "lowerarm_r"), "RightLowerArm", "lowerarm_r", "upper_arm_r", forearmR, MeshGroupType.LIMBS,
                BoneConstraint(rotMinX = -135f, rotMaxX = 10f, rotMinY = -20f, rotMaxY = 20f, rotMinZ = -15f, rotMaxZ = 15f)),
            bone("hand_r", label("Hand_R", "RightHand", "hand_r"), "RightHand", "hand_r", "forearm_r", handR, MeshGroupType.LIMBS,
                BoneConstraint(rotMinX = -65f, rotMaxX = 65f, rotMinY = -35f, rotMaxY = 35f, rotMinZ = -45f, rotMaxZ = 45f)),
            bone("thigh_l", label("Thigh_L", "LeftUpperLeg", "thigh_l"), "LeftUpperLeg", "thigh_l", "hips", thighL, MeshGroupType.LIMBS,
                BoneConstraint(rotMinX = -95f, rotMaxX = 65f, rotMinY = -35f, rotMaxY = 35f, rotMinZ = -30f, rotMaxZ = 45f)),
            bone("shin_l", label("Shin_L", "LeftLowerLeg", "calf_l"), "LeftLowerLeg", "calf_l", "thigh_l", shinL, MeshGroupType.LIMBS,
                BoneConstraint(rotMinX = -5f, rotMaxX = 135f, rotMinY = -10f, rotMaxY = 10f, rotMinZ = -10f, rotMaxZ = 10f)),
            bone("foot_l", label("Foot_L", "LeftFoot", "foot_l"), "LeftFoot", "foot_l", "shin_l", footL, MeshGroupType.LIMBS,
                BoneConstraint(rotMinX = -45f, rotMaxX = 50f, rotMinY = -25f, rotMaxY = 25f, rotMinZ = -20f, rotMaxZ = 20f)),
            bone("thigh_r", label("Thigh_R", "RightUpperLeg", "thigh_r"), "RightUpperLeg", "thigh_r", "hips", thighR, MeshGroupType.LIMBS,
                BoneConstraint(rotMinX = -95f, rotMaxX = 65f, rotMinY = -35f, rotMaxY = 35f, rotMinZ = -45f, rotMaxZ = 30f)),
            bone("shin_r", label("Shin_R", "RightLowerLeg", "calf_r"), "RightLowerLeg", "calf_r", "thigh_r", shinR, MeshGroupType.LIMBS,
                BoneConstraint(rotMinX = -5f, rotMaxX = 135f, rotMinY = -10f, rotMaxY = 10f, rotMinZ = -10f, rotMaxZ = 10f)),
            bone("foot_r", label("Foot_R", "RightFoot", "foot_r"), "RightFoot", "foot_r", "shin_r", footR, MeshGroupType.LIMBS,
                BoneConstraint(rotMinX = -45f, rotMaxX = 50f, rotMinY = -25f, rotMaxY = 25f, rotMinZ = -20f, rotMaxZ = 20f)),
            bone("cape_root", label("Cape_Dyn", "Cape_Root", "cape_01"), "Cape_Root", "cape_01", "chest", capeRoot, MeshGroupType.CLOTH_CAPE,
                BoneConstraint(rotMinX = -65f, rotMaxX = 65f, rotMinY = -45f, rotMaxY = 45f, rotMinZ = -45f, rotMaxZ = 45f)),
            bone("hair_dyn", label("Hair_Dyn", "Hair_Root", "hair_01"), "Hair_Root", "hair_01", "head", hairPlume, MeshGroupType.HAIR_PLUME,
                BoneConstraint(rotMinX = -55f, rotMaxX = 55f, rotMinY = -55f, rotMaxY = 55f, rotMinZ = -45f, rotMaxZ = 45f))
        )
    }

    fun defaultPhysicsGroups(analysis: SpriteAnalysisResult? = null): List<MeshGroupPhysics> {
        return listOf(
            MeshGroupPhysics(
                groupType = MeshGroupType.CLOTH_CAPE,
                label = "Cape / Cloak Cloth",
                enabled = analysis?.hasCapeOrCloth ?: true,
                physicsType = PhysicsType.CLOTH,
                stiffness = 0.52f,
                damping = 0.88f,
                gravityScale = 1.05f,
                windStrength = 0.48f,
                mass = 0.85f,
                collisionRadius = 0.22f
            ),
            MeshGroupPhysics(
                groupType = MeshGroupType.HAIR_PLUME,
                label = "Hair / Helmet Plume",
                enabled = analysis?.hasFlowingHair ?: true,
                physicsType = PhysicsType.HAIR,
                stiffness = 0.68f,
                damping = 0.84f,
                gravityScale = 0.90f,
                windStrength = 0.40f,
                mass = 0.55f,
                collisionRadius = 0.16f
            ),
            MeshGroupPhysics(
                groupType = MeshGroupType.SOFT_ACCESSORY,
                label = "Soft Body / Tail / Pouches",
                enabled = analysis?.hasSoftBodyParts ?: true,
                physicsType = PhysicsType.SOFT_BODY,
                stiffness = 0.78f,
                damping = 0.82f,
                gravityScale = 0.75f,
                windStrength = 0.22f,
                mass = 1.15f,
                collisionRadius = 0.18f
            ),
            MeshGroupPhysics(
                groupType = MeshGroupType.TORSO,
                label = "Torso Volume Squash",
                enabled = false,
                physicsType = PhysicsType.SOFT_BODY,
                stiffness = 0.90f,
                damping = 0.90f,
                gravityScale = 0.35f,
                windStrength = 0.05f,
                mass = 1.60f,
                collisionRadius = 0.24f
            )
        )
    }

    private fun bone(
        id: String,
        name: String,
        unityName: String,
        ue5Name: String,
        parentId: String?,
        pos: Triple<Float, Float, Float>,
        groupTag: MeshGroupType,
        constraint: BoneConstraint
    ) = BoneNode(
        id = id,
        name = name,
        unityName = unityName,
        ue5Name = ue5Name,
        parentId = parentId,
        restX = pos.first,
        restY = pos.second,
        restZ = pos.third,
        groupTag = groupTag,
        constraint = constraint
    )
}
