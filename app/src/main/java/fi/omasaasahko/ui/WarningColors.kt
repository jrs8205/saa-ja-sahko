package fi.omasaasahko.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import fi.omasaasahko.domain.WarningLevel

data class WarningColors(val container: Color, val ink: Color, val muted: Color, val accent: Color, val onAccent: Color,
                         val pill: Color)

/** Warning levels keep fixed colours in both themes, like price bands. */
@Composable
fun warningColors(level: WarningLevel): WarningColors {
    val colors = warmColors(when (level) {
        WarningLevel.YELLOW -> WarmTone.YELLOW
        WarningLevel.ORANGE -> WarmTone.ORANGE
        WarningLevel.RED -> WarmTone.RED
    })
    return WarningColors(colors.top, colors.ink, colors.muted, colors.accent, colors.onAccent, colors.bottom)
}
