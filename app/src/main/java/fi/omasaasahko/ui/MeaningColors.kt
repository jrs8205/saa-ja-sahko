package fi.omasaasahko.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal enum class WarmTone { YELLOW, ORANGE, RED }
internal data class MeaningColors(val top: Color, val bottom: Color, val ink: Color, val muted: Color,
                                  val accent: Color, val onAccent: Color)

/** Shared colour tokens for price bands and warning severity, with distinct domain labels. */
@Composable
internal fun warmColors(tone: WarmTone): MeaningColors = if (isDarkTheme()) when (tone) {
    WarmTone.YELLOW -> MeaningColors(Color(0xFF50431A), Color(0xFF302B1D), Color(0xFFFFF3CC), Color(0xFFEADCAC), Color(0xFFFFD873), Color(0xFF302B1D))
    WarmTone.ORANGE -> MeaningColors(Color(0xFF58351F), Color(0xFF352720), Color(0xFFFFEBDC), Color(0xFFF0CBB0), Color(0xFFFFC69D), Color(0xFF3A1D08))
    WarmTone.RED -> MeaningColors(Color(0xFF582731), Color(0xFF34232C), Color(0xFFFFE7EB), Color(0xFFF0C2CA), Color(0xFFFFBCC6), Color(0xFF3D1019))
} else when (tone) {
    WarmTone.YELLOW -> MeaningColors(Color(0xFFFFEDB0), Color(0xFFFFF8DE), Color(0xFF4B3C13), Color(0xFF5B4D24), Color(0xFF5A4400), Color.White)
    WarmTone.ORANGE -> MeaningColors(Color(0xFFFFDEC2), Color(0xFFFFF0E3), Color(0xFF562E16), Color(0xFF673F24), Color(0xFF783300), Color.White)
    WarmTone.RED -> MeaningColors(Color(0xFFFFD9DF), Color(0xFFFFEFF2), Color(0xFF601F30), Color(0xFF723442), Color(0xFF891B32), Color.White)
}
