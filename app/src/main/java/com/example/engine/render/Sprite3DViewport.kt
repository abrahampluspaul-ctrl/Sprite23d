package com.example.engine.render

import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.domain.model.AnimationSettings
import com.example.domain.model.BoneNode
import com.example.domain.model.MaterialConfig
import com.example.domain.model.MeshGroupPhysics
import com.example.domain.model.VolumetricMesh
import com.example.engine.physics.PhysicsAndRigSolver

@Composable
fun Sprite3DViewport(
    mesh: VolumetricMesh?,
    spriteBitmap: Bitmap?,
    bones: List<BoneNode>,
    selectedBoneId: String?,
    physicsGroups: List<MeshGroupPhysics>,
    materialConfig: MaterialConfig,
    animationSettings: AnimationSettings,
    joyX: Float,
    joyY: Float,
    resetCameraSignal: Int,
    windGustSignal: Int,
    onClampedBonesUpdated: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val solver = remember { PhysicsAndRigSolver() }
    val renderer = remember {
        Sprite3DRenderer(
            solver = solver,
            onClampedBonesUpdated = onClampedBonesUpdated
        )
    }

    val glSurfaceRef = remember { arrayOfNulls<GLSurfaceView>(1) }

    LaunchedEffect(mesh) {
        renderer.mesh = mesh
    }
    LaunchedEffect(bones) {
        renderer.bones = bones
    }
    LaunchedEffect(selectedBoneId) {
        renderer.selectedBoneId = selectedBoneId
    }
    LaunchedEffect(physicsGroups) {
        renderer.physicsGroups = physicsGroups
    }
    LaunchedEffect(animationSettings) {
        renderer.animSettings = animationSettings
    }
    LaunchedEffect(joyX, joyY) {
        renderer.joyX = joyX
        renderer.joyY = joyY
    }
    LaunchedEffect(spriteBitmap) {
        if (spriteBitmap != null) {
            glSurfaceRef[0]?.queueEvent {
                renderer.setTexturesFromSprite(spriteBitmap, materialConfig)
            } ?: run {
                renderer.setTexturesFromSprite(spriteBitmap, materialConfig)
            }
        }
    }
    LaunchedEffect(materialConfig) {
        val prev = renderer.materialConfig
        renderer.materialConfig = materialConfig
        if (prev.normalStrength != materialConfig.normalStrength ||
            prev.roughness != materialConfig.roughness ||
            prev.metallic != materialConfig.metallic ||
            prev.proceduralTexture != materialConfig.proceduralTexture ||
            prev.proceduralScale != materialConfig.proceduralScale ||
            prev.proceduralBlend != materialConfig.proceduralBlend
        ) {
            renderer.markDetailTexturesDirty()
        }
    }
    LaunchedEffect(resetCameraSignal) {
        if (resetCameraSignal > 0) renderer.resetCamera()
    }
    LaunchedEffect(windGustSignal) {
        if (windGustSignal > 0) solver.triggerWindGust(1.85f)
    }

    val cdViewport = context.getString(R.string.cd_viewport_3d)

    AndroidView(
        modifier = modifier
            .testTag("viewport_3d")
            .semantics { contentDescription = cdViewport },
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(2)
                preserveEGLContextOnPause = true
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                glSurfaceRef[0] = this

                var lastX = 0f
                var lastY = 0f
                var isPinching = false

                val scaleDetector = ScaleGestureDetector(
                    ctx,
                    object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                            isPinching = true
                            return true
                        }

                        override fun onScale(detector: ScaleGestureDetector): Boolean {
                            renderer.cameraDistance =
                                (renderer.cameraDistance / detector.scaleFactor).coerceIn(1.25f, 6.0f)
                            return true
                        }

                        override fun onScaleEnd(detector: ScaleGestureDetector) {
                            isPinching = false
                        }
                    }
                )

                setOnTouchListener { _, event ->
                    scaleDetector.onTouchEvent(event)
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            lastX = event.x
                            lastY = event.y
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (!isPinching && event.pointerCount == 1) {
                                val dx = event.x - lastX
                                val dy = event.y - lastY
                                renderer.cameraYawDeg -= dx * 0.42f
                                renderer.cameraPitchDeg =
                                    (renderer.cameraPitchDeg + dy * 0.35f).coerceIn(-45f, 75f)
                                lastX = event.x
                                lastY = event.y
                            }
                        }
                    }
                    true
                }
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            glSurfaceRef[0]?.onPause()
        }
    }
}
