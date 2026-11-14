package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.GizmoAmber
import com.example.ui.theme.PrecisionCyan
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioTextSecondary
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun OmnidirectionalJoystick(
    joyX: Float,
    joyY: Float,
    onVectorChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 116.dp
) {
    val context = LocalContext.current
    val cdJoystick = context.getString(R.string.cd_joystick)
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    val magnitude = hypot(joyX, joyY).coerceIn(0f, 1f)
    val angleDeg = if (magnitude > 0.05f) {
        ((Math.toDegrees(atan2(joyX.toDouble(), -joyY.toDouble())) + 360.0) % 360.0).roundToInt()
    } else 0

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .testTag("omnidirectional_joystick")
                .semantics { contentDescription = cdJoystick }
                .pointerInput(Unit) {
                    val maxRadius = (this.size.width.toFloat() * 0.36f).coerceAtLeast(20f)
                    val center = Offset(this.size.width / 2f, this.size.height / 2f)
                    detectDragGestures(
                        onDragStart = { startPos ->
                            val delta = startPos - center
                            val dist = hypot(delta.x, delta.y)
                            val clamped = if (dist > maxRadius) delta * (maxRadius / dist) else delta
                            dragOffset = clamped
                            onVectorChanged(clamped.x / maxRadius, clamped.y / maxRadius)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val next = dragOffset + dragAmount
                            val dist = hypot(next.x, next.y)
                            val clamped = if (dist > maxRadius) next * (maxRadius / dist) else next
                            dragOffset = clamped
                            onVectorChanged(clamped.x / maxRadius, clamped.y / maxRadius)
                        },
                        onDragEnd = {
                            dragOffset = Offset.Zero
                            onVectorChanged(0f, 0f)
                        },
                        onDragCancel = {
                            dragOffset = Offset.Zero
                            onVectorChanged(0f, 0f)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val r = this.size.minDimension / 2f
                val center = Offset(this.size.width / 2f, this.size.height / 2f)

                // Dark translucent base disc
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            StudioSurface.copy(alpha = 0.88f),
                            StudioObsidian.copy(alpha = 0.92f)
                        ),
                        center = center,
                        radius = r
                    ),
                    radius = r - 2f,
                    center = center
                )

                // Outer ring
                drawCircle(
                    color = if (magnitude > 0.08f) PrecisionCyan.copy(alpha = 0.7f) else Color(0xFF334155),
                    radius = r - 3f,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Walk threshold ring (50%)
                drawCircle(
                    color = Color(0xFF334155).copy(alpha = 0.55f),
                    radius = (r - 6f) * 0.50f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )

                // 8 directional ticks
                for (i in 0 until 8) {
                    val a = i * (PI / 4.0)
                    val inner = r - 9.dp.toPx()
                    val outer = r - 3.dp.toPx()
                    drawLine(
                        color = Color(0xFF475569),
                        start = Offset(
                            center.x + (cos(a) * inner).toFloat(),
                            center.y + (sin(a) * inner).toFloat()
                        ),
                        end = Offset(
                            center.x + (cos(a) * outer).toFloat(),
                            center.y + (sin(a) * outer).toFloat()
                        ),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }

                // Thumbstick offset (reflects either touch or hardware gamepad input!)
                val maxTravel = r * 0.62f
                val thumbCenter = Offset(
                    center.x + joyX * maxTravel,
                    center.y + joyY * maxTravel
                )

                if (magnitude > 0.05f) {
                    drawLine(
                        color = if (magnitude > 0.75f) GizmoAmber else PrecisionCyan,
                        start = center,
                        end = thumbCenter,
                        strokeWidth = 3.dp.toPx()
                    )
                }

                // Thumbstick puck
                drawCircle(
                    color = if (magnitude > 0.75f) GizmoAmber else PrecisionCyan,
                    radius = 17.dp.toPx(),
                    center = thumbCenter
                )
                drawCircle(
                    color = StudioObsidian,
                    radius = 7.dp.toPx(),
                    center = thumbCenter
                )
            }
        }

        Text(
            text = if (magnitude > 0.05f) "${angleDeg}° • ${(magnitude * 100).roundToInt()}%" else "STICK 360°",
            style = MaterialTheme.typography.labelSmall,
            color = if (magnitude > 0.05f) PrecisionCyan else StudioTextSecondary,
            modifier = Modifier
                .padding(top = 4.dp)
                .background(StudioObsidian.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                .border(0.5.dp, Color(0xFF2A3447), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
