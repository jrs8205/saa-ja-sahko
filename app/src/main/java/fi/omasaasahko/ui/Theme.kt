package fi.omasaasahko.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF286B5E), onPrimary = Color.White,
    primaryContainer = Color(0xFFD3EDE3), onPrimaryContainer = Color(0xFF113B32),
    secondary = Color(0xFF51665E), secondaryContainer = Color(0xFFE0EAE4),
    tertiary = Color(0xFF896A35), tertiaryContainer = Color(0xFFF7E3BE),
    background = Color(0xFFF5F7F3), surface = Color(0xFFF5F7F3),
    surfaceContainer = Color(0xFFEBEFEB), surfaceContainerLow = Color(0xFFEEF2ED),
    surfaceContainerHigh = Color(0xFFE4EBE4), onSurface = Color(0xFF18221D),
    onSurfaceVariant = Color(0xFF526159), outlineVariant = Color(0xFFD4DDD5),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF9FD5C0), onPrimary = Color(0xFF0C3829),
    primaryContainer = Color(0xFF244E3D), onPrimaryContainer = Color(0xFFD1EFDE),
    secondary = Color(0xFFB7CDC0), secondaryContainer = Color(0xFF31463C),
    tertiary = Color(0xFFE2C38B), tertiaryContainer = Color(0xFF4E4027),
    background = Color(0xFF101713), surface = Color(0xFF101713),
    surfaceContainer = Color(0xFF1C2720), surfaceContainerLow = Color(0xFF172019),
    surfaceContainerHigh = Color(0xFF263129), onSurface = Color(0xFFE0EBE1),
    onSurfaceVariant = Color(0xFFB3C2B6), outlineVariant = Color(0xFF37473C),
)
@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), dynamic: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (dynamic) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) Dark else Light
    MaterialTheme(colorScheme = colors, typography = Typography(
        displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light, fontSize = 64.sp, letterSpacing = (-3).sp),
        headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = (-0.8).sp),
        titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, letterSpacing = (-0.3).sp),
        titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.4.sp),
    ), content = content)
}
