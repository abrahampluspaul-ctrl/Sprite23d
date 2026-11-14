package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.domain.model.JointLandmark
import com.example.domain.model.ProceduralTextureType
import com.example.domain.model.RigPreset
import com.example.domain.model.ShaderMode
import com.example.domain.model.SpriteAnalysisResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "SpriteAnalysisRepo"

class SpriteAnalysisRepository {

    private val analysisSchema = Schema.obj(
        properties = mapOf(
            "characterTitle" to Schema.string(description = "Short 2-4 word name describing the sprite character"),
            "archetype" to Schema.enumeration(
                values = listOf("humanoid", "quadruped", "mecha", "creature"),
                description = "Primary anatomical archetype"
            ),
            "summary" to Schema.string(description = "1-2 sentence breakdown of silhouette proportions, gear, and dynamic appendages"),
            "depthProfile" to Schema.enumeration(
                values = listOf("slim", "balanced", "bulky"),
                description = "Volumetric front-to-back depth thickness profile"
            ),
            "thicknessScale" to Schema.double(description = "Recommended 3D inflation scale from 0.22 (slim) to 0.48 (bulky)"),
            "hasCapeOrCloth" to Schema.boolean(description = "True if sprite has a cape, cloak, robe, scarf, or banner"),
            "hasFlowingHair" to Schema.boolean(description = "True if sprite has hair, helmet plume, ears, or crest"),
            "hasSoftBodyParts" to Schema.boolean(description = "True if sprite has tail, pouches, satchel, or organic soft volumes"),
            "suggestedRoughness" to Schema.double(description = "Surface roughness between 0.15 and 0.90"),
            "suggestedMetallic" to Schema.double(description = "Surface metallic factor between 0.0 and 0.95"),
            "suggestedShader" to Schema.enumeration(
                values = listOf("PBR_STANDARD", "TOON_CEL", "HOLOGRAM_RIM", "IRIDESCENT"),
                description = "Best matching shader pipeline for the sprite art style"
            ),
            "suggestedProcedural" to Schema.enumeration(
                values = listOf("NONE", "BRUSHED_METAL", "WOVEN_CLOTH", "SCALED_LEATHER", "HEX_TECH", "MARBLE_NOISE"),
                description = "Recommended procedural micro-surface detail map"
            ),
            "joints" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "id" to Schema.string(description = "Joint key: head, neck, chest, spine, hips, arm_l, forearm_l, hand_l, arm_r, forearm_r, hand_r, leg_l, shin_l, foot_l, leg_r, shin_r, foot_r, cape, hair"),
                        "normX" to Schema.double(description = "Normalized horizontal coordinate in sprite image [0.0..1.0]"),
                        "normY" to Schema.double(description = "Normalized vertical coordinate in sprite image [0.0..1.0] where 0 is top"),
                        "zOffset" to Schema.double(description = "Depth offset [-0.20..0.20]")
                    )
                ),
                description = "Estimated anatomical joint coordinates on the 2D sprite"
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = analysisSchema
                temperature = 0.25f
            }
        )
    }

    suspend fun analyzeSprite(
        spriteBitmap: Bitmap,
        selectedPreset: RigPreset
    ): SpriteAnalysisResult {
        try {
            val promptText = """
                Analyze this 2D game sprite for volumetric 3D mesh conversion, automatic UV mapping, skeletal rigging, material surface setup, and secondary physics simulation.
                Target rig preset: ${selectedPreset.displayName}.
                Estimate normalized 2D joint positions (normX 0..1 left-to-right, normY 0..1 top-to-bottom) matching the character's visible body parts, depth thickness, PBR roughness/metallic response, and whether cloth/cape, hair/plume, or soft-body appendages are present.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        image(spriteBitmap)
                        text(promptText)
                    }
                )
            }

            val rawJson = response.text
                ?: throw IllegalStateException("Gemini returned an empty sprite analysis response.")

            return parseAnalysisJson(rawJson)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Sprite analysis timed out", e)
            throw IllegalStateException("Sprite analysis timed out after 60s. Please retry.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI sprite analysis failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Sprite analysis failed.", e)
        }
    }

    private fun parseAnalysisJson(rawJson: String): SpriteAnalysisResult {
        val obj = JSONObject(rawJson)
        val jointsArray = obj.optJSONArray("joints")
        val joints = buildList {
            if (jointsArray != null) {
                for (i in 0 until jointsArray.length()) {
                    val j = jointsArray.getJSONObject(i)
                    add(
                        JointLandmark(
                            id = j.optString("id", "hips"),
                            normX = j.optDouble("normX", 0.5).toFloat().coerceIn(0.05f, 0.95f),
                            normY = j.optDouble("normY", 0.5).toFloat().coerceIn(0.05f, 0.95f),
                            zOffset = j.optDouble("zOffset", 0.0).toFloat().coerceIn(-0.22f, 0.22f)
                        )
                    )
                }
            }
        }

        return SpriteAnalysisResult(
            characterTitle = obj.optString("characterTitle", "Rigged 3D Sprite"),
            archetype = obj.optString("archetype", "humanoid"),
            summary = obj.optString(
                "summary",
                "Volumetric character mesh segmented for omnidirectional locomotion and dynamic secondary physics."
            ),
            depthProfile = obj.optString("depthProfile", "balanced"),
            thicknessScale = obj.optDouble("thicknessScale", 0.34).toFloat().coerceIn(0.18f, 0.52f),
            hasCapeOrCloth = obj.optBoolean("hasCapeOrCloth", true),
            hasFlowingHair = obj.optBoolean("hasFlowingHair", true),
            hasSoftBodyParts = obj.optBoolean("hasSoftBodyParts", false),
            suggestedRoughness = obj.optDouble("suggestedRoughness", 0.42).toFloat().coerceIn(0.1f, 0.95f),
            suggestedMetallic = obj.optDouble("suggestedMetallic", 0.22).toFloat().coerceIn(0.0f, 0.95f),
            suggestedShader = runCatching {
                ShaderMode.valueOf(obj.optString("suggestedShader", "PBR_STANDARD"))
            }.getOrDefault(ShaderMode.PBR_STANDARD),
            suggestedProcedural = runCatching {
                ProceduralTextureType.valueOf(obj.optString("suggestedProcedural", "NONE"))
            }.getOrDefault(ProceduralTextureType.NONE),
            joints = joints
        )
    }
}
