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
        dark -> DarkColors
        else -> LightColors
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
