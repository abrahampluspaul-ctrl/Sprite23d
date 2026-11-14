package com.example.engine.export

import android.graphics.Bitmap
import android.util.Base64
import com.example.domain.model.ExportFormat
import com.example.domain.model.ExportOptions
import com.example.domain.model.StudioProject
import com.example.domain.model.TargetEngine
import com.example.domain.model.VolumetricMesh
import com.example.engine.mesh.VolumetricMeshBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

data class ExportArtifact(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
    val summary: String
)

object ModelExporter {

    fun export(
        project: StudioProject,
        mesh: VolumetricMesh,
        spriteBitmap: Bitmap,
        options: ExportOptions
    ): ExportArtifact {
        val safeName = project.name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "sprite_model" }

        return when (options.format) {
            ExportFormat.GLB -> {
                val bytes = buildGlb(project, mesh, spriteBitmap, options)
                ExportArtifact(
                    fileName = "${safeName}.glb",
                    mimeType = options.format.mimeType,
                    bytes = bytes,
                    summary = "Binary glTF 2.0 (${bytes.size / 1024} KB) • ${mesh.vertexCount} verts • ${project.bones.size} bones"
                )
            }
            ExportFormat.GLTF -> {
                val bytes = buildGltfEmbedded(project, mesh, spriteBitmap, options)
                ExportArtifact(
                    fileName = "${safeName}.gltf",
                    mimeType = options.format.mimeType,
                    bytes = bytes,
                    summary = "Embedded glTF 2.0 JSON (${bytes.size / 1024} KB) • ${mesh.vertexCount} verts"
                )
            }
            ExportFormat.FBX -> {
                val bytes = buildAsciiFbx(project, mesh, options)
                ExportArtifact(
                    fileName = "${safeName}.fbx",
                    mimeType = options.format.mimeType,
                    bytes = bytes,
                    summary = "ASCII FBX 7.4 (${bytes.size / 1024} KB) • ${options.targetEngine.displayName}"
                )
            }
        }
    }

    private fun buildGlb(
        project: StudioProject,
        mesh: VolumetricMesh,
        spriteBitmap: Bitmap,
        options: ExportOptions
    ): ByteArray {
        val (jsonObj, binBytes) = buildGltfCore(project, mesh, spriteBitmap, options, embedDataUri = false)
        val jsonRaw = jsonObj.toString().toByteArray(Charsets.UTF_8)
        val jsonPad = (4 - (jsonRaw.size % 4)) % 4
        val jsonChunkLen = jsonRaw.size + jsonPad

        val binPad = (4 - (binBytes.size % 4)) % 4
        val binChunkLen = binBytes.size + binPad

        val totalLen = 12 + 8 + jsonChunkLen + 8 + binChunkLen
        val out = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN)

        // GLB Header
        out.putInt(0x46546C67) // "glTF"
        out.putInt(2)
        out.putInt(totalLen)

        // JSON Chunk
        out.putInt(jsonChunkLen)
        out.putInt(0x4E4F534A) // "JSON"
        out.put(jsonRaw)
        repeat(jsonPad) { out.put(0x20.toByte()) }

        // BIN Chunk
        out.putInt(binChunkLen)
        out.putInt(0x004E4942) // "BIN\0"
        out.put(binBytes)
        repeat(binPad) { out.put(0.toByte()) }

        return out.array()
    }

    private fun buildGltfEmbedded(
        project: StudioProject,
        mesh: VolumetricMesh,
        spriteBitmap: Bitmap,
        options: ExportOptions
    ): ByteArray {
        val (jsonObj, _) = buildGltfCore(project, mesh, spriteBitmap, options, embedDataUri = true)
        return jsonObj.toString(2).toByteArray(Charsets.UTF_8)
    }

    private fun buildGltfCore(
        project: StudioProject,
        mesh: VolumetricMesh,
        spriteBitmap: Bitmap,
        options: ExportOptions,
        embedDataUri: Boolean
    ): Pair<JSONObject, ByteArray> {
        val binStream = ByteArrayOutputStream()
        val bufferViews = JSONArray()
        val accessors = JSONArray()

        fun appendAligned(bytes: ByteArray): Pair<Int, Int> {
            while (binStream.size() % 4 != 0) binStream.write(0)
            val offset = binStream.size()
            binStream.write(bytes)
            return offset to bytes.size
        }

        fun addBufferView(bytes: ByteArray, target: Int? = null): Int {
            val (offset, len) = appendAligned(bytes)
            val bvIdx = bufferViews.length()
            bufferViews.put(JSONObject().apply {
                put("buffer", 0)
                put("byteOffset", offset)
                put("byteLength", len)
                if (target != null) put("target", target)
            })
            return bvIdx
        }

        fun addAccessor(
            bvIdx: Int,
            componentType: Int,
            count: Int,
            type: String,
            minArr: FloatArray? = null,
            maxArr: FloatArray? = null
        ): Int {
            val accIdx = accessors.length()
            accessors.put(JSONObject().apply {
                put("bufferView", bvIdx)
                put("componentType", componentType)
                put("count", count)
                put("type", type)
                if (minArr != null) put("min", JSONArray(minArr.map { it.toDouble() }))
                if (maxArr != null) put("max", JSONArray(maxArr.map { it.toDouble() }))
            })
            return accIdx
        }

        val vCount = mesh.vertexCount
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (i in 0 until vCount) {
            val x = mesh.basePositions[i * 3]
            val y = mesh.basePositions[i * 3 + 1]
            val z = mesh.basePositions[i * 3 + 2]
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
        }

        val posBv = addBufferView(floatsToLeBytes(mesh.basePositions), 34962)
        val posAcc = addAccessor(posBv, 5126, vCount, "VEC3", floatArrayOf(minX, minY, minZ), floatArrayOf(maxX, maxY, maxZ))

        val normBv = addBufferView(floatsToLeBytes(mesh.normals), 34962)
        val normAcc = addAccessor(normBv, 5126, vCount, "VEC3")

        val uvBv = addBufferView(floatsToLeBytes(mesh.uvs), 34962)
        val uvAcc = addAccessor(uvBv, 5126, vCount, "VEC2")

        val idxBv = addBufferView(intsToLeBytes(mesh.indices), 34963)
        val idxAcc = addAccessor(idxBv, 5125, mesh.indices.size, "SCALAR")

        val attributesObj = JSONObject().apply {
            put("POSITION", posAcc)
            put("NORMAL", normAcc)
            put("TEXCOORD_0", uvAcc)
        }

        if (options.includeRigAndConstraints && project.bones.isNotEmpty()) {
            val jointsU16 = ShortArray(mesh.jointIndices.size) { mesh.jointIndices[it].toShort() }
            val jBv = addBufferView(shortsToLeBytes(jointsU16), 34962)
            val jAcc = addAccessor(jBv, 5123, vCount, "VEC4")

            val wBv = addBufferView(floatsToLeBytes(mesh.jointWeights), 34962)
            val wAcc = addAccessor(wBv, 5126, vCount, "VEC4")

            attributesObj.put("JOINTS_0", jAcc)
            attributesObj.put("WEIGHTS_0", wAcc)
        }

        // Material & Textures
        val images = JSONArray()
        val textures = JSONArray()
        val materials = JSONArray()

        val mat = project.materialConfig
        val pbrObj = JSONObject().apply {
            put("metallicFactor", mat.metallic.toDouble())
            put("roughnessFactor", mat.roughness.toDouble())
        }

        if (options.includeTextures) {
            fun addPngTexture(bmp: Bitmap, name: String): Int {
                val pngOut = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 95, pngOut)
                val bv = addBufferView(pngOut.toByteArray())
                val imgIdx = images.length()
                images.put(JSONObject().apply {
                    put("name", name)
                    put("bufferView", bv)
                    put("mimeType", "image/png")
                })
                val texIdx = textures.length()
                textures.put(JSONObject().apply { put("source", imgIdx) })
                return texIdx
            }

            val albedoTexIdx = addPngTexture(spriteBitmap, "${project.name}_albedo")
            pbrObj.put("baseColorTexture", JSONObject().put("index", albedoTexIdx))

            val mrBmp = VolumetricMeshBuilder.generateMetallicRoughnessMap(spriteBitmap, mat)
            val mrTexIdx = addPngTexture(mrBmp, "${project.name}_metallic_roughness")
            mrBmp.recycle()
            pbrObj.put("metallicRoughnessTexture", JSONObject().put("index", mrTexIdx))

            val normBmp = VolumetricMeshBuilder.generateNormalMap(spriteBitmap, mat)
            val normTexIdx = addPngTexture(normBmp, "${project.name}_normal")
            normBmp.recycle()
            materials.put(JSONObject().apply {
                put("name", "${project.name}_Mat")
                put("pbrMetallicRoughness", pbrObj)
                put("normalTexture", JSONObject().apply {
                    put("index", normTexIdx)
                    put("scale", mat.normalStrength.toDouble())
                })
                put("extras", JSONObject().apply {
                    put("shaderMode", mat.shaderMode.name)
                    put("proceduralTexture", mat.proceduralTexture.name)
                    put("proceduralScale", mat.proceduralScale)
                    put("rimPower", mat.rimPower)
                    put("celBands", mat.celBands)
                    put("emissiveIntensity", mat.emissiveIntensity)
                    put("anisotropy", mat.anisotropy)
                })
            })
        } else {
            materials.put(JSONObject().apply {
                put("name", "${project.name}_Mat")
                put("pbrMetallicRoughness", pbrObj)
            })
        }

        // Nodes & Skeleton Skin
        val nodes = JSONArray()
        // Node 0: Mesh Node
        val meshNode = JSONObject().apply {
            put("name", project.name)
            put("mesh", 0)
            if (options.includeRigAndConstraints && project.bones.isNotEmpty()) {
                put("skin", 0)
            }
        }
        nodes.put(meshNode)

        val skins = JSONArray()
        val animations = JSONArray()
        val rootChildren = JSONArray().put(0)

        if (options.includeRigAndConstraints && project.bones.isNotEmpty()) {
            val boneToNodeIdx = HashMap<String, Int>()
            val jointNodeIndices = JSONArray()
            val bones = project.bones
            bones.forEachIndexed { i, b ->
                boneToNodeIdx[b.id] = 1 + i
                jointNodeIndices.put(1 + i)
            }

            val childrenMap = HashMap<String, MutableList<Int>>()
            bones.forEachIndexed { i, b ->
                b.parentId?.let { pid ->
                    childrenMap.getOrPut(pid) { mutableListOf() }.add(1 + i)
                }
            }

            val boneById = bones.associateBy { it.id }
            val ibmFloats = FloatArray(bones.size * 16)

            bones.forEachIndexed { i, b ->
                val parent = b.parentId?.let { boneById[it] }
                val lx = b.restX - (parent?.restX ?: 0f)
                val ly = b.restY - (parent?.restY ?: 0f)
                val lz = b.restZ - (parent?.restZ ?: 0f)

                val nodeObj = JSONObject().apply {
                    val exportName = when (options.targetEngine) {
                        TargetEngine.UNITY -> b.unityName
                        TargetEngine.UNREAL -> b.ue5Name
                        TargetEngine.UNIVERSAL -> b.name
                    }
                    put("name", exportName)
                    put("translation", JSONArray(listOf(lx.toDouble(), ly.toDouble(), lz.toDouble())))
                    childrenMap[b.id]?.let { kids ->
                        put("children", JSONArray(kids))
                    }
                    put("extras", JSONObject().apply {
                        put("constraintEnabled", b.constraint.enabled)
                        put("rotMinDeg", JSONArray(listOf(b.constraint.rotMinX, b.constraint.rotMinY, b.constraint.rotMinZ)))
                        put("rotMaxDeg", JSONArray(listOf(b.constraint.rotMaxX, b.constraint.rotMaxY, b.constraint.rotMaxZ)))
                        put("scaleMin", JSONArray(listOf(b.constraint.scaleMinX, b.constraint.scaleMinY, b.constraint.scaleMinZ)))
                        put("scaleMax", JSONArray(listOf(b.constraint.scaleMaxX, b.constraint.scaleMaxY, b.constraint.scaleMaxZ)))
                        put("posMin", JSONArray(listOf(b.constraint.posMinX, b.constraint.posMinY, b.constraint.posMinZ)))
                        put("posMax", JSONArray(listOf(b.constraint.posMaxX, b.constraint.posMaxY, b.constraint.posMaxZ)))
                    })
                }
                nodes.put(nodeObj)

                // Column-major 4x4 Inverse Bind Matrix translating by -rest
                val off = i * 16
                ibmFloats[off + 0] = 1f; ibmFloats[off + 5] = 1f; ibmFloats[off + 10] = 1f; ibmFloats[off + 15] = 1f
                ibmFloats[off + 12] = -b.restX
                ibmFloats[off + 13] = -b.restY
                ibmFloats[off + 14] = -b.restZ
            }

            // Root bone enters scene
            val rootBoneIdx = bones.indexOfFirst { it.parentId == null }.coerceAtLeast(0)
            val rootBoneNodeIdx = 1 + rootBoneIdx
            rootChildren.put(rootBoneNodeIdx)

            val ibmBv = addBufferView(floatsToLeBytes(ibmFloats))
            val ibmAcc = addAccessor(ibmBv, 5126, bones.size, "MAT4")

            skins.put(JSONObject().apply {
                put("name", "${project.rigPreset.name}_Skin")
                put("inverseBindMatrices", ibmAcc)
                put("skeleton", rootBoneNodeIdx)
                put("joints", jointNodeIndices)
            })

            if (options.includeAnimations) {
                // Export a procedural Locomotion_Run animation clip targeting hips & spine
                val times = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1.0f)
                val hipsTrans = FloatArray(times.size * 3)
                val rootBone = bones[rootBoneIdx]
                for (k in times.indices) {
                    val bob = kotlin.math.abs(sin(times[k] * Math.PI * 2.0)).toFloat() * 0.05f * project.animationSettings.animationLevel
                    hipsTrans[k * 3] = rootBone.restX
                    hipsTrans[k * 3 + 1] = rootBone.restY + bob
                    hipsTrans[k * 3 + 2] = rootBone.restZ
                }
                val tBv = addBufferView(floatsToLeBytes(times))
                val tAcc = addAccessor(tBv, 5126, times.size, "SCALAR", floatArrayOf(0f), floatArrayOf(1f))
                val trBv = addBufferView(floatsToLeBytes(hipsTrans))
                val trAcc = addAccessor(trBv, 5126, times.size, "VEC3")

                animations.put(JSONObject().apply {
                    put("name", "Omnidirectional_Locomotion")
                    put("samplers", JSONArray().put(JSONObject().apply {
                        put("input", tAcc)
                        put("interpolation", "LINEAR")
                        put("output", trAcc)
                    }))
                    put("channels", JSONArray().put(JSONObject().apply {
                        put("sampler", 0)
                        put("target", JSONObject().apply {
                            put("node", rootBoneNodeIdx)
                            put("path", "translation")
                        })
                    }))
                })
            }
        }

        val binBytes = binStream.toByteArray()
        val bufferObj = JSONObject().apply {
            put("byteLength", binBytes.size)
            if (embedDataUri) {
                put("uri", "data:application/octet-stream;base64," + Base64.encodeToString(binBytes, Base64.NO_WRAP))
            }
        }

        val rootExtras = JSONObject().apply {
            put("generator", "SpriteForge 3D")
            put("targetEngine", options.targetEngine.name)
            put("rigPreset", project.rigPreset.name)
            if (options.includePhysics) {
                val physArr = JSONArray()
                project.physicsGroups.forEach { pg ->
                    physArr.put(JSONObject().apply {
                        put("group", pg.groupType.name)
                        put("enabled", pg.enabled)
                        put("solver", pg.physicsType.name)
                        put("stiffness", pg.stiffness)
                        put("damping", pg.damping)
                        put("gravityScale", pg.gravityScale)
                        put("windStrength", pg.windStrength)
                        put("mass", pg.mass)
                        put("collisionRadius", pg.collisionRadius)
                    })
                }
                put("physicsMeshGroups", physArr)
            }
        }

        val gltf = JSONObject().apply {
            put("asset", JSONObject().apply {
                put("version", "2.0")
                put("generator", "SpriteForge 3D Studio Exporter")
            })
            put("scene", 0)
            put("scenes", JSONArray().put(JSONObject().put("nodes", rootChildren)))
            put("nodes", nodes)
            put("meshes", JSONArray().put(JSONObject().apply {
                put("name", "${project.name}_Mesh")
                put("primitives", JSONArray().put(JSONObject().apply {
                    put("attributes", attributesObj)
                    put("indices", idxAcc)
                    put("material", 0)
                }))
            }))
            if (skins.length() > 0) put("skins", skins)
            if (animations.length() > 0) put("animations", animations)
            put("materials", materials)
            if (textures.length() > 0) put("textures", textures)
            if (images.length() > 0) put("images", images)
            put("accessors", accessors)
            put("bufferViews", bufferViews)
            put("buffers", JSONArray().put(bufferObj))
            put("extras", rootExtras)
        }

        return gltf to binBytes
    }

    private fun buildAsciiFbx(
        project: StudioProject,
        mesh: VolumetricMesh,
        options: ExportOptions
    ): ByteArray {
        val sb = StringBuilder(65536)
        val upAxis = if (options.targetEngine == TargetEngine.UNREAL) 2 else 1

        sb.appendLine("; FBX 7.4.0 project file")
        sb.appendLine("; Generated by SpriteForge 3D for ${options.targetEngine.displayName}")
        sb.appendLine("FBXHeaderExtension:  {")
        sb.appendLine("    FBXHeaderVersion: 1003")
        sb.appendLine("    FBXVersion: 7400")
        sb.appendLine("    Creator: \"SpriteForge 3D Studio\"")
        sb.appendLine("}")
        sb.appendLine("GlobalSettings:  {")
        sb.appendLine("    Version: 1000")
        sb.appendLine("    Properties70:  {")
        sb.appendLine("        P: \"UpAxis\", \"int\", \"Integer\", \"\",$upAxis")
        sb.appendLine("        P: \"UpAxisSign\", \"int\", \"Integer\", \"\",1")
        sb.appendLine("        P: \"UnitScaleFactor\", \"double\", \"Number\", \"\",100")
        sb.appendLine("    }")
        sb.appendLine("}")

        sb.appendLine("Objects:  {")
        // Geometry
        val geomId = 100001L
        val modelId = 100002L
        val matId = 100003L

        sb.appendLine("    Geometry: $geomId, \"Geometry::${project.name}_Geo\", \"Mesh\" {")
        sb.append("        Vertices: *${mesh.basePositions.size} { a: ")
        sb.append(mesh.basePositions.joinToString(","))
        sb.appendLine(" }")

        // Convert triangle indices to FBX PolygonVertexIndex (negative last index - 1)
        val polyIdx = IntArray(mesh.indices.size)
        var t = 0
        while (t < mesh.indices.size) {
            polyIdx[t] = mesh.indices[t]
            polyIdx[t + 1] = mesh.indices[t + 1]
            polyIdx[t + 2] = -mesh.indices[t + 2] - 1
            t += 3
        }
        sb.append("        PolygonVertexIndex: *${polyIdx.size} { a: ")
        sb.append(polyIdx.joinToString(","))
        sb.appendLine(" }")

        sb.appendLine("        LayerElementNormal: 0 {")
        sb.appendLine("            MappingInformationType: \"ByVertice\"")
        sb.appendLine("            ReferenceInformationType: \"Direct\"")
        sb.append("            Normals: *${mesh.normals.size} { a: ")
        sb.append(mesh.normals.joinToString(","))
        sb.appendLine(" }")
        sb.appendLine("        }")

        sb.appendLine("        LayerElementUV: 0 {")
        sb.appendLine("            MappingInformationType: \"ByVertice\"")
        sb.appendLine("            ReferenceInformationType: \"Direct\"")
        sb.append("            UV: *${mesh.uvs.size} { a: ")
        sb.append(mesh.uvs.joinToString(","))
        sb.appendLine(" }")
        sb.appendLine("        }")
        sb.appendLine("    }")

        sb.appendLine("    Model: $modelId, \"Model::${project.name}\", \"Mesh\" {")
        sb.appendLine("        Version: 232")
        sb.appendLine("        Properties70:  {")
        sb.appendLine("            P: \"ShaderMode\", \"KString\", \"\", \"\", \"${project.materialConfig.shaderMode.name}\"")
        if (options.includePhysics) {
            project.physicsGroups.filter { it.enabled }.forEach { pg ->
                sb.appendLine("            P: \"Physics_${pg.groupType.name}\", \"KString\", \"\", \"\", \"${pg.physicsType.name}:stiff=${pg.stiffness}:damp=${pg.damping}:wind=${pg.windStrength}\"")
            }
        }
        sb.appendLine("        }")
        sb.appendLine("    }")

        val m = project.materialConfig
        sb.appendLine("    Material: $matId, \"Material::${project.name}_Mat\", \"\" {")
        sb.appendLine("        ShadingModel: \"Phong\"")
        sb.appendLine("        Properties70:  {")
        sb.appendLine("            P: \"Shininess\", \"double\", \"Number\", \"\",${((1f - m.roughness) * 100f)}")
        sb.appendLine("            P: \"Reflectivity\", \"double\", \"Number\", \"\",${m.metallic}")
        sb.appendLine("        }")
        sb.appendLine("    }")

        // Skeleton LimbNodes & Bone Constraints
        val boneIds = mutableMapOf<String, Long>()
        if (options.includeRigAndConstraints) {
            project.bones.forEachIndexed { idx, b ->
                val bid = 200000L + idx
                boneIds[b.id] = bid
                val boneName = when (options.targetEngine) {
                    TargetEngine.UNITY -> b.unityName
                    TargetEngine.UNREAL -> b.ue5Name
                    TargetEngine.UNIVERSAL -> b.name
                }
                val c = b.constraint
                sb.appendLine("    Model: $bid, \"Model::$boneName\", \"LimbNode\" {")
                sb.appendLine("        Properties70:  {")
                sb.appendLine("            P: \"Lcl Translation\", \"Lcl Translation\", \"\", \"A\",${b.restX},${b.restY},${b.restZ}")
                sb.appendLine("            P: \"RotationActive\", \"bool\", \"\", \"\",${if (c.enabled) 1 else 0}")
                sb.appendLine("            P: \"RotationMin\", \"Vector3D\", \"Vector\", \"\",${c.rotMinX},${c.rotMinY},${c.rotMinZ}")
                sb.appendLine("            P: \"RotationMax\", \"Vector3D\", \"Vector\", \"\",${c.rotMaxX},${c.rotMaxY},${c.rotMaxZ}")
                sb.appendLine("            P: \"ScalingMin\", \"Vector3D\", \"Vector\", \"\",${c.scaleMinX},${c.scaleMinY},${c.scaleMinZ}")
                sb.appendLine("            P: \"ScalingMax\", \"Vector3D\", \"Vector\", \"\",${c.scaleMaxX},${c.scaleMaxY},${c.scaleMaxZ}")
                sb.appendLine("            P: \"TranslationMin\", \"Vector3D\", \"Vector\", \"\",${c.posMinX},${c.posMinY},${c.posMinZ}")
                sb.appendLine("            P: \"TranslationMax\", \"Vector3D\", \"Vector\", \"\",${c.posMaxX},${c.posMaxY},${c.posMaxZ}")
                sb.appendLine("        }")
                sb.appendLine("    }")
            }
        }
        sb.appendLine("}")

        sb.appendLine("Connections:  {")
        sb.appendLine("    C: \"OO\",$geomId,$modelId")
        sb.appendLine("    C: \"OO\",$matId,$modelId")
        sb.appendLine("    C: \"OO\",$modelId,0")
        if (options.includeRigAndConstraints) {
            project.bones.forEach { b ->
                val cid = boneIds[b.id] ?: return@forEach
                val pid = b.parentId?.let { boneIds[it] } ?: 0L
                sb.appendLine("    C: \"OO\",$cid,$pid")
            }
        }
        sb.appendLine("}")

        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun floatsToLeBytes(arr: FloatArray): ByteArray {
        val bb = ByteBuffer.allocate(arr.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        arr.forEach { bb.putFloat(it) }
        return bb.array()
    }

    private fun intsToLeBytes(arr: IntArray): ByteArray {
        val bb = ByteBuffer.allocate(arr.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        arr.forEach { bb.putInt(it) }
        return bb.array()
    }

    private fun shortsToLeBytes(arr: ShortArray): ByteArray {
        val bb = ByteBuffer.allocate(arr.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        arr.forEach { bb.putShort(it) }
        return bb.array()
    }
}
