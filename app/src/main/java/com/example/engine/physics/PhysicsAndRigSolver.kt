package com.example.engine.physics

import android.opengl.Matrix
import com.example.domain.model.AnimationSettings
import com.example.domain.model.BoneNode
import com.example.domain.model.LocomotionMode
import com.example.domain.model.MeshGroupPhysics
import com.example.domain.model.PhysicsType
import com.example.domain.model.VolumetricMesh
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class SolverFrameOutput(
    val deformedPositions: FloatArray,
    val deformedNormals: FloatArray,
    val boneWorldPositions: FloatArray,
    val physicsVectors: FloatArray,
    val clampedBoneIds: Set<String>,
    val facingYawDeg: Float,
    val speedNormalized: Float
)

class PhysicsAndRigSolver {

    private var simTime = 0f
    private var walkPhase = 0f
    private var facingYawRad = 0f
    private var worldOffsetX = 0f
    private var worldOffsetZ = 0f
    private var prevVelX = 0f
    private var prevVelZ = 0f
    private var gustAmplitude = 0f

    // Verlet state buffers
    private var prevPositions: FloatArray? = null
    private var currPositions: FloatArray? = null
    private var lastMeshIdentity: VolumetricMesh? = null

    fun triggerWindGust(strength: Float = 1.6f) {
        gustAmplitude = max(gustAmplitude, strength)
    }

    fun resetState() {
        simTime = 0f
        walkPhase = 0f
        facingYawRad = 0f
        worldOffsetX = 0f
        worldOffsetZ = 0f
        prevPositions = null
        currPositions = null
    }

    fun step(
        dtRaw: Float,
        mesh: VolumetricMesh,
        bones: List<BoneNode>,
        physicsGroups: List<MeshGroupPhysics>,
        animSettings: AnimationSettings,
        joyX: Float,
        joyY: Float
    ): SolverFrameOutput {
        val dt = dtRaw.coerceIn(0.004f, 0.045f)
        val speedFactor = animSettings.playbackSpeed.coerceIn(0.15f, 2.5f)
        val level = animSettings.animationLevel.coerceIn(0f, 1.5f)
        simTime += dt * speedFactor

        if (lastMeshIdentity !== mesh || prevPositions == null || prevPositions!!.size != mesh.basePositions.size) {
            lastMeshIdentity = mesh
            prevPositions = mesh.basePositions.copyOf()
            currPositions = mesh.basePositions.copyOf()
        }

        val joyMag = hypot(joyX, joyY).coerceIn(0f, 1f)
        val effectiveMag = when (animSettings.locomotionMode) {
            LocomotionMode.IDLE -> 0f
            LocomotionMode.WALK -> max(0.45f, joyMag)
            LocomotionMode.RUN -> 1.0f
            LocomotionMode.STRAFE -> max(0.65f, joyMag)
            LocomotionMode.JUMP -> max(0.35f, joyMag)
            LocomotionMode.TURN -> 0.55f
            LocomotionMode.AUTO -> joyMag
        }

        // Compute omnidirectional facing & velocity
        val velX: Float
        val velZ: Float
        when (animSettings.locomotionMode) {
            LocomotionMode.TURN -> {
                facingYawRad += dt * 2.4f * speedFactor
                velX = 0f
                velZ = 0f
            }
            LocomotionMode.STRAFE -> {
                velX = joyX * 1.4f
                velZ = -joyY * 1.4f
            }
            else -> {
                if (joyMag > 0.08f) {
                    val targetYaw = atan2(joyX, -joyY)
                    var diff = targetYaw - facingYawRad
                    while (diff > PI) diff -= (2.0 * PI).toFloat()
                    while (diff < -PI) diff += (2.0 * PI).toFloat()
                    facingYawRad += diff * min(1f, dt * 11f)
                }
                val moveSpeed = effectiveMag * 1.8f
                velX = if (joyMag > 0.08f) joyX * moveSpeed else sin(facingYawRad) * effectiveMag * 0.6f
                velZ = if (joyMag > 0.08f) -joyY * moveSpeed else cos(facingYawRad) * effectiveMag * 0.6f
            }
        }

        worldOffsetX += velX * dt
        worldOffsetZ += velZ * dt

        val phaseRate = when {
            effectiveMag < 0.08f -> 2.1f
            effectiveMag < 0.6f -> 6.8f
            else -> 11.4f
        }
        walkPhase += dt * phaseRate * speedFactor

        val accelX = (velX - prevVelX) / dt
        val accelZ = (velZ - prevVelZ) / dt
        prevVelX = velX
        prevVelZ = velZ
        gustAmplitude = max(0f, gustAmplitude - dt * 1.25f)

        // Evaluate bone local transforms + apply user BoneConstraint clamping
        val boneCount = bones.size
        val boneIndexMap = HashMap<String, Int>(boneCount)
        bones.forEachIndexed { i, b -> boneIndexMap[b.id] = i }

        val localMatrices = Array(boneCount) { FloatArray(16) }
        val worldMatrices = Array(boneCount) { FloatArray(16) }
        val skinMatrices = Array(boneCount) { FloatArray(16) }
        val clampedBones = HashSet<String>()

        val strideAmp = effectiveMag * level
        val breath = sin(simTime * 2.2f) * 2.2f * level
        val swing = sin(walkPhase) * 34f * strideAmp
        val oppSwing = -swing
        val kneeBendL = max(0f, -cos(walkPhase)) * 52f * strideAmp
        val kneeBendR = max(0f, cos(walkPhase)) * 52f * strideAmp
        val jumpOffsetY = if (animSettings.locomotionMode == LocomotionMode.JUMP) {
            abs(sin(simTime * 3.8f * speedFactor)) * 0.28f * level
        } else {
            abs(sin(walkPhase)) * 0.055f * strideAmp
        }

        val yawDeg = Math.toDegrees(facingYawRad.toDouble()).toFloat()

        for (i in 0 until boneCount) {
            val b = bones[i]
            var rx = 0f
            var ry = 0f
            var rz = 0f
            var sx = 1f
            var sy = 1f
            var sz = 1f
            var px = 0f
            var py = 0f
            var pz = 0f

            val key = b.id.lowercase()
            when {
                key == "hips" -> {
                    py = jumpOffsetY
                    ry = sin(walkPhase) * 8f * strideAmp
                    rx = effectiveMag * 9f * level
                    rz = -joyX * 7f * strideAmp
                    // Squash & stretch during jump
                    if (animSettings.locomotionMode == LocomotionMode.JUMP) {
                        val s = 1f + sin(simTime * 7.6f) * 0.06f * level
                        sy = s
                        sx = 1f / sqrt(s)
                        sz = 1f / sqrt(s)
                    }
                }
                key == "spine" -> {
                    rx = breath + effectiveMag * 10f * level
                    ry = -sin(walkPhase) * 10f * strideAmp
                }
                key == "chest" -> {
                    rx = breath * 0.8f
                    ry = -sin(walkPhase) * 12f * strideAmp
                }
                key == "neck" || key == "head" -> {
                    rx = -breath * 0.6f
                    ry = sin(simTime * 1.3f) * 7f * level
                }
                key.contains("upper_arm_l") || key == "front_l" -> {
                    rx = oppSwing * 0.95f
                    rz = -10f - effectiveMag * 8f * level
                }
                key.contains("upper_arm_r") || key == "front_r" -> {
                    rx = swing * 0.95f
                    rz = 10f + effectiveMag * 8f * level
                }
                key.contains("forearm_l") || key == "paw_fl" -> {
                    rx = -22f - max(0f, oppSwing) * 0.65f
                }
                key.contains("forearm_r") || key == "paw_fr" -> {
                    rx = -22f - max(0f, swing) * 0.65f
                }
                key.contains("thigh_l") || key == "hind_l" -> {
                    rx = swing * 1.15f
                }
                key.contains("thigh_r") || key == "hind_r" -> {
                    rx = oppSwing * 1.15f
                }
                key.contains("shin_l") || key == "paw_hl" -> {
                    rx = kneeBendL
                }
                key.contains("shin_r") || key == "paw_hr" -> {
                    rx = kneeBendR
                }
                key.contains("foot") -> {
                    rx = -kneeBendL * 0.25f
                }
                key.contains("cape") || key.contains("tail") -> {
                    rx = 14f + effectiveMag * 26f * level + sin(simTime * 4.5f) * 10f * level
                    ry = cos(simTime * 3.1f) * 12f * level
                }
                key.contains("hair") -> {
                    rx = effectiveMag * 16f * level + sin(simTime * 5.2f) * 8f * level
                    rz = sin(simTime * 3.8f) * 7f * level
                }
            }

            // Enforce BoneConstraint limits (Rotation, Scaling, Position)
            val c = b.constraint
            if (c.enabled) {
                val crx = rx.coerceIn(c.rotMinX, c.rotMaxX)
                val cry = ry.coerceIn(c.rotMinY, c.rotMaxY)
                val crz = rz.coerceIn(c.rotMinZ, c.rotMaxZ)
                if (crx != rx || cry != ry || crz != rz) clampedBones.add(b.id)
                rx = crx; ry = cry; rz = crz

                val csx = sx.coerceIn(c.scaleMinX, c.scaleMaxX)
                val csy = sy.coerceIn(c.scaleMinY, c.scaleMaxY)
                val csz = sz.coerceIn(c.scaleMinZ, c.scaleMaxZ)
                if (csx != sx || csy != sy || csz != sz) clampedBones.add(b.id)
                sx = csx; sy = csy; sz = csz

                px = if (c.lockPosX) 0f else px.coerceIn(c.posMinX, c.posMaxX)
                py = if (c.lockPosY) 0f else py.coerceIn(c.posMinY, c.posMaxY)
                pz = if (c.lockPosZ) 0f else pz.coerceIn(c.posMinZ, c.posMaxZ)
            }

            // Build local delta matrix around bone rest position
            val m = localMatrices[i]
            Matrix.setIdentityM(m, 0)
            val parentIdx = b.parentId?.let { boneIndexMap[it] }
            val parentRestX = if (parentIdx != null) bones[parentIdx].restX else 0f
            val parentRestY = if (parentIdx != null) bones[parentIdx].restY else 0f
            val parentRestZ = if (parentIdx != null) bones[parentIdx].restZ else 0f

            Matrix.translateM(
                m, 0,
                (b.restX - parentRestX) + px,
                (b.restY - parentRestY) + py,
                (b.restZ - parentRestZ) + pz
            )
            Matrix.rotateM(m, 0, ry, 0f, 1f, 0f)
            Matrix.rotateM(m, 0, rx, 1f, 0f, 0f)
            Matrix.rotateM(m, 0, rz, 0f, 0f, 1f)
            Matrix.scaleM(m, 0, sx, sy, sz)

            if (parentIdx != null) {
                Matrix.multiplyMM(worldMatrices[i], 0, worldMatrices[parentIdx], 0, m, 0)
            } else {
                // Apply character omnidirectional facing at root
                val rootRot = FloatArray(16)
                Matrix.setIdentityM(rootRot, 0)
                Matrix.rotateM(rootRot, 0, yawDeg, 0f, 1f, 0f)
                Matrix.multiplyMM(worldMatrices[i], 0, rootRot, 0, m, 0)
            }

            // Skinning offset = worldMatrix * inverseBind(rest)
            val invBind = FloatArray(16)
            Matrix.setIdentityM(invBind, 0)
            Matrix.translateM(invBind, 0, -b.restX, -b.restY, -b.restZ)
            Matrix.multiplyMM(skinMatrices[i], 0, worldMatrices[i], 0, invBind, 0)
        }

        // Linear Blend Skinning pass for target deformed positions
        val vCount = mesh.vertexCount
        val skinnedPositions = FloatArray(vCount * 3)
        val deformedNormals = FloatArray(vCount * 3)
        val basePos = mesh.basePositions
        val baseNorm = mesh.normals
        val jIdx = mesh.jointIndices
        val jWt = mesh.jointWeights

        for (v in 0 until vCount) {
            val p3 = v * 3
            val bx = basePos[p3]
            val by = basePos[p3 + 1]
            val bz = basePos[p3 + 2]
            val nx = baseNorm[p3]
            val ny = baseNorm[p3 + 1]
            val nz = baseNorm[p3 + 2]

            var ox = 0f; var oy = 0f; var oz = 0f
            var onx = 0f; var ony = 0f; var onz = 0f

            val j4 = v * 4
            for (k in 0 until 4) {
                val w = jWt[j4 + k]
                if (w <= 0.0001f) continue
                val boneIdx = jIdx[j4 + k].coerceIn(0, boneCount - 1)
                val sm = skinMatrices[boneIdx]
                ox += (sm[0] * bx + sm[4] * by + sm[8] * bz + sm[12]) * w
                oy += (sm[1] * bx + sm[5] * by + sm[9] * bz + sm[13]) * w
                oz += (sm[2] * bx + sm[6] * by + sm[10] * bz + sm[14]) * w

                onx += (sm[0] * nx + sm[4] * ny + sm[8] * nz) * w
                ony += (sm[1] * nx + sm[5] * ny + sm[9] * nz) * w
                onz += (sm[2] * nx + sm[6] * ny + sm[10] * nz) * w
            }

            skinnedPositions[p3] = ox
            skinnedPositions[p3 + 1] = oy
            skinnedPositions[p3 + 2] = oz

            val nLen = sqrt(onx * onx + ony * ony + onz * onz)
            if (nLen > 1e-5f) {
                deformedNormals[p3] = onx / nLen
                deformedNormals[p3 + 1] = ony / nLen
                deformedNormals[p3 + 2] = onz / nLen
            } else {
                deformedNormals[p3 + 2] = 1f
            }
        }

        // Verlet & Spring-Damper Physics pass for enabled Cloth / Hair / Soft Body groups
        val physicsMap = physicsGroups.associateBy { it.groupType.id }
        val prev = prevPositions!!
        val curr = currPositions!!
        val finalPos = FloatArray(vCount * 3)
        val vectorLines = ArrayList<Float>(vCount / 2)

        val windDirX = cos(simTime * 1.4f) * 0.65f + 0.35f
        val windDirZ = sin(simTime * 1.1f) * 0.55f - 0.55f

        for (v in 0 until vCount) {
            val p3 = v * 3
            val tx = skinnedPositions[p3]
            val ty = skinnedPositions[p3 + 1]
            val tz = skinnedPositions[p3 + 2]

            val grpId = mesh.groupTags[v]
            val cfg = physicsMap[grpId]

            if (cfg == null || !cfg.enabled) {
                curr[p3] = tx; curr[p3 + 1] = ty; curr[p3 + 2] = tz
                prev[p3] = tx; prev[p3 + 1] = ty; prev[p3 + 2] = tz
                finalPos[p3] = tx; finalPos[p3 + 1] = ty; finalPos[p3 + 2] = tz
                continue
            }

            val damping = cfg.damping.coerceIn(0.55f, 0.96f)
            val vx = (curr[p3] - prev[p3]) * damping
            val vy = (curr[p3 + 1] - prev[p3 + 1]) * damping
            val vz = (curr[p3 + 2] - prev[p3 + 2]) * damping

            prev[p3] = curr[p3]
            prev[p3 + 1] = curr[p3 + 1]
            prev[p3 + 2] = curr[p3 + 2]

            val massInv = 1f / max(0.25f, cfg.mass)
            val wave = sin(simTime * 6.5f + tx * 5f + ty * 4f)
            val windScale = (cfg.windStrength + gustAmplitude) * (0.6f + 0.4f * wave)

            val fx = (-velX * 0.9f - accelX * 0.08f + windDirX * windScale) * massInv
            val fy = (-0.55f * cfg.gravityScale) * massInv
            val fz = (-velZ * 0.9f - accelZ * 0.08f + windDirZ * windScale) * massInv

            var nx = curr[p3] + vx + fx * dt * dt * 24f
            var ny = curr[p3 + 1] + vy + fy * dt * dt * 24f
            var nz = curr[p3 + 2] + vz + fz * dt * dt * 24f

            // Spring return toward skinned animation target (stronger near anchor/top, looser at tips)
            val anchorPull = when (cfg.physicsType) {
                PhysicsType.CLOTH -> cfg.stiffness * (if (ty > 0.1f) 0.92f else 0.55f)
                PhysicsType.HAIR -> cfg.stiffness * (if (ty > 0.55f) 0.90f else 0.62f)
                PhysicsType.SOFT_BODY -> cfg.stiffness * 0.82f
            }.coerceIn(0.12f, 0.97f)

            nx += (tx - nx) * anchorPull
            ny += (ty - ny) * anchorPull
            nz += (tz - nz) * anchorPull

            // Torso sphere collision check
            val colR = cfg.collisionRadius
            if (cfg.physicsType == PhysicsType.CLOTH && colR > 0.02f) {
                val dx = nx
                val dy = ny - 0.10f
                val dz = nz
                val distSq = dx * dx + dy * dy + dz * dz
                val minR = colR * 0.72f
                if (distSq < minR * minR && distSq > 1e-5f) {
                    val d = sqrt(distSq)
                    val push = (minR - d) / d
                    nx += dx * push * 0.45f
                    nz += dz * push * 0.45f
                }
            }

            // Clamp maximum displacement to avoid mesh tearing
            val maxDisp = when (cfg.physicsType) {
                PhysicsType.CLOTH -> 0.24f
                PhysicsType.HAIR -> 0.18f
                PhysicsType.SOFT_BODY -> 0.12f
            }
            val dispX = nx - tx
            val dispY = ny - ty
            val dispZ = nz - tz
            val dispLen = sqrt(dispX * dispX + dispY * dispY + dispZ * dispZ)
            if (dispLen > maxDisp) {
                val s = maxDisp / dispLen
                nx = tx + dispX * s
                ny = ty + dispY * s
                nz = tz + dispZ * s
            }

            curr[p3] = nx; curr[p3 + 1] = ny; curr[p3 + 2] = nz
            finalPos[p3] = nx; finalPos[p3 + 1] = ny; finalPos[p3 + 2] = nz

            if (animSettings.showPhysicsVectors && v % 5 == 0 && dispLen > 0.008f) {
                vectorLines.add(tx); vectorLines.add(ty); vectorLines.add(tz)
                vectorLines.add(nx + (nx - tx) * 1.4f)
                vectorLines.add(ny + (ny - ty) * 1.4f)
                vectorLines.add(nz + (nz - tz) * 1.4f)
            }
        }

        // Extract world bone positions for skeleton X-ray rendering
        val boneWorldPos = FloatArray(boneCount * 3)
        for (i in 0 until boneCount) {
            val wm = worldMatrices[i]
            boneWorldPos[i * 3] = wm[12]
            boneWorldPos[i * 3 + 1] = wm[13]
            boneWorldPos[i * 3 + 2] = wm[14]
        }

        return SolverFrameOutput(
            deformedPositions = finalPos,
            deformedNormals = deformedNormals,
            boneWorldPositions = boneWorldPos,
            physicsVectors = vectorLines.toFloatArray(),
            clampedBoneIds = clampedBones,
            facingYawDeg = yawDeg,
            speedNormalized = effectiveMag
        )
    }
}
