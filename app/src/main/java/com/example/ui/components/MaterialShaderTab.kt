package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.MaterialConfig
import com.example.domain.model.ProceduralTextureType
import com.example.domain.model.ShaderMode
import com.example.ui.theme.PrecisionCyan
import com.example.ui.theme.ShaderViolet
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextSecondary
import java.util.Locale

private val ALBEDO_SWATCHES = listOf(
    0xFFFFFFFFL,
    0xFFE2E8F0L,
    0xFFFDE68AL,
    0xFF93C5FDL,
    0xFFFCA5A5L,
    0xFFC4B5FDL,
    0xFF6EE7B7L
)

private val RIM_SWATCHES = listOf(
    0xFF38BDF8L,
    0xFFA78BFAL,
    0xFFF59E0BL,
    0xFF34D399L,
    0xFFFB7185L,
    0xFFFFFFFFL
)

@Composable
fun MaterialShaderTab(
    material: MaterialConfig,
    onUpdateMaterial: ((MaterialConfig) -> MaterialConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Custom Shader Pipeline Selector
        StudioSectionHeader(
            title = "Custom Shader Pipeline",
            badge = material.shaderMode.badge,
            accent = ShaderViolet
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShaderMode.entries.forEach { mode ->
                StudioChoiceChip(
                    label = mode.displayName,
                    selected = material.shaderMode == mode,
                    onClick = { onUpdateMaterial { it.copy(shaderMode = mode) } },
                    tag = "shader_mode_${mode.name.lowercase()}",
                    accent = ShaderViolet,
                    subBadge = mode.badge
                )
            }
        }

        // 2. Texture Maps (Albedo, Normal, Roughness, Metallic)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioSurfaceElevated, RoundedCornerShape(14.dp))
                .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StudioSectionHeader(title = "PBR Texture Maps", badge = "LIVE GLSL")

            Text(
                text = "Albedo Tint",
                style = MaterialTheme.typography.bodyMedium,
                color = StudioTextSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ALBEDO_SWATCHES.forEachIndexed { idx, hex ->
                    ColorSwatchCircle(
                        hex = hex,
                        selected = material.albedoTintHex == hex,
                        onClick = { onUpdateMaterial { it.copy(albedoTintHex = hex) } },
                        tag = "albedo_swatch_$idx"
                    )
                }
            }

            StudioSliderRow(
                label = "Albedo Exposure",
                valueText = String.format(Locale.US, "%.2f", material.exposure),
                value = material.exposure,
                valueRange = 0.4f..1.8f,
                onValueChange = { v -> onUpdateMaterial { it.copy(exposure = v) } },
                tag = "slider_exposure"
            )
            StudioSliderRow(
                label = "Normal Map Strength",
                valueText = String.format(Locale.US, "%.0f%%", material.normalStrength * 100f),
                value = material.normalStrength,
                valueRange = 0f..2.0f,
                onValueChange = { v -> onUpdateMaterial { it.copy(normalStrength = v) } },
                tag = "slider_normal_strength"
            )
            StudioSliderRow(
                label = "Roughness Map",
                valueText = String.format(Locale.US, "%.2f", material.roughness),
                value = material.roughness,
                valueRange = 0.05f..1.0f,
                onValueChange = { v -> onUpdateMaterial { it.copy(roughness = v) } },
                tag = "slider_roughness"
            )
            StudioSliderRow(
                label = "Metallic Map",
                valueText = String.format(Locale.US, "%.2f", material.metallic),
                value = material.metallic,
                valueRange = 0.0f..1.0f,
                onValueChange = { v -> onUpdateMaterial { it.copy(metallic = v) } },
                tag = "slider_metallic"
            )
        }

        // 3. Procedural Texture Generator
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioSurfaceElevated, RoundedCornerShape(14.dp))
                .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StudioSectionHeader(
                title = "Procedural Texture Generator",
                badge = material.proceduralTexture.displayName,
                accent = PrecisionCyan
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProceduralTextureType.entries.forEach { proc ->
                    StudioChoiceChip(
                        label = proc.displayName,
                        selected = material.proceduralTexture == proc,
                        onClick = { onUpdateMaterial { it.copy(proceduralTexture = proc) } },
                        tag = "proc_tex_${proc.name.lowercase()}"
                    )
                }
            }
            if (material.proceduralTexture != ProceduralTextureType.NONE) {
                StudioSliderRow(
                    label = "Procedural Frequency Scale",
                    valueText = String.format(Locale.US, "%.1fx", material.proceduralScale),
                    value = material.proceduralScale,
                    valueRange = 1f..16f,
                    onValueChange = { v -> onUpdateMaterial { it.copy(proceduralScale = v) } },
                    tag = "slider_proc_scale"
                )
                StudioSliderRow(
                    label = "Surface Detail Blend",
                    valueText = String.format(Locale.US, "%.0f%%", material.proceduralBlend * 100f),
                    value = material.proceduralBlend,
                    valueRange = 0f..1f,
                    onValueChange = { v -> onUpdateMaterial { it.copy(proceduralBlend = v) } },
                    tag = "slider_proc_blend"
                )
            }
        }

        // 4. Custom Shader Uniform Lab
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioSurfaceElevated, RoundedCornerShape(14.dp))
                .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StudioSectionHeader(
                title = "Custom Shader Uniforms",
                badge = "REAL-TIME",
                accent = ShaderViolet
            )

            Text(
                text = "Rim / Fresnel Glow Color",
                style = MaterialTheme.typography.bodyMedium,
                color = StudioTextSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RIM_SWATCHES.forEachIndexed { idx, hex ->
                    ColorSwatchCircle(
                        hex = hex,
                        selected = material.rimColorHex == hex,
                        onClick = { onUpdateMaterial { it.copy(rimColorHex = hex) } },
                        tag = "rim_swatch_$idx"
                    )
                }
            }

            StudioSliderRow(
                label = "Fresnel Rim Power",
                valueText = String.format(Locale.US, "%.1f", material.rimPower),
                value = material.rimPower,
                valueRange = 0.6f..5.5f,
                onValueChange = { v -> onUpdateMaterial { it.copy(rimPower = v) } },
                tag = "slider_rim_power",
                accent = ShaderViolet
            )
            StudioSliderRow(
                label = "Emissive Self-Glow",
                valueText = String.format(Locale.US, "%.0f%%", material.emissiveIntensity * 100f),
                value = material.emissiveIntensity,
                valueRange = 0f..0.85f,
                onValueChange = { v -> onUpdateMaterial { it.copy(emissiveIntensity = v) } },
                tag = "slider_emissive",
                accent = ShaderViolet
            )
            if (material.shaderMode == ShaderMode.TOON_CEL) {
                StudioSliderRow(
                    label = "Cel Shading Bands",
                    valueText = "${material.celBands}",
                    value = material.celBands.toFloat(),
                    valueRange = 2f..8f,
                    steps = 5,
                    onValueChange = { v -> onUpdateMaterial { it.copy(celBands = v.toInt()) } },
                    tag = "slider_cel_bands",
                    accent = ShaderViolet
                )
            }
            if (material.shaderMode == ShaderMode.IRIDESCENT || material.shaderMode == ShaderMode.CUSTOM_UNIFORM_LAB) {
                StudioSliderRow(
                    label = "Anisotropic Specular",
                    valueText = String.format(Locale.US, "%.2f", material.anisotropy),
                    value = material.anisotropy,
                    valueRange = 0f..1f,
                    onValueChange = { v -> onUpdateMaterial { it.copy(anisotropy = v) } },
                    tag = "slider_anisotropy",
                    accent = ShaderViolet
                )
            }
            if (material.shaderMode == ShaderMode.CUSTOM_UNIFORM_LAB) {
                StudioSliderRow(
                    label = "Shader Output Gain",
                    valueText = String.format(Locale.US, "%.2fx", material.customShaderGain),
                    value = material.customShaderGain,
                    valueRange = 0.5f..1.8f,
                    onValueChange = { v -> onUpdateMaterial { it.copy(customShaderGain = v) } },
                    tag = "slider_custom_gain",
                    accent = ShaderViolet
                )
            }
            StudioSliderRow(
                label = "UV Map Tiling",
                valueText = String.format(Locale.US, "%.2fx", material.uvTiling),
                value = material.uvTiling,
                valueRange = 0.5f..3.0f,
                onValueChange = { v -> onUpdateMaterial { it.copy(uvTiling = v) } },
                tag = "slider_uv_tiling",
                accent = ShaderViolet
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ColorSwatchCircle(
    hex: Long,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(34.dp)
            .clip(CircleShape)
            .background(Color(hex))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) PrecisionCyan else StudioBorder,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {}
}
