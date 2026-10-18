package com.l0gic0l.casiocalc.ui.keypad

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.l0gic0l.casiocalc.data.KeyShape
import com.l0gic0l.casiocalc.data.UiPrefs

/** How the keypad looks, from Settings. Provided once for the whole app. */
@Immutable
data class KeypadLook(
    val shape: KeyShape = KeyShape.ROUNDED,
    val spacing: Dp = 6.dp,
    val labelScale: Float = 1f,
    val heightFactor: Float = 1f,
) {
    companion object {
        fun of(p: UiPrefs) = KeypadLook(p.keyShape, p.keySpacing.dp.dp, p.keyLabels.factor, p.keypadSize.factor)
    }
}

val LocalKeypadLook = staticCompositionLocalOf { KeypadLook() }
