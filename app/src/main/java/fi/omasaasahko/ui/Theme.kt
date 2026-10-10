package fi.omasaasahko.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import fi.omasaasahko.R
import fi.omasaasahko.domain.AppLanguage
import fi.omasaasahko.of

private val Light = lightColorScheme(
    primary = Color(0xFF1D4D44), onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F0DF), onPrimaryContainer = Color(0xFF113B32),
    secondary = Color(0xFF51665E), secondaryContainer = Color(0xFFE0EAE4),
    tertiary = Color(0xFF896A35), tertiaryContainer = Color(0xFFF7E3BE),
    background = Color(0xFFF3F5EF), surface = Color(0xFFF3F5EF),
    surfaceContainer = Color(0xFFEBEFEB), surfaceContainerLow = Color(0xFFEEF2ED),
    surfaceContainerHigh = Color(0xFFE4EBE4), surfaceContainerHighest = Color(0xFFDDE5DE),
    onSurface = Color(0xFF18221D), onSurfaceVariant = Color(0xFF3F4A44), outlineVariant = Color(0xFFD4DDD5),
    onSecondaryContainer = Color(0xFF13251D), error = Color(0xFF881D16), onError = Color.White,
    inverseSurface = Color(0xFF18221D), inverseOnSurface = Color(0xFFF3F5EF),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFA5D8C4), onPrimary = Color(0xFF0C3829),
    primaryContainer = Color(0xFF244E3D), onPrimaryContainer = Color(0xFFD1EFDE),
    secondary = Color(0xFFB7CDC0), secondaryContainer = Color(0xFF31463C),
    tertiary = Color(0xFFE2C38B), tertiaryContainer = Color(0xFF4E4027),
    background = Color(0xFF101713), surface = Color(0xFF101713),
    surfaceContainer = Color(0xFF1C2720), surfaceContainerLow = Color(0xFF172019),
    surfaceContainerHigh = Color(0xFF263129), surfaceContainerHighest = Color(0xFF313D34),
    onSurface = Color(0xFFE0EBE1), onSurfaceVariant = Color(0xFFC4D0C6), outlineVariant = Color(0xFF37473C),
    onSecondaryContainer = Color(0xFFDDEBE2), error = Color(0xFFF4C0BE), onError = Color(0xFF4A1210),
    inverseSurface = Color(0xFFE0EBE1), inverseOnSurface = Color(0xFF101713),
)

@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: FontWeight, opticalSize: Float? = null) = Font(
    resId, weight,
    variationSettings = FontVariation.Settings(*listOfNotNull(
        FontVariation.weight(weight.weight),
        opticalSize?.let { FontVariation.Setting("opsz", it) },
    ).toTypedArray()),
)

/** Numbers and headings. One weight on purpose: the display face is always ExtraBold. */
internal val DisplayFont = FontFamily(variableFont(R.font.bricolage_grotesque, FontWeight.ExtraBold, 96f))
internal val BodyFont = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { variableFont(R.font.figtree, it) })

private fun display(size: Int, line: Int, spacing: Double) = TextStyle(fontFamily = DisplayFont,
    fontWeight = FontWeight.ExtraBold, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp)
private fun body(weight: FontWeight, size: Int, line: Int) = TextStyle(fontFamily = BodyFont,
    fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = 0.sp)

private val AppTypography = Typography(
    displayLarge = display(92, 92, -3.6), displayMedium = display(80, 80, -3.2), displaySmall = display(34, 38, -0.7),
    headlineLarge = display(32, 34, -0.6), headlineMedium = display(28, 32, -0.4), headlineSmall = display(24, 28, -0.3),
    titleLarge = display(22, 26, -0.2), titleMedium = body(FontWeight.Bold, 16, 22), titleSmall = body(FontWeight.Bold, 14, 20),
    bodyLarge = body(FontWeight.Normal, 16, 24), bodyMedium = body(FontWeight.Normal, 14, 20), bodySmall = body(FontWeight.Normal, 12, 17),
    labelLarge = body(FontWeight.Bold, 14, 20), labelMedium = body(FontWeight.SemiBold, 12, 16), labelSmall = body(FontWeight.SemiBold, 11, 16),
)

private const val MaxFontScale = 1.5f

@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), dynamic: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (dynamic) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) Dark else Light
    val density = LocalDensity.current
    // Text follows the phone setting up to 150 %; the default Density keeps Android's non-linear scaling.
    val capped = if (density.fontScale > MaxFontScale) Density(density.density, MaxFontScale) else density
    CompositionLocalProvider(LocalAppLanguage provides AppLanguage.of(context), LocalDensity provides capped) {
        MaterialTheme(colorScheme = colors.readable(), typography = AppTypography, content = content)
    }
}
