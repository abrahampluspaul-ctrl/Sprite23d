package com.example.engine.render

import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import com.example.domain.model.AnimationSettings
import com.example.domain.model.BoneNode
import com.example.domain.model.MaterialConfig
import com.example.domain.model.MeshGroupPhysics
import com.example.domain.model.ShaderMode
import com.example.domain.model.VolumetricMesh
import com.example.engine.mesh.VolumetricMeshBuilder
import com.example.engine.physics.PhysicsAndRigSolver
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

class Sprite3DRenderer(
    private val solver: PhysicsAndRigSolver,
    private val onClampedBonesUpdated: (Set<String>) -> Unit = {}
) : GLSurfaceView.Renderer {

    @Volatile var mesh: VolumetricMesh? = null
    @Volatile var bones: List<BoneNode> = emptyList()
    @Volatile var selectedBoneId: String? = null
    @Volatile var physicsGroups: List<MeshGroupPhysics> = emptyList()
    @Volatile var materialConfig: MaterialConfig = MaterialConfig()
    @Volatile var animSettings: AnimationSettings = AnimationSettings()
    @Volatile var joyX: Float = 0f
    @Volatile var joyY: Float = 0f

    // Camera orbit parameters
    @Volatile var cameraYawDeg: Float = 22f
    @Volatile var cameraPitchDeg: Float = 16f
    @Volatile var cameraDistance: Float = 2.65f

    private var pendingAlbedoBitmap: Bitmap? = null
    private var pendingTexturesDirty = false

    private var meshProgram = 0
    private var lineProgram = 0
    private var albedoTexId = 0
    private var normalTexId = 0
    private var pbrTexId = 0

    private var gridVertexBuffer: FloatBuffer? = null
    private var gridVertexCount = 0

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    private var lastFrameNs = System.nanoTime()
    private var elapsedSec = 0f
    private var frameCounter = 0

    // Reusable dynamic buffers
    private var posBuffer: FloatBuffer? = null
    private var normBuffer: FloatBuffer? = null
    private var uvBuffer: FloatBuffer? = null
    private var idxBuffer: ShortBuffer? = null
    private var lastUploadedMesh: VolumetricMesh? = null

    fun setTexturesFromSprite(sprite: Bitmap, material: MaterialConfig) {
        pendingAlbedoBitmap = sprite
        materialConfig = material
        pendingTexturesDirty = true
    }

    fun markDetailTexturesDirty() {
        pendingTexturesDirty = true
    }

    fun resetCamera() {
        cameraYawDeg = 22f
        cameraPitchDeg = 16f
        cameraDistance = 2.65f
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.043f, 0.055f, 0.078f, 1.0f) // #0B0E14
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        meshProgram = createProgram(MESH_VERTEX_SHADER, MESH_FRAGMENT_SHADER)
        lineProgram = createProgram(LINE_VERTEX_SHADER, LINE_FRAGMENT_SHADER)

        val texIds = IntArray(3)
        GLES20.glGenTextures(3, texIds, 0)
        albedoTexId = texIds[0]
        normalTexId = texIds[1]
        pbrTexId = texIds[2]

        buildStudioGrid()
        pendingTexturesDirty = true
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = if (height > 0) width.toFloat() / height.toFloat() else 1f
        Matrix.perspectiveM(projMatrix, 0, 44f, aspect, 0.1f, 40f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastFrameNs) / 1_000_000_000.0).toFloat().coerceIn(0.004f, 0.05f)
        lastFrameNs = now
        elapsedSec += dt

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        if (pendingTexturesDirty && pendingAlbedoBitmap != null) {
            uploadTextures(pendingAlbedoBitmap!!, materialConfig)
            pendingTexturesDirty = false
        }

        // Orbit Camera View Matrix
        val yawRad = Math.toRadians(cameraYawDeg.toDouble())
        val pitchRad = Math.toRadians(cameraPitchDeg.coerceIn(-45f, 75f).toDouble())
        val dist = cameraDistance.coerceIn(1.25f, 6.0f)
        val eyeX = (dist * cos(pitchRad) * sin(yawRad)).toFloat()
        val eyeY = (dist * sin(pitchRad) + 0.05f).toFloat()
        val eyeZ = (dist * cos(pitchRad) * cos(yawRad)).toFloat()

        Matrix.setLookAtM(viewMatrix, 0, eyeX, eyeY, eyeZ, 0f, 0.02f, 0f, 0f, 1f, 0f)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        // 1. Draw infinite studio floor grid
        drawStudioGrid()

        val activeMesh = mesh ?: return
        val activeBones = bones
        if (activeBones.isEmpty()) return

        // 2. Step Physics & Rig Solver
        val frameOut = solver.step(
            dtRaw = dt,
            mesh = activeMesh,
            bones = activeBones,
            physicsGroups = physicsGroups,
            animSettings = animSettings,
            joyX = joyX,
            joyY = joyY
        )

        frameCounter++
        if (frameCounter % 6 == 0) {
            onClampedBonesUpdated(frameOut.clampedBoneIds)
        }

        // 3. Draw 3D Character Mesh with PBR + Procedural + Custom Shaders
        drawVolumetricMesh(activeMesh, frameOut.deformedPositions, frameOut.deformedNormals, eyeX, eyeY, eyeZ)

        // 4. Draw Skeleton X-Ray, Bone Constraint Gizmos, and Physics Velocity Vectors
        if (animSettings.showSkeleton || animSettings.showConstraintGizmos || animSettings.showPhysicsVectors) {
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            if (animSettings.showSkeleton) {
                drawSkeletonOverlay(activeBones, frameOut.boneWorldPositions, frameOut.clampedBoneIds)
            }
            if (animSettings.showConstraintGizmos) {
                drawConstraintGizmos(activeBones, frameOut.boneWorldPositions, frameOut.clampedBoneIds)
            }
            if (animSettings.showPhysicsVectors && frameOut.physicsVectors.isNotEmpty()) {
                drawLineArray(frameOut.physicsVectors, 0.65f, 0.55f, 0.98f, 0.9f, 3.0f)
            }
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        }
    }

    private fun uploadTextures(sprite: Bitmap, mat: MaterialConfig) {
        bindBitmapToTexture(albedoTexId, sprite)
        val normalBmp = VolumetricMeshBuilder.generateNormalMap(sprite, mat)
        bindBitmapToTexture(normalTexId, normalBmp)
        normalBmp.recycle()
        val pbrBmp = VolumetricMeshBuilder.generateMetallicRoughnessMap(sprite, mat)
        bindBitmapToTexture(pbrTexId, pbrBmp)
        pbrBmp.recycle()
    }

    private fun bindBitmapToTexture(texId: Int, bmp: Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
    }

    private fun drawStudioGrid() {
        val gb = gridVertexBuffer ?: return
        GLES20.glUseProgram(lineProgram)
        val mvpLoc = GLES20.glGetUniformLocation(lineProgram, "uMVP")
        val colorLoc = GLES20.glGetUniformLocation(lineProgram, "uColor")
        val posLoc = GLES20.glGetAttribLocation(lineProgram, "aPos")

        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glEnableVertexAttribArray(posLoc)
        gb.position(0)
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 0, gb)

        // Subtle slate grid lines
        GLES20.glUniform4f(colorLoc, 0.16f, 0.21f, 0.30f, 0.72f)
        GLES20.glLineWidth(1.5f)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, gridVertexCount - 4)

        // Cyan & Amber origin axes
        GLES20.glUniform4f(colorLoc, 0.22f, 0.74f, 0.97f, 0.85f)
        GLES20.glLineWidth(2.5f)
        GLES20.glDrawArrays(GLES20.GL_LINES, gridVertexCount - 4, 2)

        GLES20.glUniform4f(colorLoc, 0.96f, 0.62f, 0.04f, 0.85f)
        GLES20.glDrawArrays(GLES20.GL_LINES, gridVertexCount - 2, 2)
        GLES20.glDisableVertexAttribArray(posLoc)
    }

    private fun drawVolumetricMesh(
        m: VolumetricMesh,
        positions: FloatArray,
        normals: FloatArray,
        eyeX: Float,
        eyeY: Float,
        eyeZ: Float
    ) {
        if (lastUploadedMesh !== m) {
            lastUploadedMesh = m
            posBuffer = allocFloatBuffer(positions.size)
            normBuffer = allocFloatBuffer(normals.size)
            uvBuffer = allocFloatBuffer(m.uvs.size).apply {
                put(m.uvs)
                position(0)
            }
            val shortArr = ShortArray(m.indices.size) { m.indices[it].toShort() }
            idxBuffer = ByteBuffer.allocateDirect(shortArr.size * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .apply {
                    put(shortArr)
                    position(0)
                }
        }

        val pb = posBuffer!!
        pb.position(0); pb.put(positions); pb.position(0)
        val nb = normBuffer!!
        nb.position(0); nb.put(normals); nb.position(0)
        val uvb = uvBuffer!!; uvb.position(0)
        val ib = idxBuffer!!; ib.position(0)

        GLES20.glUseProgram(meshProgram)

        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(meshProgram, "uMVP"), 1, false, mvpMatrix, 0)
        GLES20.glUniform3f(GLES20.glGetUniformLocation(meshProgram, "uCameraPos"), eyeX, eyeY, eyeZ)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(meshProgram, "uTime"), elapsedSec)

        val mat = materialConfig
        val tintR = Color.red(mat.albedoTintHex.toInt()) / 255f
        val tintG = Color.green(mat.albedoTintHex.toInt()) / 255f
        val tintB = Color.blue(mat.albedoTintHex.toInt()) / 255f
        GLES20.glUniform3f(GLES20.glGetUniformLocation(meshProgram, "uAlbedoTint"), tintR, tintG, tintB)

        val rimR = Color.red(mat.rimColorHex.toInt()) / 255f
        val rimG = Color.green(mat.rimColorHex.toInt()) / 255f
        val rimB = Color.blue(mat.rimColorHex.toInt()) / 255f
        GLES20.glUniform3f(GLES20.glGetUniformLocation(meshProgram, "uRimColor"), rimR, rimG, rimB)

        GLES20.glUniform1f(GLES20.glGetUniformLocation(meshProgram, "uExposure"), mat.exposure)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(meshProgram, "uNormalStrength"), mat.normalStrength)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(meshProgram, "uRoughness"), mat.roughness)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(meshProgram, "uMetallic"), mat.metallic)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(meshProgram, "uRimPower"), mat.rimPower)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(meshProgram, "uCelBands"), mat.celBands.toFloat())
        GLES20.glUniform1f(GLES20.glGetUniformLocation(meshProgram, "uEmissive"), mat.emissiveIntensity)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(meshProgram, "uUvTiling"), mat.uvTiling)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(meshProgram, "uUvOffset"), mat.uvOffsetX, mat.uvOffsetY)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(meshProgram, "uAnisotropy"), mat.anisotropy)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(meshProgram, "uCustomGain"), mat.customShaderGain)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(meshProgram, "uShaderMode"), mat.shaderMode.ordinal)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, albedoTexId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(meshProgram, "uAlbedoTex"), 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, normalTexId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(meshProgram, "uNormalTex"), 1)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, pbrTexId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(meshProgram, "uPbrTex"), 2)

        val posLoc = GLES20.glGetAttribLocation(meshProgram, "aPos")
        val normLoc = GLES20.glGetAttribLocation(meshProgram, "aNormal")
        val uvLoc = GLES20.glGetAttribLocation(meshProgram, "aUv")

        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 0, pb)

        GLES20.glEnableVertexAttribArray(normLoc)
        GLES20.glVertexAttribPointer(normLoc, 3, GLES20.GL_FLOAT, false, 0, nb)

        GLES20.glEnableVertexAttribArray(uvLoc)
        GLES20.glVertexAttribPointer(uvLoc, 2, GLES20.GL_FLOAT, false, 0, uvb)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, m.indices.size, GLES20.GL_UNSIGNED_SHORT, ib)

        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(normLoc)
        GLES20.glDisableVertexAttribArray(uvLoc)
    }

    private fun drawSkeletonOverlay(
        bones: List<BoneNode>,
        worldPos: FloatArray,
        clampedIds: Set<String>
    ) {
        val indexMap = HashMap<String, Int>(bones.size)
        bones.forEachIndexed { idx, b -> indexMap[b.id] = idx }

        val lineVerts = ArrayList<Float>(bones.size * 6)
        for (i in bones.indices) {
            val pid = bones[i].parentId ?: continue
            val pIdx = indexMap[pid] ?: continue
            lineVerts.add(worldPos[pIdx * 3]); lineVerts.add(worldPos[pIdx * 3 + 1]); lineVerts.add(worldPos[pIdx * 3 + 2])
            lineVerts.add(worldPos[i * 3]); lineVerts.add(worldPos[i * 3 + 1]); lineVerts.add(worldPos[i * 3 + 2])
        }
        if (lineVerts.isNotEmpty()) {
            drawLineArray(lineVerts.toFloatArray(), 0.22f, 0.74f, 0.97f, 0.92f, 4.0f)
        }

        // Draw joint crosshairs (Amber if selected or clamped, Cyan otherwise)
        val cyanCross = ArrayList<Float>()
        val amberCross = ArrayList<Float>()
        for (i in bones.indices) {
            val b = bones[i]
            val x = worldPos[i * 3]; val y = worldPos[i * 3 + 1]; val z = worldPos[i * 3 + 2]
            val target = if (b.id == selectedBoneId || clampedIds.contains(b.id)) amberCross else cyanCross
            val r = if (b.id == selectedBoneId) 0.048f else 0.025f
            target.add(x - r); target.add(y); target.add(z); target.add(x + r); target.add(y); target.add(z)
            target.add(x); target.add(y - r); target.add(z); target.add(x); target.add(y + r); target.add(z)
            target.add(x); target.add(y); target.add(z - r); target.add(x); target.add(y); target.add(z + r)
        }
        if (cyanCross.isNotEmpty()) drawLineArray(cyanCross.toFloatArray(), 0.22f, 0.74f, 0.97f, 1.0f, 3.5f)
        if (amberCross.isNotEmpty()) drawLineArray(amberCross.toFloatArray(), 0.96f, 0.62f, 0.04f, 1.0f, 5.5f)
    }

    private fun drawConstraintGizmos(
        bones: List<BoneNode>,
        worldPos: FloatArray,
        clampedIds: Set<String>
    ) {
        val targetId = selectedBoneId ?: clampedIds.firstOrNull() ?: return
        val idx = bones.indexOfFirst { it.id == targetId }
        if (idx < 0) return
        val bone = bones[idx]
        if (!bone.constraint.enabled) return

        val cx = worldPos[idx * 3]
        val cy = worldPos[idx * 3 + 1]
        val cz = worldPos[idx * 3 + 2]

        // Draw angular constraint arc for rotation X/Y limits around selected joint
        val arcVerts = ArrayList<Float>()
        val radius = 0.14f
        val minAngle = Math.toRadians(bone.constraint.rotMinX.toDouble()).toFloat()
        val maxAngle = Math.toRadians(bone.constraint.rotMaxX.toDouble()).toFloat()
        val steps = 16
        for (s in 0 until steps) {
            val a0 = minAngle + (maxAngle - minAngle) * (s.toFloat() / steps)
            val a1 = minAngle + (maxAngle - minAngle) * ((s + 1).toFloat() / steps)
            arcVerts.add(cx); arcVerts.add(cy + sin(a0) * radius); arcVerts.add(cz + cos(a0) * radius)
            arcVerts.add(cx); arcVerts.add(cy + sin(a1) * radius); arcVerts.add(cz + cos(a1) * radius)
        }
        drawLineArray(arcVerts.toFloatArray(), 0.96f, 0.62f, 0.04f, 0.95f, 4.0f)
    }

    private fun drawLineArray(verts: FloatArray, r: Float, g: Float, b: Float, a: Float, width: Float) {
        val buf = allocFloatBuffer(verts.size).apply { put(verts); position(0) }
        GLES20.glUseProgram(lineProgram)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(lineProgram, "uMVP"), 1, false, mvpMatrix, 0)
        GLES20.glUniform4f(GLES20.glGetUniformLocation(lineProgram, "uColor"), r, g, b, a)
        val posLoc = GLES20.glGetAttribLocation(lineProgram, "aPos")
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glLineWidth(width)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, verts.size / 3)
        GLES20.glDisableVertexAttribArray(posLoc)
    }

    private fun buildStudioGrid() {
        val verts = ArrayList<Float>()
        val y = -0.68f
        val extent = 3.2f
        val step = 0.4f
        var p = -extent
        while (p <= extent + 0.001f) {
            verts.add(p); verts.add(y); verts.add(-extent)
            verts.add(p); verts.add(y); verts.add(extent)
            verts.add(-extent); verts.add(y); verts.add(p)
            verts.add(extent); verts.add(y); verts.add(p)
            p += step
        }
        // Center X & Z axis highlights
        verts.add(-extent); verts.add(y + 0.002f); verts.add(0f)
        verts.add(extent); verts.add(y + 0.002f); verts.add(0f)
        verts.add(0f); verts.add(y + 0.002f); verts.add(-extent)
        verts.add(0f); verts.add(y + 0.002f); verts.add(extent)

        gridVertexCount = verts.size / 3
        gridVertexBuffer = allocFloatBuffer(verts.size).apply {
            put(verts.toFloatArray())
            position(0)
        }
    }

    private fun allocFloatBuffer(size: Int): FloatBuffer =
        ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    private fun createProgram(vsSource: String, fsSource: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vsSource)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fsSource)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }

    companion object {
        private const val LINE_VERTEX_SHADER = """
            uniform mat4 uMVP;
            attribute vec3 aPos;
            void main() {
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """

        private const val LINE_FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
                gl_FragColor = uColor;
            }
        """

        private const val MESH_VERTEX_SHADER = """
            uniform mat4 uMVP;
            attribute vec3 aPos;
            attribute vec3 aNormal;
            attribute vec2 aUv;
            varying vec3 vWorldPos;
            varying vec3 vNormal;
            varying vec2 vUv;
            void main() {
                vWorldPos = aPos;
                vNormal = aNormal;
                vUv = aUv;
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """

        private const val MESH_FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uAlbedoTex;
            uniform sampler2D uNormalTex;
            uniform sampler2D uPbrTex;
            uniform vec3 uCameraPos;
            uniform float uTime;
            uniform vec3 uAlbedoTint;
            uniform vec3 uRimColor;
            uniform float uExposure;
            uniform float uNormalStrength;
            uniform float uRoughness;
            uniform float uMetallic;
            uniform float uRimPower;
            uniform float uCelBands;
            uniform float uEmissive;
            uniform float uUvTiling;
            uniform vec2 uUvOffset;
            uniform float uAnisotropy;
            uniform float uCustomGain;
            uniform int uShaderMode;

            varying vec3 vWorldPos;
            varying vec3 vNormal;
            varying vec2 vUv;

            void main() {
                vec2 uv = fract(vUv * uUvTiling + uUvOffset);
                vec4 texel = texture2D(uAlbedoTex, uv);
                vec3 baseAlbedo = mix(vec3(0.18, 0.22, 0.30), texel.rgb, step(0.08, texel.a));
                baseAlbedo *= uAlbedoTint * uExposure;

                vec3 normalSample = texture2D(uNormalTex, uv).xyz * 2.0 - 1.0;
                vec3 N = normalize(vNormal + vec3(normalSample.x, normalSample.y, 0.0) * uNormalStrength * 0.65);

                vec4 pbrMap = texture2D(uPbrTex, uv);
                float rough = clamp(mix(uRoughness, pbrMap.g, 0.65), 0.05, 1.0);
                float metal = clamp(mix(uMetallic, pbrMap.b, 0.65), 0.0, 0.98);

                vec3 V = normalize(uCameraPos - vWorldPos);
                vec3 L1 = normalize(vec3(1.8, 2.5, 2.2));
                vec3 L2 = normalize(vec3(-2.0, 0.8, -1.5));
                vec3 H1 = normalize(L1 + V);

                float NdotL = max(dot(N, L1), 0.0);
                float fillNdotL = max(dot(N, L2), 0.0);
                float NdotV = max(dot(N, V), 0.01);
                float fresnel = pow(1.0 - NdotV, clamp(uRimPower, 0.5, 6.0));

                float shininess = mix(128.0, 8.0, rough);
                float spec = pow(max(dot(N, H1), 0.0), shininess) * (1.2 - rough * 0.7);
                vec3 specColor = mix(vec3(0.95), baseAlbedo, metal);

                vec3 color;
                float alpha = 1.0;

                if (uShaderMode == 1) {
                    // TOON_CEL
                    float bands = max(2.0, uCelBands);
                    float cel = floor(NdotL * bands + 0.35) / bands;
                    float rimStep = step(0.55, fresnel);
                    color = baseAlbedo * (0.28 + cel * 0.82) + specColor * step(0.5, spec) * 0.5 + uRimColor * rimStep * 0.45;
                } else if (uShaderMode == 2) {
                    // HOLOGRAM_RIM
                    float scan = 0.68 + 0.32 * sin(vWorldPos.y * 42.0 - uTime * 6.0);
                    vec3 holo = mix(baseAlbedo * 0.55, uRimColor, 0.55 + fresnel * 0.45);
                    color = holo * scan + uRimColor * fresnel * 1.15;
                    alpha = clamp(0.52 + fresnel * 0.45, 0.35, 0.98);
                } else if (uShaderMode == 3) {
                    // IRIDESCENT
                    float shift = NdotV * 4.5 + vWorldPos.y * 1.8;
                    vec3 irid = vec3(0.5 + 0.5 * sin(shift), 0.5 + 0.5 * sin(shift + 2.1), 0.5 + 0.5 * sin(shift + 4.2));
                    float aniso = pow(1.0 - abs(dot(N, normalize(vec3(1.0, 0.2, 0.0)))), 4.0) * uAnisotropy;
                    color = baseAlbedo * (0.25 + NdotL * 0.75) + mix(specColor, irid, 0.65) * (spec + fresnel * 0.6 + aniso);
                } else if (uShaderMode == 4) {
                    // CUSTOM_UNIFORM_LAB
                    float anisoBand = pow(max(0.0, 1.0 - abs(dot(N, normalize(vec3(0.0, 1.0, 0.3))))), 3.0) * uAnisotropy;
                    vec3 diffuse = baseAlbedo * (0.26 + NdotL * 0.85 + fillNdotL * 0.25) * (1.0 - metal * 0.45);
                    color = (diffuse + specColor * (spec + anisoBand) + uRimColor * fresnel * 0.75) * uCustomGain;
                } else {
                    // PBR_STANDARD
                    vec3 diffuse = baseAlbedo * (1.0 - metal * 0.55) * (0.25 + NdotL * 0.82 + fillNdotL * 0.28);
                    color = diffuse + specColor * spec + uRimColor * fresnel * 0.24;
                }

                color += baseAlbedo * uEmissive;
                gl_FragColor = vec4(clamp(color, 0.0, 1.0), alpha);
            }
        """
    }
}
