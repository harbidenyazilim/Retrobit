package com.retrobit.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF08080E)
val InkDeep = Color(0xFF05050A)
val Surface = Color(0xFF12121C)
val SurfaceHigh = Color(0xFF1A1A28)
val Stroke = Color(0xFF2A2A3A)

val Violet = Color(0xFF8B7CFF)
val VioletDeep = Color(0xFF5B4BDB)
val Cyan = Color(0xFF37E6D0)
val Pink = Color(0xFFFF5CA8)
val Amber = Color(0xFFFFC857)
val TextPrimary = Color(0xFFF4F2FF)
val TextSecondary = Color(0xFFA6A3BC)
val TextMuted = Color(0xFF6F6C85)

val HeroGradient = Brush.linearGradient(listOf(Violet, Pink))
val NeonGradient = Brush.linearGradient(listOf(Cyan, Violet))
val ScrimGradient = Brush.verticalGradient(
    0f to Color.Transparent,
    0.55f to Color(0x66000000),
    1f to Color(0xFF0A0A12)
)

fun consoleGradient(color: Color): Brush =
    Brush.linearGradient(listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.45f), Color(0xFF141420)))
