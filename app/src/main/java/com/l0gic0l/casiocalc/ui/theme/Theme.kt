package com.l0gic0l.casiocalc.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF3A5BA9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDAE2FF),
    onPrimaryContainer = Color(0xFF001946),
    secondary = Color(0xFF575E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBE2F9),
    onSecondaryContainer = Color(0xFF141B2C),
    tertiary = Color(0xFF8B5000),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCBE),
    onTertiaryContainer = Color(0xFF2C1600),
    background = Color(0xFFFAF8FF),
    surface = Color(0xFFFAF8FF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB1C5FF),
    onPrimary = Color(0xFF002C70),
    primaryContainer = Color(0xFF1D438F),
    onPrimaryContainer = Color(0xFFDAE2FF),
    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF293041),
    secondaryContainer = Color(0xFF3F4759),
    onSecondaryContainer = Color(0xFFDBE2F9),
    tertiary = Color(0xFFFFB870),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6A3B00),
    onTertiaryContainer = Color(0xFFFFDCBE),
    background = Color(0xFF121318),
    surface = Color(0xFF121318),
)

/** Colours for the calculator-specific labels: SHIFT functions and ALPHA letters. */
@Immutable
data class KeyLabelColors(val shift: Color, val alpha: Color)

val LocalKeyLabelColors = staticCompositionLocalOf { KeyLabelColors(Color(0xFF8B5000), Color(0xFFBA1A1A)) }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun CasioCalcTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    pureBlack: Boolean = false,
    /** Degrees to turn the built-in palette round the colour wheel; not used with wallpaper colours. */
    accentHue: Float = 0f,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors.rotateHue(accentHue)
        else -> LightColors.rotateHue(accentHue)
    }.let { if (dark && pureBlack) it.blackened() else it }
    val labels = if (dark) {
        KeyLabelColors(shift = Color(0xFFFFB870), alpha = Color(0xFFFF8A80))
    } else {
        KeyLabelColors(shift = Color(0xFF9A5A00), alpha = Color(0xFFC62828))
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalKeyLabelColors provides labels) {
        MaterialTheme(colorScheme = animateColorScheme(scheme), content = content)
    }
}

/**
 * This colour turned [degrees] round the colour wheel, keeping its saturation and its luminance.
 * Contrast depends only on luminance, so every pair in the scheme keeps the contrast the blue
 * palette was made with: a teal at the blue's brightness would be far lighter, too light for
 * white text.
 */
private fun Color.turned(degrees: Float): Color {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    if (delta < 1e-4f) return this
    val hue = when (max) {
        red -> 60f * (((green - blue) / delta) % 6f)
        green -> 60f * ((blue - red) / delta + 2f)
        else -> 60f * ((red - green) / delta + 4f)
    }
    val h = ((hue + degrees) % 360f + 360f) % 360f
    val target = luminance()
    fun at(value: Float) = Color.hsv(h, delta / max, value, alpha)
    // A hue darker than this one even at full brightness (a light orange turned blue) is mixed with white instead.
    val brightest = at(1f)
    if (brightest.luminance() < target) return lerp(brightest, Color.White, solve { lerp(brightest, Color.White, it).luminance() < target })
    return at(solve { at(it).luminance() < target })
}

/** The t in [0, 1] where [below] turns false, by bisection; [below] must go from true to false as t grows. */
private inline fun solve(below: (Float) -> Boolean): Float {
    var lo = 0f
    var hi = 1f
    repeat(24) {
        val t = (lo + hi) / 2
        if (below(t)) lo = t else hi = t
    }
    return (lo + hi) / 2
}

/** The primary colour of the built-in palette turned by [hueShift], for colour swatches. */
fun accentSwatch(hueShift: Float): Color = LightColors.primary.turned(hueShift)

/** Every colour of the scheme turned [degrees] round the colour wheel. */
private fun ColorScheme.rotateHue(degrees: Float): ColorScheme {
    if (degrees == 0f) return this
    fun Color.r() = turned(degrees)
    return copy(
        primary = primary.r(), onPrimary = onPrimary.r(), primaryContainer = primaryContainer.r(), onPrimaryContainer = onPrimaryContainer.r(),
        inversePrimary = inversePrimary.r(),
        secondary = secondary.r(), onSecondary = onSecondary.r(), secondaryContainer = secondaryContainer.r(), onSecondaryContainer = onSecondaryContainer.r(),
        tertiary = tertiary.r(), onTertiary = onTertiary.r(), tertiaryContainer = tertiaryContainer.r(), onTertiaryContainer = onTertiaryContainer.r(),
        background = background.r(), onBackground = onBackground.r(), surface = surface.r(), onSurface = onSurface.r(),
        surfaceVariant = surfaceVariant.r(), onSurfaceVariant = onSurfaceVariant.r(), surfaceTint = surfaceTint.r(),
        outline = outline.r(), outlineVariant = outlineVariant.r(),
        surfaceBright = surfaceBright.r(), surfaceDim = surfaceDim.r(),
        surfaceContainerLowest = surfaceContainerLowest.r(), surfaceContainerLow = surfaceContainerLow.r(), surfaceContainer = surfaceContainer.r(),
        surfaceContainerHigh = surfaceContainerHigh.r(), surfaceContainerHighest = surfaceContainerHighest.r(),
    )
}

/** Black background and surfaces, keeping the tonal containers so cards and keys still stand out. */
private fun ColorScheme.blackened() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0D),
    surfaceContainer = Color(0xFF121214),
)

/** Theme changes fade between colours instead of snapping. */
@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    val spec = tween<Color>(400)
    @Composable
    fun a(c: Color) = animateColorAsState(c, spec, label = "scheme").value
    return target.copy(
        primary = a(target.primary),
        onPrimary = a(target.onPrimary),
        primaryContainer = a(target.primaryContainer),
        onPrimaryContainer = a(target.onPrimaryContainer),
        secondary = a(target.secondary),
        onSecondary = a(target.onSecondary),
        secondaryContainer = a(target.secondaryContainer),
        onSecondaryContainer = a(target.onSecondaryContainer),
        tertiary = a(target.tertiary),
        onTertiary = a(target.onTertiary),
        tertiaryContainer = a(target.tertiaryContainer),
        onTertiaryContainer = a(target.onTertiaryContainer),
        background = a(target.background),
        onBackground = a(target.onBackground),
        surface = a(target.surface),
        onSurface = a(target.onSurface),
        surfaceVariant = a(target.surfaceVariant),
        onSurfaceVariant = a(target.onSurfaceVariant),
        outline = a(target.outline),
        outlineVariant = a(target.outlineVariant),
        surfaceContainerLowest = a(target.surfaceContainerLowest),
        surfaceContainerLow = a(target.surfaceContainerLow),
        surfaceContainer = a(target.surfaceContainer),
        surfaceContainerHigh = a(target.surfaceContainerHigh),
        surfaceContainerHighest = a(target.surfaceContainerHighest),
        error = a(target.error),
        errorContainer = a(target.errorContainer),
        onErrorContainer = a(target.onErrorContainer),
    )
}
