package fi.omasaasahko.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import fi.omasaasahko.domain.WarningLevel

data class WarningColors(val container: Color, val ink: Color, val muted: Color, val accent: Color, val onAccent: Color)

/** Warning levels keep fixed colours in both themes, like price bands. */
@Composable
fun warningColors(level: WarningLevel): WarningColors = if (isDarkTheme()) when (level) {
    WarningLevel.YELLOW -> WarningColors(Color(0xFF50431A), Color(0xFFFFF3CC), Color(0xFFE9D9A6), Color(0xFFFFD873), Color(0xFF302B1D))
    WarningLevel.ORANGE -> WarningColors(Color(0xFF58351F), Color(0xFFFFEBDC), Color(0xFFF0CBB0), Color(0xFFFFB27C), Color(0xFF3A1D08))
    WarningLevel.RED -> WarningColors(Color(0xFF582731), Color(0xFFFFE7EB), Color(0xFFF0C2CA), Color(0xFFFF9CAC), Color(0xFF3D1019))
} else when (level) {
    WarningLevel.YELLOW -> WarningColors(Color(0xFFFFEDB0), Color(0xFF4B3C13), Color(0xFF6B5A2A), Color(0xFF735700), Color.White)
    WarningLevel.ORANGE -> WarningColors(Color(0xFFFFDEC2), Color(0xFF562E16), Color(0xFF7A4A2A), Color(0xFF964100), Color.White)
    WarningLevel.RED -> WarningColors(Color(0xFFFFD9DF), Color(0xFF601F30), Color(0xFF803A4A), Color(0xFFA7213D), Color.White)
}
