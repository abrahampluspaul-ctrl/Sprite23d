package com.example.engine.mesh

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.example.domain.model.RigPreset

data class StarterSpriteTemplate(
    val id: String,
    val title: String,
    val subtitle: String,
    val defaultPreset: RigPreset
)

object StarterSpriteGenerator {

    val templates = listOf(
        StarterSpriteTemplate(
            id = "vanguard_knight",
            title = "Vanguard Knight",
            subtitle = "Plate armor, crimson cloak & helm plume",
            defaultPreset = RigPreset.UNIVERSAL_HUMANOID
        ),
        StarterSpriteTemplate(
            id = "arcane_mage",
            title = "Arcane Weaver",
            subtitle = "Flowing robe, crystal staff & floating hair",
            defaultPreset = RigPreset.UNITY_MECANIM
        ),
        StarterSpriteTemplate(
            id = "cyber_wolf",
            title = "Cyber Hound",
            subtitle = "Quadruped chassis, glowing visor & tail",
            defaultPreset = RigPreset.QUADRUPED
        )
    )

    fun generateBitmap(templateId: String): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)

        when (templateId) {
            "arcane_mage" -> drawArcaneMage(canvas)
            "cyber_wolf" -> drawCyberWolf(canvas)
            else -> drawVanguardKnight(canvas)
        }
        return bmp
    }

    private fun drawVanguardKnight(c: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Crimson flowing cape behind shoulders
        p.color = Color.rgb(175, 32, 54)
        val cape = Path().apply {
            moveTo(92f, 92f)
            lineTo(164f, 92f)
            lineTo(186f, 206f)
            lineTo(128f, 218f)
            lineTo(70f, 206f)
            close()
        }
        c.drawPath(cape, p)
        p.color = Color.rgb(120, 18, 36)
        c.drawRect(78f, 128f, 96f, 200f, p)
        c.drawRect(160f, 128f, 178f, 200f, p)

        // Helmet plume (hair/plume group)
        p.color = Color.rgb(245, 158, 11)
        val plume = Path().apply {
            moveTo(124f, 22f)
            quadTo(168f, 12f, 172f, 44f)
            quadTo(148f, 52f, 124f, 46f)
            close()
        }
        c.drawPath(plume, p)

        // Legs & Greaves
        p.color = Color.rgb(92, 108, 134)
        c.drawRoundRect(RectF(98f, 162f, 122f, 228f), 8f, 8f, p)
        c.drawRoundRect(RectF(134f, 162f, 158f, 228f), 8f, 8f, p)
        // Boots
        p.color = Color.rgb(56, 68, 88)
        c.drawRoundRect(RectF(94f, 214f, 124f, 236f), 6f, 6f, p)
        c.drawRoundRect(RectF(132f, 214f, 162f, 236f), 6f, 6f, p)

        // Torso breastplate
        p.color = Color.rgb(168, 186, 212)
        c.drawRoundRect(RectF(92f, 88f, 164f, 166f), 14f, 14f, p)
        p.color = Color.rgb(56, 189, 248) // Cyan chest core
        c.drawCircle(128f, 118f, 11f, p)
        p.color = Color.rgb(218, 165, 32) // Belt trim
        c.drawRect(94f, 148f, 162f, 160f, p)

        // Pauldrons & Arms
        p.color = Color.rgb(198, 212, 232)
        c.drawCircle(86f, 98f, 18f, p)
        c.drawCircle(170f, 98f, 18f, p)
        p.color = Color.rgb(125, 142, 168)
        c.drawRoundRect(RectF(68f, 106f, 88f, 168f), 8f, 8f, p)
        c.drawRoundRect(RectF(168f, 106f, 188f, 168f), 8f, 8f, p)

        // Helmet & Visor
        p.color = Color.rgb(208, 220, 238)
        c.drawRoundRect(RectF(100f, 36f, 156f, 86f), 16f, 16f, p)
        p.color = Color.rgb(15, 23, 42)
        c.drawRoundRect(RectF(108f, 54f, 148f, 68f), 4f, 4f, p)
        p.color = Color.rgb(56, 189, 248)
        c.drawCircle(118f, 61f, 3.5f, p)
        c.drawCircle(138f, 61f, 3.5f, p)
    }

    private fun drawArcaneMage(c: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Flowing violet hair & hood
        p.color = Color.rgb(139, 92, 246)
        val hair = Path().apply {
            moveTo(92f, 46f)
            lineTo(164f, 46f)
            lineTo(180f, 128f)
            lineTo(76f, 128f)
            close()
        }
        c.drawPath(hair, p)

        // Wizard hat
        p.color = Color.rgb(49, 46, 129)
        val hat = Path().apply {
            moveTo(128f, 12f)
            lineTo(168f, 54f)
            lineTo(88f, 54f)
            close()
        }
        c.drawPath(hat, p)
        c.drawRoundRect(RectF(78f, 48f, 178f, 60f), 6f, 6f, p)

        // Robe skirt (cloth sim)
        p.color = Color.rgb(67, 56, 202)
        val robe = Path().apply {
            moveTo(96f, 94f)
            lineTo(160f, 94f)
            lineTo(182f, 226f)
            lineTo(74f, 226f)
            close()
        }
        c.drawPath(robe, p)

        // Gold rune trim
        p.color = Color.rgb(251, 191, 36)
        c.drawRect(120f, 96f, 136f, 224f, p)

        // Face
        p.color = Color.rgb(253, 224, 198)
        c.drawCircle(128f, 72f, 20f, p)
        p.color = Color.rgb(30, 41, 59)
        c.drawCircle(121f, 71f, 3f, p)
        c.drawCircle(135f, 71f, 3f, p)

        // Sleeves & Hands
        p.color = Color.rgb(99, 102, 241)
        c.drawRoundRect(RectF(68f, 96f, 94f, 154f), 10f, 10f, p)
        c.drawRoundRect(RectF(162f, 96f, 188f, 154f), 10f, 10f, p)
        p.color = Color.rgb(56, 189, 248)
        c.drawCircle(76f, 160f, 10f, p)
        c.drawCircle(180f, 160f, 10f, p)
    }

    private fun drawCyberWolf(c: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Tail (soft/dynamic appendage)
        p.color = Color.rgb(56, 189, 248)
        val tail = Path().apply {
            moveTo(64f, 118f)
            quadTo(22f, 92f, 28f, 64f)
            quadTo(44f, 84f, 72f, 106f)
            close()
        }
        c.drawPath(tail, p)

        // Hind & Front Legs
        p.color = Color.rgb(51, 65, 85)
        c.drawRoundRect(RectF(72f, 148f, 94f, 214f), 8f, 8f, p)
        c.drawRoundRect(RectF(96f, 152f, 114f, 210f), 8f, 8f, p)
        c.drawRoundRect(RectF(148f, 152f, 166f, 210f), 8f, 8f, p)
        c.drawRoundRect(RectF(168f, 146f, 190f, 214f), 8f, 8f, p)

        // Armored Body
        p.color = Color.rgb(100, 116, 139)
        c.drawRoundRect(RectF(66f, 104f, 192f, 162f), 26f, 26f, p)
        p.color = Color.rgb(56, 189, 248)
        c.drawRoundRect(RectF(98f, 120f, 158f, 132f), 6f, 6f, p)

        // Neck & Wolf Head
        p.color = Color.rgb(148, 163, 184)
        c.drawRoundRect(RectF(162f, 70f, 224f, 118f), 16f, 16f, p)
        // Ears
        val ear1 = Path().apply {
            moveTo(168f, 76f)
            lineTo(176f, 44f)
            lineTo(190f, 74f)
            close()
        }
        c.drawPath(ear1, p)
        // Glowing Visor
        p.color = Color.rgb(245, 158, 11)
        c.drawRoundRect(RectF(192f, 84f, 218f, 94f), 4f, 4f, p)
    }
}
