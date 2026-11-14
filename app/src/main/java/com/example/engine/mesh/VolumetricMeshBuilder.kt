package com.example.engine.mesh

import android.graphics.Bitmap
import android.graphics.Color
import com.example.domain.model.BoneNode
import com.example.domain.model.MaterialConfig
import com.example.domain.model.MeshDensity
import com.example.domain.model.MeshGroupType
import com.example.domain.model.ProceduralTextureType
import com.example.domain.model.SpriteAnalysisResult
import com.example.domain.model.VolumetricMesh
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object VolumetricMeshBuilder {

    fun buildMesh(
        sprite: Bitmap,
        analysis: SpriteAnalysisResult,
        bones: List<BoneNode>,
        density: MeshDensity
    ): VolumetricMesh {
        val cols = density.gridCols
        val rows = density.gridRows
        val mask = BooleanArray(cols * rows)

        // Sample alpha mask from sprite bitmap
        for (r in 0 until rows) {
            val py = ((r + 0.5f) / rows * sprite.height).toInt().coerceIn(0, sprite.height - 1)
            for (c in 0 until cols) {
                val px = ((c + 0.5f) / cols * sprite.width).toInt().coerceIn(0, sprite.width - 1)
                val alpha = Color.alpha(sprite.getPixel(px, py))
                mask[r * cols + c] = alpha > 40
            }
        }

        // Compute normalized distance to silhouette edge for smooth volumetric inflation
        val distField = FloatArray(cols * rows)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (!mask[r * cols + c]) {
                    distField[r * cols + c] = 0f
                    continue
                }
                var minDist = 999f
                val searchRadius = 5
                for (dr in -searchRadius..searchRadius) {
                    for (dc in -searchRadius..searchRadius) {
                        val nr = r + dr
                        val nc = c + dc
                        val isOut = nr < 0 || nr >= rows || nc < 0 || nc >= cols || !mask[nr * cols + nc]
                        if (isOut) {
                            val d = sqrt((dr * dr + dc * dc).toFloat())
                            if (d < minDist) minDist = d
                        }
                    }
                }
                distField[r * cols + c] = min(1f, minDist / 4.2f)
            }
        }

        // Build front (+Z) and back (-Z) vertex grids for active cells + boundary
        val nodeActive = BooleanArray((cols + 1) * (rows + 1))
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (mask[r * cols + c]) {
                    nodeActive[r * (cols + 1) + c] = true
                    nodeActive[r * (cols + 1) + (c + 1)] = true
                    nodeActive[(r + 1) * (cols + 1) + c] = true
                    nodeActive[(r + 1) * (cols + 1) + (c + 1)] = true
                }
            }
        }

        val frontVertIndex = IntArray((cols + 1) * (rows + 1)) { -1 }
        val backVertIndex = IntArray((cols + 1) * (rows + 1)) { -1 }

        val posList = ArrayList<Float>(cols * rows * 6)
        val uvList = ArrayList<Float>(cols * rows * 4)
        val groupList = ArrayList<Int>(cols * rows * 2)
        val jIdxList = ArrayList<Int>(cols * rows * 8)
        val jWtList = ArrayList<Float>(cols * rows * 8)

        val widthWorld = 1.15f
        val heightWorld = 1.45f
        val baseThickness = analysis.thicknessScale.coerceIn(0.16f, 0.50f)

        fun nodeDepth(gr: Int, gc: Int): Float {
            var sum = 0f
            var count = 0
            for (dr in -1..0) {
                for (dc in -1..0) {
                    val rr = gr + dr
                    val cc = gc + dc
                    if (rr in 0 until rows && cc in 0 until cols && mask[rr * cols + cc]) {
                        sum += distField[rr * cols + cc]
                        count++
                    }
                }
            }
            if (count == 0) return 0.015f
            val avg = sum / count.toFloat()
            val ny = gr.toFloat() / rows.toFloat()
            // Torso & head get fuller volumetric profile than feet/edges
            val anatomicalBoost = if (ny in 0.15f..0.68f) 1.15f else 0.88f
            return (0.025f + sqrt(avg) * baseThickness * anatomicalBoost)
        }

        fun assignSkinningAndGroup(wx: Float, wy: Float, wz: Float): Pair<IntArray, FloatArray> {
            val idx = IntArray(4)
            val dists = FloatArray(4) { Float.MAX_VALUE }
            for (bIdx in bones.indices) {
                val b = bones[bIdx]
                val dx = wx - b.restX
                val dy = wy - b.restY
                val dz = wz - b.restZ
                val d = dx * dx + dy * dy + dz * dz + 0.0005f
                for (slot in 0 until 4) {
                    if (d < dists[slot]) {
                        for (shift in 3 downTo slot + 1) {
                            dists[shift] = dists[shift - 1]
                            idx[shift] = idx[shift - 1]
                        }
                        dists[slot] = d
                        idx[slot] = bIdx
                        break
                    }
                }
            }
            val wts = FloatArray(4)
            var sumW = 0f
            for (k in 0 until 4) {
                val w = 1f / dists[k].pow(1.45f)
                wts[k] = w
                sumW += w
            }
            if (sumW > 0f) {
                for (k in 0 until 4) wts[k] /= sumW
            } else {
                wts[0] = 1f
            }
            return idx to wts
        }

        fun classifyGroup(wx: Float, wy: Float, wz: Float, nearestBone: BoneNode): Int {
            // Back/outer cape silhouette or explicit cape/hair bone affinity
            if (nearestBone.groupTag == MeshGroupType.CLOTH_CAPE) return MeshGroupType.CLOTH_CAPE.id
            if (nearestBone.groupTag == MeshGroupType.HAIR_PLUME) return MeshGroupType.HAIR_PLUME.id
            if (nearestBone.groupTag == MeshGroupType.SOFT_ACCESSORY) return MeshGroupType.SOFT_ACCESSORY.id
            if (analysis.hasCapeOrCloth && wz < -0.05f && wy in -0.45f..0.28f && abs(wx) > 0.14f) {
                return MeshGroupType.CLOTH_CAPE.id
            }
            if (analysis.hasFlowingHair && wy > 0.48f) {
                return MeshGroupType.HAIR_PLUME.id
            }
            return nearestBone.groupTag.id
        }

        fun pushVertex(wx: Float, wy: Float, wz: Float, u: Float, v: Float): Int {
            val vIndex = posList.size / 3
            posList.add(wx)
            posList.add(wy)
            posList.add(wz)
            uvList.add(u)
            uvList.add(v)
            val (bIdx, bWt) = assignSkinningAndGroup(wx, wy, wz)
            val primaryBone = bones.getOrNull(bIdx[0]) ?: bones.first()
            groupList.add(classifyGroup(wx, wy, wz, primaryBone))
            for (k in 0 until 4) {
                jIdxList.add(bIdx[k])
                jWtList.add(bWt[k])
            }
            return vIndex
        }

        for (r in 0..rows) {
            val v = r.toFloat() / rows.toFloat()
            val wy = (0.5f - v) * heightWorld
            for (c in 0..cols) {
                val nIdx = r * (cols + 1) + c
                if (!nodeActive[nIdx]) continue
                val u = c.toFloat() / cols.toFloat()
                val wx = (u - 0.5f) * widthWorld
                val depth = nodeDepth(r, c)
                frontVertIndex[nIdx] = pushVertex(wx, wy, depth, u, v)
                backVertIndex[nIdx] = pushVertex(wx, wy, -depth, u, v)
            }
        }

        val triIndices = ArrayList<Int>(cols * rows * 18)

        // Front and Back faces
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (!mask[r * cols + c]) continue
                val tl = r * (cols + 1) + c
                val tr = tl + 1
                val bl = (r + 1) * (cols + 1) + c
                val br = bl + 1

                // Front (+Z) CCW
                val ftl = frontVertIndex[tl]
                val ftr = frontVertIndex[tr]
                val fbl = frontVertIndex[bl]
                val fbr = frontVertIndex[br]
                triIndices.add(ftl); triIndices.add(fbl); triIndices.add(ftr)
                triIndices.add(ftr); triIndices.add(fbl); triIndices.add(fbr)

                // Back (-Z) CCW when viewed from back
                val btl = backVertIndex[tl]
                val btr = backVertIndex[tr]
                val bbl = backVertIndex[bl]
                val bbr = backVertIndex[br]
                triIndices.add(btl); triIndices.add(btr); triIndices.add(bbl)
                triIndices.add(btr); triIndices.add(bbr); triIndices.add(bbl)

                // Perimeter side walls stitching front to back
                // Top edge
                if (r == 0 || !mask[(r - 1) * cols + c]) {
                    triIndices.add(ftl); triIndices.add(ftr); triIndices.add(btl)
                    triIndices.add(ftr); triIndices.add(btr); triIndices.add(btl)
                }
                // Bottom edge
                if (r == rows - 1 || !mask[(r + 1) * cols + c]) {
                    triIndices.add(fbl); triIndices.add(bbl); triIndices.add(fbr)
                    triIndices.add(fbr); triIndices.add(bbl); triIndices.add(bbr)
                }
                // Left edge
                if (c == 0 || !mask[r * cols + (c - 1)]) {
                    triIndices.add(ftl); triIndices.add(btl); triIndices.add(fbl)
                    triIndices.add(fbl); triIndices.add(btl); triIndices.add(bbl)
                }
                // Right edge
                if (c == cols - 1 || !mask[r * cols + (c + 1)]) {
                    triIndices.add(ftr); triIndices.add(fbr); triIndices.add(btr)
                    triIndices.add(fbr); triIndices.add(bbr); triIndices.add(btr)
                }
            }
        }

        val positions = posList.toFloatArray()
        val indices = triIndices.toIntArray()
        val normals = computeSmoothNormals(positions, indices)

        return VolumetricMesh(
            basePositions = positions,
            normals = normals,
            uvs = uvList.toFloatArray(),
            jointIndices = jIdxList.toIntArray(),
            jointWeights = jWtList.toFloatArray(),
            groupTags = groupList.toIntArray(),
            indices = indices
        )
    }

    fun computeSmoothNormals(positions: FloatArray, indices: IntArray): FloatArray {
        val normals = FloatArray(positions.size)
        var i = 0
        while (i < indices.size) {
            val i0 = indices[i] * 3
            val i1 = indices[i + 1] * 3
            val i2 = indices[i + 2] * 3

            val ax = positions[i1] - positions[i0]
            val ay = positions[i1 + 1] - positions[i0 + 1]
            val az = positions[i1 + 2] - positions[i0 + 2]

            val bx = positions[i2] - positions[i0]
            val by = positions[i2 + 1] - positions[i0 + 1]
            val bz = positions[i2 + 2] - positions[i0 + 2]

            val nx = ay * bz - az * by
            val ny = az * bx - ax * bz
            val nz = ax * by - ay * bx

            normals[i0] += nx; normals[i0 + 1] += ny; normals[i0 + 2] += nz
            normals[i1] += nx; normals[i1 + 1] += ny; normals[i1 + 2] += nz
            normals[i2] += nx; normals[i2 + 1] += ny; normals[i2 + 2] += nz
            i += 3
        }
        var v = 0
        while (v < normals.size) {
            val len = sqrt(
                normals[v] * normals[v] +
                    normals[v + 1] * normals[v + 1] +
                    normals[v + 2] * normals[v + 2]
            )
            if (len > 1e-5f) {
                normals[v] /= len
                normals[v + 1] /= len
                normals[v + 2] /= len
            } else {
                normals[v + 2] = 1f
            }
            v += 3
        }
        return normals
    }

    /**
     * Generates a tangent-space normal map from sprite luminance gradients + procedural micro-detail.
     */
    fun generateNormalMap(sprite: Bitmap, material: MaterialConfig): Bitmap {
        val w = sprite.width
        val h = sprite.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        sprite.getPixels(pixels, 0, w, 0, 0, w, h)
        val outPixels = IntArray(w * h)

        fun lumaAt(x: Int, y: Int): Float {
            val cx = x.coerceIn(0, w - 1)
            val cy = y.coerceIn(0, h - 1)
            val p = pixels[cy * w + cx]
            val a = Color.alpha(p) / 255f
            val r = Color.red(p) / 255f
            val g = Color.green(p) / 255f
            val b = Color.blue(p) / 255f
            val baseLuma = (0.299f * r + 0.587f * g + 0.114f * b) * a
            val proc = proceduralSample(
                cx.toFloat() / w,
                cy.toFloat() / h,
                material.proceduralTexture,
                material.proceduralScale
            )
            return baseLuma + proc * material.proceduralBlend * 0.45f
        }

        val strength = (material.normalStrength * 2.6f).coerceIn(0f, 5f)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val tl = lumaAt(x - 1, y - 1)
                val t = lumaAt(x, y - 1)
                val tr = lumaAt(x + 1, y - 1)
                val l = lumaAt(x - 1, y)
                val r = lumaAt(x + 1, y)
                val bl = lumaAt(x - 1, y + 1)
                val b = lumaAt(x, y + 1)
                val br = lumaAt(x + 1, y + 1)

                val dx = (tr + 2f * r + br) - (tl + 2f * l + bl)
                val dy = (bl + 2f * b + br) - (tl + 2f * t + tr)

                var nx = -dx * strength
                var ny = -dy * strength
                var nz = 1.0f
                val len = sqrt(nx * nx + ny * ny + nz * nz)
                nx /= len; ny /= len; nz /= len

                val red = ((nx * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255)
                val green = ((ny * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255)
                val blue = ((nz * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255)
                outPixels[y * w + x] = Color.rgb(red, green, blue)
            }
        }
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Generates a glTF 2.0 packed Metallic-Roughness texture (G = roughness, B = metallic).
     */
    fun generateMetallicRoughnessMap(sprite: Bitmap, material: MaterialConfig): Bitmap {
        val w = sprite.width
        val h = sprite.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(w * h)
        for (y in 0 until h) {
            val v = y.toFloat() / h
            for (x in 0 until w) {
                val u = x.toFloat() / w
                val proc = proceduralSample(u, v, material.proceduralTexture, material.proceduralScale)
                val rough = (material.roughness + (proc - 0.5f) * material.proceduralBlend * 0.35f)
                    .coerceIn(0.05f, 1.0f)
                val metal = (material.metallic + (proc - 0.5f) * material.proceduralBlend * 0.2f)
                    .coerceIn(0.0f, 1.0f)
                val gByte = (rough * 255f).toInt().coerceIn(0, 255)
                val bByte = (metal * 255f).toInt().coerceIn(0, 255)
                outPixels[y * w + x] = Color.rgb(255, gByte, bByte)
            }
        }
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun proceduralSample(
        u: Float,
        v: Float,
        type: ProceduralTextureType,
        scale: Float
    ): Float {
        val s = max(1f, scale)
        return when (type) {
            ProceduralTextureType.NONE -> 0.5f
            ProceduralTextureType.BRUSHED_METAL -> {
                val streak = sin(v * s * 28f) * 0.35f + sin(u * s * 4f + v * 12f) * 0.15f
                0.5f + streak
            }
            ProceduralTextureType.WOVEN_CLOTH -> {
                val warp = sin(u * s * 18f)
                val weft = sin(v * s * 18f)
                0.5f + 0.35f * (warp * weft)
            }
            ProceduralTextureType.SCALED_LEATHER -> {
                val cells = abs(sin(u * s * 10f) * cos(v * s * 10f))
                0.35f + cells * 0.55f
            }
            ProceduralTextureType.HEX_TECH -> {
                val hx = abs(sin(u * s * 12f + sin(v * s * 6f)))
                val hy = abs(cos(v * s * 12f))
                0.4f + 0.5f * min(hx, hy)
            }
            ProceduralTextureType.MARBLE_NOISE -> {
                val vein = sin((u * 4f + v * 3f + sin(u * s * 2.5f) * 0.8f) * s)
                0.5f + 0.45f * vein
            }
        }
    }
}
