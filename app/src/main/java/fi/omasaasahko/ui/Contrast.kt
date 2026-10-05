package fi.omasaasahko.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** WCAG 2 contrast ratio, 1–21. AAA asks 7:1 for text and 3:1 for icons and other graphics. */
fun contrastRatio(a: Color, b: Color): Float {
    val x = a.luminance(); val y = b.luminance()
    return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
}

internal const val TEXT_CONTRAST = 7f

/** The strongest contrast any colour can reach on these backgrounds: pure black or pure white. */
internal fun bestContrastOn(backgrounds: List<Color>): Float =
    listOf(Color.Black, Color.White).maxOf { pole -> backgrounds.minOf { contrastRatio(pole, it) } }

/**
 * Moves the colour toward black or white, keeping its hue, until it reads at [ratio] on every background.
 * A mid-tone background cannot carry [ratio] at all; then the stronger pole is the best there is.
 */
internal fun Color.readableOn(backgrounds: List<Color>, ratio: Float = TEXT_CONTRAST): Color {
    fun passes(color: Color) = backgrounds.all { contrastRatio(color, it) >= ratio }
    if (passes(this)) return this
    val toward = listOf(Color.Black, Color.White).maxBy { pole -> backgrounds.minOf { contrastRatio(pole, it) } }
    if (!passes(toward)) return toward
    var low = 0f; var high = 1f
    repeat(16) { val mid = (low + high) / 2; if (passes(lerp(this, toward, mid))) high = mid else low = mid }
    return lerp(this, toward, high)
}

/**
 * Wallpaper-derived schemes only promise AA; this lifts the text roles the app uses to AAA.
 * Surfaces and containers stay as the system chose them, so the look still follows the phone.
 */
internal fun ColorScheme.readable(): ColorScheme {
    val surfaces = listOf(background, surface, surfaceContainerLowest, surfaceContainerLow, surfaceContainer,
        surfaceContainerHigh, surfaceContainerHighest)
    val primary = primary.readableOn(surfaces)
    return copy(
        primary = primary,
        onPrimary = onPrimary.readableOn(listOf(primary)),
        onSurface = onSurface.readableOn(surfaces),
        onSurfaceVariant = onSurfaceVariant.readableOn(surfaces),
        error = error.readableOn(surfaces),
        onPrimaryContainer = onPrimaryContainer.readableOn(listOf(primaryContainer)),
        onSecondaryContainer = onSecondaryContainer.readableOn(listOf(secondaryContainer)),
        inverseOnSurface = inverseOnSurface.readableOn(listOf(inverseSurface)),
    )
}
