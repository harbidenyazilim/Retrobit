package com.retrobit.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrobit.app.ui.theme.AudiowideFont
import com.retrobit.app.ui.theme.BungeeFont
import com.retrobit.app.ui.theme.Display
import com.retrobit.app.ui.theme.LilitaFont
import com.retrobit.app.ui.theme.MichromaFont
import com.retrobit.app.ui.theme.PixelFont
import com.retrobit.app.ui.theme.RussoFont
import com.retrobit.app.ui.theme.TechFont

/*
 * Original, type-driven system marks. Each one borrows only the era's general feel (pixel type,
 * 3D extrusion, chrome, LCD, neon) from OFL fonts and simple shapes; no manufacturer logo,
 * emblem or trade dress is redrawn.
 */

@Composable
internal fun RetroConsoleMark(
    id: String?,
    fallback: String,
    accent: Color,
    size: TextUnit,
    showStrip: Boolean,
    alignEnd: Boolean,
    modifier: Modifier
) {
    val em = with(LocalDensity.current) { size.toDp() }
    // Only the large hero mark animates; carousel panels and headers stay static.
    val live = size.value >= 40f
    Column(
        modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(em * 0.16f)
    ) {
        when (id) {
            "nes" -> NesMark(size, em, showStrip)
            "snes" -> SnesMark(size, em, showStrip)
            "n64" -> N64Mark(size, em, showStrip)
            "gb" -> GameBoyMark(size, em, showStrip)
            "gbc" -> GameBoyColorMark(size, em, showStrip)
            "gba" -> AdvanceMark(size, em, showStrip)
            "nds" -> DualMark(size, em, showStrip)
            "genesis" -> MegaMark(size, em, showStrip)
            "ps1" -> DiscMark(size, em, showStrip, live)
            "psp" -> PortableMark(size, em, showStrip)
            "atari2600" -> AtariMark(size, em, showStrip)
            else -> Line(
                fallback,
                base(TechFont, size, 0.04f, FontWeight.Black).copy(
                    brush = Brush.verticalGradient(
                        0f to Color.White, 0.5f to Color(0xFFE5E7EB), 0.52f to accent, 1f to accent
                    ),
                    shadow = Shadow(accent.copy(alpha = 0.7f), Offset.Zero, 26f)
                )
            )
        }
    }
}

private fun base(
    font: FontFamily,
    size: TextUnit,
    spacing: Float = 0f,
    weight: FontWeight? = null,
    skew: Float = 0f,
    color: Color = Color.White
) = TextStyle(
    fontFamily = font,
    fontWeight = weight,
    fontSize = size,
    lineHeight = size * 1.12f,
    letterSpacing = size * spacing,
    color = color,
    textGeometricTransform = if (skew != 0f) TextGeometricTransform(skewX = skew) else null
)

@Composable
private fun Line(text: String, style: TextStyle, modifier: Modifier = Modifier) =
    Text(text, style = style, maxLines = 1, softWrap = false, modifier = modifier)

/** Hard-offset copies drawn behind the face: drop shadows and stepped 3D extrusions. */
@Composable
private fun Layered(text: String, face: TextStyle, layers: List<Triple<Dp, Dp, Color>>) {
    Box {
        layers.forEach { (x, y, c) ->
            Line(text, face.copy(color = c, shadow = null), Modifier.offset(x, y))
        }
        Line(text, face)
    }
}

private fun tagSize(size: TextUnit) = (size.value * 0.17f).coerceAtLeast(7f).sp

@Composable
private fun Tag(
    text: String,
    size: TextUnit,
    bg: Color,
    fg: Color,
    font: FontFamily = PixelFont,
    shape: Shape = RectangleShape,
    outline: Color? = null
) {
    val fontSize = tagSize(size)
    val lineHeight = fontSize * 1.4f
    val em = with(LocalDensity.current) { size.toDp() }
    val horizontalPadding = if (shape === Parallelogram) {
        (with(LocalDensity.current) { lineHeight.toDp() } + em * 0.14f) * 0.6f + 1.dp
    } else em * 0.16f
    Box(
        Modifier
            .clip(shape)
            .background(bg)
            .then(if (outline != null) Modifier.border(1.dp, outline, shape) else Modifier)
            .padding(horizontal = horizontalPadding, vertical = em * 0.07f)
    ) {
        Text(
            text,
            fontFamily = font,
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacing = fontSize * 0.08f,
            color = fg,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun loop(live: Boolean, millis: Int, label: String): State<Float> =
    if (live) {
        rememberInfiniteTransition(label).animateFloat(
            0f, 1f, infiniteRepeatable(tween(millis, easing = LinearEasing)), label
        )
    } else {
        remember { mutableFloatStateOf(0.125f) }
    }

private val Parallelogram = GenericShape { s, _ ->
    val lean = s.height * 0.6f
    moveTo(lean, 0f)
    lineTo(s.width, 0f)
    lineTo(s.width - lean, s.height)
    lineTo(0f, s.height)
    close()
}

// 8-bit: chunky pixel type, hard black drop and the grey stripe band of a late-80s front panel.
@Composable
private fun NesMark(size: TextUnit, em: Dp, strip: Boolean) {
    Layered(
        "NES",
        base(PixelFont, size * 0.7f, 0.04f, color = Color(0xFFE4572E)),
        listOf(
            Triple(em * 0.09f, em * 0.09f, Color.Black),
            Triple(em * 0.045f, em * 0.045f, Color(0xFF7A1E0E))
        )
    )
    if (strip) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tag("8-BIT", size, Color(0xFF1F1F1F), Color(0xFFD4D4D4))
            Spacer(Modifier.width(em * 0.2f))
            Column(verticalArrangement = Arrangement.spacedBy(em * 0.05f)) {
                repeat(3) {
                    Box(Modifier.width(em * 1.6f).height(em * 0.05f).background(Color(0xFFBDBDBD).copy(alpha = 0.85f - it * 0.2f)))
                }
            }
        }
    }
}

// 16-bit: rounded, friendly lavender-to-purple face with a soft stepped extrusion.
@Composable
private fun SnesMark(size: TextUnit, em: Dp, strip: Boolean) {
    Layered(
        "SNES",
        base(LilitaFont, size * 1.02f, 0.03f).copy(
            brush = Brush.verticalGradient(
                0f to Color(0xFFF5F3FF), 0.45f to Color(0xFFC4B5FD), 1f to Color(0xFF7C5CC4)
            )
        ),
        listOf(
            Triple(em * 0.03f, em * 0.12f, Color.Black.copy(alpha = 0.55f)),
            Triple(0.dp, em * 0.08f, Color(0xFF2E1A5E)),
            Triple(0.dp, em * 0.04f, Color(0xFF4C2F8F))
        )
    )
    if (strip) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(em * 0.12f)) {
            Tag("16-BIT", size, Color(0xFFC4B5FD), Color(0xFF2E1A5E), LilitaFont, CircleShape)
            Box(Modifier.size(em * 0.5f, em * 0.18f).background(Color(0xFF7C5CC4), CircleShape))
            Box(Modifier.size(em * 0.5f, em * 0.18f).background(Color(0xFFDDD6FE), CircleShape))
        }
    }
}

// 64-bit: blocky type pushed out into a stepped multi-colour 3D extrusion.
@Composable
private fun N64Mark(size: TextUnit, em: Dp, strip: Boolean) {
    Layered(
        "N64",
        base(BungeeFont, size * 0.92f, 0.02f).copy(
            brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFFDE68A)))
        ),
        listOf(
            Triple(em * 0.16f, em * 0.16f, Color.Black.copy(alpha = 0.5f)),
            Triple(em * 0.12f, em * 0.12f, Color(0xFF2563EB)),
            Triple(em * 0.08f, em * 0.08f, Color(0xFF16A34A)),
            Triple(em * 0.04f, em * 0.04f, Color(0xFFDC2626))
        )
    )
    if (strip) Tag("64-BIT", size, Color(0xFFEAB308), Color(0xFF111111), BungeeFont, RoundedCornerShape(3.dp))
}

// Late-70s type only: warm cream letters, amber numerals and an understated wood/scan band.
@Composable
private fun AtariMark(size: TextUnit, em: Dp, strip: Boolean) {
    val amber = Color(0xFFD58A43)
    Row(verticalAlignment = Alignment.Bottom) {
        Line(
            "ATARI",
            base(TechFont, size * 0.64f, 0.08f, FontWeight.Bold, color = Color(0xFFE8D6B5))
                .copy(shadow = Shadow(Color(0xFF352319), Offset(2f, 2f), 0f))
        )
        Spacer(Modifier.width(em * 0.16f))
        Line("2600", base(TechFont, size * 0.64f, 0.04f, FontWeight.Bold, color = amber))
    }
    if (strip) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tag("1977", size, Color(0xFF352319), Color(0xFFE8D6B5), TechFont)
            Spacer(Modifier.width(em * 0.16f))
            Column(verticalArrangement = Arrangement.spacedBy(em * 0.045f)) {
                listOf(1.8f, 1.35f, 0.9f).forEachIndexed { index, width ->
                    Box(
                        Modifier.width(em * width).height(em * 0.035f)
                            .background(amber.copy(alpha = 0.7f - index * 0.2f))
                    )
                }
            }
        }
    }
}

// Monochrome handheld: pixel lettering with hard LCD-green offsets and a four-shade strip.
@Composable
private fun GameBoyMark(size: TextUnit, em: Dp, strip: Boolean) {
    val shades = listOf(Color(0xFF9BBC0F), Color(0xFF8BAC0F), Color(0xFF306230), Color(0xFF0F380F))
    Layered(
        "GAME BOY",
        base(PixelFont, size * 0.48f, 0.03f, color = shades[0]),
        listOf(
            Triple(em * 0.06f, em * 0.06f, Color.Black),
            Triple(em * 0.03f, em * 0.03f, shades[2])
        )
    )
    if (strip) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(em * 0.1f)) {
            Tag("DOT MATRIX", size, shades[3], shades[0])
            shades.forEach { shade ->
                Box(Modifier.size(em * 0.22f).background(shade))
            }
        }
    }
}

// Colour handheld: clean blue-to-teal-to-purple type above playful, individually tinted letters.
@Composable
private fun GameBoyColorMark(size: TextUnit, em: Dp, strip: Boolean) {
    val colors = listOf(Color(0xFF2563EB), Color(0xFF2DD4BF), Color(0xFFA855F7))
    Column(verticalArrangement = Arrangement.spacedBy(em * 0.04f)) {
        Line(
            "GAME BOY",
            base(TechFont, size * 0.62f, 0.06f, FontWeight.Black).copy(
                brush = Brush.horizontalGradient(colors),
                shadow = Shadow(Color.Black.copy(alpha = 0.65f), Offset(2f, 2f), 0f)
            )
        )
        Row(horizontalArrangement = Arrangement.spacedBy(em * 0.06f)) {
            "COLOR".forEachIndexed { index, letter ->
                Layered(
                    letter.toString(),
                    base(LilitaFont, size * 0.62f, color = colors[index % colors.size]),
                    listOf(Triple(em * 0.025f, em * 0.04f, Color(0xFF172554)))
                )
            }
        }
    }
    if (strip) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(em * 0.12f)) {
            Tag("1998", size, Color.Transparent, colors[1], LilitaFont, CircleShape, colors[1])
            colors.forEach { color ->
                Box(Modifier.size(em * 0.45f, em * 0.12f).background(color, RoundedCornerShape(2.dp)))
            }
        }
    }
}

// 32-bit handheld: italic tech type in an indigo-to-violet sheen, trailing speed lines.
@Composable
private fun AdvanceMark(size: TextUnit, em: Dp, strip: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(em * 0.08f)) {
            listOf(0.8f, 0.55f, 0.3f).forEachIndexed { i, w ->
                Box(
                    Modifier.width(em * w).height(em * 0.07f)
                        .background(Color(0xFFA78BFA).copy(alpha = 0.9f - i * 0.25f), Parallelogram)
                )
            }
        }
        Spacer(Modifier.width(em * 0.14f))
        Line(
            "GBA",
            base(AudiowideFont, size * 0.98f, 0.05f, skew = -0.22f).copy(
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFF818CF8), Color(0xFFC084FC), Color(0xFFF0ABFC))
                ),
                shadow = Shadow(Color(0xFF7C3AED).copy(alpha = 0.85f), Offset.Zero, 24f)
            )
        )
    }
    if (strip) Tag("32-BIT", size, Color(0xFF4338CA), Color(0xFFE0E7FF), AudiowideFont, Parallelogram)
}

// Dual-screen handheld: two stacked screen frames beside a clean, two-tone name.
@Composable
private fun DualMark(size: TextUnit, em: Dp, strip: Boolean) {
    val sky = Color(0xFF38BDF8)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(em * 0.07f)) {
            Box(
                Modifier.size(em * 0.6f, em * 0.4f)
                    .clip(RoundedCornerShape(em * 0.06f))
                    .background(sky.copy(alpha = 0.45f))
                    .border(2.dp, sky, RoundedCornerShape(em * 0.06f))
            )
            Box(
                Modifier.size(em * 0.6f, em * 0.4f)
                    .clip(RoundedCornerShape(em * 0.06f))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(2.dp, Color(0xFFE2E8F0), RoundedCornerShape(em * 0.06f))
            )
        }
        Spacer(Modifier.width(em * 0.2f))
        Row {
            val face = base(Display, size, 0.06f, FontWeight.Bold)
                .copy(shadow = Shadow(sky.copy(alpha = 0.6f), Offset.Zero, 20f))
            Line("D", face)
            Line("S", face.copy(color = sky))
        }
    }
    if (strip) Tag("DUAL SCREEN", size, Color.Transparent, sky, PixelFont, CircleShape, sky)
}

// 16-bit attitude: slanted heavy type with a chrome horizon split and red slash marks.
@Composable
private fun MegaMark(size: TextUnit, em: Dp, strip: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Layered(
            "SEGA",
            base(RussoFont, size * 0.8f, 0.03f, skew = -0.18f).copy(
                brush = Brush.verticalGradient(
                    0f to Color.White, 0.46f to Color(0xFFCBD5E1),
                    0.5f to Color(0xFF1D4ED8), 1f to Color(0xFF7DD3FC)
                )
            ),
            listOf(
                Triple(em * 0.06f, em * 0.07f, Color.Black),
                Triple(em * 0.03f, em * 0.035f, Color(0xFFDC2626))
            )
        )
        Spacer(Modifier.width(em * 0.14f))
        Row(horizontalArrangement = Arrangement.spacedBy(em * 0.04f)) {
            repeat(3) {
                Box(
                    Modifier.size(em * 0.2f, em * 0.5f)
                        .background(Color(0xFFDC2626).copy(alpha = 1f - it * 0.3f), Parallelogram)
                )
            }
        }
    }
    if (strip) Tag("MD · SMS · GG", size, Color(0xFFDC2626), Color.White, RussoFont, Parallelogram)
}

// CD era: an iridescent compact-disc sheen across both the disc glyph and the name.
@Composable
private fun DiscMark(size: TextUnit, em: Dp, strip: Boolean, live: Boolean) {
    val sheen = listOf(
        Color(0xFFE2E8F0), Color(0xFFA5F3FC), Color(0xFFFBCFE8),
        Color(0xFFFDE68A), Color(0xFFC4B5FD), Color(0xFFE2E8F0)
    )
    val phase = loop(live, 9000, "discSpin")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(em * 0.86f)) {
            val r = this.size.minDimension / 2f
            rotate(phase.value * 360f) { drawCircle(Brush.sweepGradient(sheen, center), r) }
            drawCircle(Color.White.copy(alpha = 0.35f), r, style = Stroke(r * 0.06f))
            drawCircle(Color(0xFF94A3B8), r * 0.3f)
            drawCircle(RetroInk, r * 0.16f)
        }
        Spacer(Modifier.width(em * 0.2f))
        Line(
            "PS1",
            base(TechFont, size * 0.96f, 0.05f, FontWeight.Black).copy(
                brush = Brush.linearGradient(sheen),
                shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 4f), 10f)
            )
        )
    }
    if (strip) Tag("32-BIT CD", size, Color(0xFF334155), Color(0xFFE2E8F0))
}

// Glossy mid-2000s: wide-tracked silver type over a teal line and a faint floor reflection.
@Composable
private fun PortableMark(size: TextUnit, em: Dp, strip: Boolean) {
    val teal = Color(0xFF2DD4BF)
    val face = base(MichromaFont, size * 0.78f, 0.34f).copy(
        brush = Brush.verticalGradient(listOf(Color.White, Color(0xFF94A3B8))),
        shadow = Shadow(teal.copy(alpha = 0.55f), Offset.Zero, 18f)
    )
    Column(Modifier.width(IntrinsicSize.Max)) {
        Line("PSP", face)
        Box(
            Modifier.fillMaxWidth().height(2.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, teal, Color.Transparent)))
        )
        if (strip) {
            Box(Modifier.height(em * 0.4f).clipToBounds()) {
                Line("PSP", face.copy(shadow = null), Modifier.graphicsLayer { scaleY = -1f; alpha = 0.18f })
            }
        }
    }
}
