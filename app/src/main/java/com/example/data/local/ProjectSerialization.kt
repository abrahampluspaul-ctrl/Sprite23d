package com.example.data.local

import com.example.domain.model.AnimationSettings
import com.example.domain.model.BoneConstraint
import com.example.domain.model.BoneNode
import com.example.domain.model.JointLandmark
import com.example.domain.model.LocomotionMode
import com.example.domain.model.MaterialConfig
import com.example.domain.model.MeshGroupPhysics
import com.example.domain.model.MeshGroupType
import com.example.domain.model.PhysicsType
import com.example.domain.model.ProceduralTextureType
import com.example.domain.model.ShaderMode
import com.example.domain.model.SpriteAnalysisResult
import org.json.JSONArray
import org.json.JSONObject

object ProjectSerialization {

    fun analysisToJson(a: SpriteAnalysisResult): String {
        val joints = JSONArray()
        a.joints.forEach { j ->
            joints.put(JSONObject().apply {
                put("id", j.id)
                put("normX", j.normX)
                put("normY", j.normY)
                put("zOffset", j.zOffset)
            })
        }
        return JSONObject().apply {
            put("characterTitle", a.characterTitle)
            put("archetype", a.archetype)
            put("summary", a.summary)
            put("depthProfile", a.depthProfile)
            put("thicknessScale", a.thicknessScale)
            put("hasCapeOrCloth", a.hasCapeOrCloth)
            put("hasFlowingHair", a.hasFlowingHair)
            put("hasSoftBodyParts", a.hasSoftBodyParts)
            put("suggestedRoughness", a.suggestedRoughness)
            put("suggestedMetallic", a.suggestedMetallic)
            put("suggestedShader", a.suggestedShader.name)
            put("suggestedProcedural", a.suggestedProcedural.name)
            put("joints", joints)
        }.toString()
    }

    fun jsonToAnalysis(raw: String): SpriteAnalysisResult {
        val obj = JSONObject(raw)
        val arr = obj.optJSONArray("joints") ?: JSONArray()
        val joints = buildList {
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                add(
                    JointLandmark(
                        id = j.getString("id"),
                        normX = j.optDouble("normX", 0.5).toFloat(),
                        normY = j.optDouble("normY", 0.5).toFloat(),
                        zOffset = j.optDouble("zOffset", 0.0).toFloat()
                    )
                )
            }
        }
        return SpriteAnalysisResult(
            characterTitle = obj.optString("characterTitle", "3D Character"),
            archetype = obj.optString("archetype", "humanoid"),
            summary = obj.optString("summary", ""),
            depthProfile = obj.optString("depthProfile", "balanced"),
            thicknessScale = obj.optDouble("thicknessScale", 0.32).toFloat(),
            hasCapeOrCloth = obj.optBoolean("hasCapeOrCloth", true),
            hasFlowingHair = obj.optBoolean("hasFlowingHair", true),
            hasSoftBodyParts = obj.optBoolean("hasSoftBodyParts", false),
            suggestedRoughness = obj.optDouble("suggestedRoughness", 0.45).toFloat(),
            suggestedMetallic = obj.optDouble("suggestedMetallic", 0.2).toFloat(),
            suggestedShader = runCatching { ShaderMode.valueOf(obj.optString("suggestedShader")) }
                .getOrDefault(ShaderMode.PBR_STANDARD),
            suggestedProcedural = runCatching {
                ProceduralTextureType.valueOf(obj.optString("suggestedProcedural"))
            }.getOrDefault(ProceduralTextureType.NONE),
            joints = joints
        )
    }

    fun bonesToJson(bones: List<BoneNode>): String {
        val arr = JSONArray()
        bones.forEach { b ->
            val c = b.constraint
            arr.put(JSONObject().apply {
                put("id", b.id)
                put("name", b.name)
                put("unityName", b.unityName)
                put("ue5Name", b.ue5Name)
                put("parentId", b.parentId ?: JSONObject.NULL)
                put("restX", b.restX)
                put("restY", b.restY)
                put("restZ", b.restZ)
                put("groupTag", b.groupTag.name)
                put("constraint", JSONObject().apply {
                    put("enabled", c.enabled)
                    put("rotMinX", c.rotMinX)
                    put("rotMaxX", c.rotMaxX)
                    put("rotMinY", c.rotMinY)
                    put("rotMaxY", c.rotMaxY)
                    put("rotMinZ", c.rotMinZ)
                    put("rotMaxZ", c.rotMaxZ)
                    put("scaleMinX", c.scaleMinX)
                    put("scaleMaxX", c.scaleMaxX)
                    put("scaleMinY", c.scaleMinY)
                    put("scaleMaxY", c.scaleMaxY)
                    put("scaleMinZ", c.scaleMinZ)
                    put("scaleMaxZ", c.scaleMaxZ)
                    put("posMinX", c.posMinX)
                    put("posMaxX", c.posMaxX)
                    put("posMinY", c.posMinY)
                    put("posMaxY", c.posMaxY)
                    put("posMinZ", c.posMinZ)
                    put("posMaxZ", c.posMaxZ)
                    put("lockPosX", c.lockPosX)
                    put("lockPosY", c.lockPosY)
                    put("lockPosZ", c.lockPosZ)
                })
            })
        }
        return arr.toString()
    }

    fun jsonToBones(raw: String): List<BoneNode> {
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val cObj = obj.optJSONObject("constraint") ?: JSONObject()
                val parentRaw = if (obj.isNull("parentId")) null else obj.optString("parentId")
                add(
                    BoneNode(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        unityName = obj.optString("unityName", obj.getString("name")),
                        ue5Name = obj.optString("ue5Name", obj.getString("name")),
                        parentId = parentRaw,
                        restX = obj.optDouble("restX", 0.0).toFloat(),
                        restY = obj.optDouble("restY", 0.0).toFloat(),
                        restZ = obj.optDouble("restZ", 0.0).toFloat(),
                        groupTag = runCatching { MeshGroupType.valueOf(obj.optString("groupTag")) }
                            .getOrDefault(MeshGroupType.TORSO),
                        constraint = BoneConstraint(
                            enabled = cObj.optBoolean("enabled", true),
                            rotMinX = cObj.optDouble("rotMinX", -45.0).toFloat(),
                            rotMaxX = cObj.optDouble("rotMaxX", 45.0).toFloat(),
                            rotMinY = cObj.optDouble("rotMinY", -45.0).toFloat(),
                            rotMaxY = cObj.optDouble("rotMaxY", 45.0).toFloat(),
                            rotMinZ = cObj.optDouble("rotMinZ", -35.0).toFloat(),
                            rotMaxZ = cObj.optDouble("rotMaxZ", 35.0).toFloat(),
                            scaleMinX = cObj.optDouble("scaleMinX", 0.85).toFloat(),
                            scaleMaxX = cObj.optDouble("scaleMaxX", 1.15).toFloat(),
                            scaleMinY = cObj.optDouble("scaleMinY", 0.85).toFloat(),
                            scaleMaxY = cObj.optDouble("scaleMaxY", 1.15).toFloat(),
                            scaleMinZ = cObj.optDouble("scaleMinZ", 0.85).toFloat(),
                            scaleMaxZ = cObj.optDouble("scaleMaxZ", 1.15).toFloat(),
                            posMinX = cObj.optDouble("posMinX", -0.15).toFloat(),
                            posMaxX = cObj.optDouble("posMaxX", 0.15).toFloat(),
                            posMinY = cObj.optDouble("posMinY", -0.15).toFloat(),
                            posMaxY = cObj.optDouble("posMaxY", 0.15).toFloat(),
                            posMinZ = cObj.optDouble("posMinZ", -0.15).toFloat(),
                            posMaxZ = cObj.optDouble("posMaxZ", 0.15).toFloat(),
                            lockPosX = cObj.optBoolean("lockPosX", false),
                            lockPosY = cObj.optBoolean("lockPosY", false),
                            lockPosZ = cObj.optBoolean("lockPosZ", false)
                        )
                    )
                )
            }
        }
    }

    fun materialToJson(m: MaterialConfig): String = JSONObject().apply {
        put("albedoTintHex", m.albedoTintHex)
        put("exposure", m.exposure)
        put("normalStrength", m.normalStrength)
        put("roughness", m.roughness)
        put("metallic", m.metallic)
        put("proceduralTexture", m.proceduralTexture.name)
        put("proceduralScale", m.proceduralScale)
        put("proceduralBlend", m.proceduralBlend)
        put("shaderMode", m.shaderMode.name)
        put("rimColorHex", m.rimColorHex)
        put("rimPower", m.rimPower)
        put("celBands", m.celBands)
        put("emissiveIntensity", m.emissiveIntensity)
        put("uvTiling", m.uvTiling)
        put("uvOffsetX", m.uvOffsetX)
        put("uvOffsetY", m.uvOffsetY)
        put("anisotropy", m.anisotropy)
        put("customShaderGain", m.customShaderGain)
    }.toString()

    fun jsonToMaterial(raw: String): MaterialConfig {
        val o = JSONObject(raw)
        return MaterialConfig(
            albedoTintHex = o.optLong("albedoTintHex", 0xFFFFFFFFL),
            exposure = o.optDouble("exposure", 1.05).toFloat(),
            normalStrength = o.optDouble("normalStrength", 0.85).toFloat(),
            roughness = o.optDouble("roughness", 0.42).toFloat(),
            metallic = o.optDouble("metallic", 0.18).toFloat(),
            proceduralTexture = runCatching {
                ProceduralTextureType.valueOf(o.optString("proceduralTexture"))
            }.getOrDefault(ProceduralTextureType.NONE),
            proceduralScale = o.optDouble("proceduralScale", 6.0).toFloat(),
            proceduralBlend = o.optDouble("proceduralBlend", 0.35).toFloat(),
            shaderMode = runCatching { ShaderMode.valueOf(o.optString("shaderMode")) }
                .getOrDefault(ShaderMode.PBR_STANDARD),
            rimColorHex = o.optLong("rimColorHex", 0xFF38BDF8L),
            rimPower = o.optDouble("rimPower", 2.4).toFloat(),
            celBands = o.optInt("celBands", 4),
            emissiveIntensity = o.optDouble("emissiveIntensity", 0.12).toFloat(),
            uvTiling = o.optDouble("uvTiling", 1.0).toFloat(),
            uvOffsetX = o.optDouble("uvOffsetX", 0.0).toFloat(),
            uvOffsetY = o.optDouble("uvOffsetY", 0.0).toFloat(),
            anisotropy = o.optDouble("anisotropy", 0.25).toFloat(),
            customShaderGain = o.optDouble("customShaderGain", 1.0).toFloat()
        )
    }

    fun physicsToJson(list: List<MeshGroupPhysics>): String {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().apply {
                put("groupType", p.groupType.name)
                put("label", p.label)
                put("enabled", p.enabled)
                put("physicsType", p.physicsType.name)
                put("stiffness", p.stiffness)
                put("damping", p.damping)
                put("gravityScale", p.gravityScale)
                put("windStrength", p.windStrength)
                put("mass", p.mass)
                put("collisionRadius", p.collisionRadius)
            })
        }
        return arr.toString()
    }

    fun jsonToPhysics(raw: String): List<MeshGroupPhysics> {
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    MeshGroupPhysics(
                        groupType = runCatching { MeshGroupType.valueOf(o.optString("groupType")) }
                            .getOrDefault(MeshGroupType.CLOTH_CAPE),
                        label = o.optString("label", "Cloth Group"),
                        enabled = o.optBoolean("enabled", true),
                        physicsType = runCatching { PhysicsType.valueOf(o.optString("physicsType")) }
                            .getOrDefault(PhysicsType.CLOTH),
                        stiffness = o.optDouble("stiffness", 0.6).toFloat(),
                        damping = o.optDouble("damping", 0.85).toFloat(),
                        gravityScale = o.optDouble("gravityScale", 1.0).toFloat(),
                        windStrength = o.optDouble("windStrength", 0.35).toFloat(),
                        mass = o.optDouble("mass", 1.0).toFloat(),
                        collisionRadius = o.optDouble("collisionRadius", 0.18).toFloat()
                    )
                )
            }
        }
    }

    fun animationToJson(a: AnimationSettings): String = JSONObject().apply {
        put("animationLevel", a.animationLevel)
        put("playbackSpeed", a.playbackSpeed)
        put("locomotionMode", a.locomotionMode.name)
        put("showSkeleton", a.showSkeleton)
        put("showConstraintGizmos", a.showConstraintGizmos)
        put("showPhysicsVectors", a.showPhysicsVectors)
    }.toString()

    fun jsonToAnimation(raw: String): AnimationSettings {
        val o = JSONObject(raw)
        return AnimationSettings(
            animationLevel = o.optDouble("animationLevel", 1.0).toFloat(),
            playbackSpeed = o.optDouble("playbackSpeed", 1.0).toFloat(),
            locomotionMode = runCatching { LocomotionMode.valueOf(o.optString("locomotionMode")) }
                .getOrDefault(LocomotionMode.AUTO),
            showSkeleton = o.optBoolean("showSkeleton", true),
            showConstraintGizmos = o.optBoolean("showConstraintGizmos", true),
            showPhysicsVectors = o.optBoolean("showPhysicsVectors", false)
        )
    }
}
