package fi.omasaasahko.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.math.BigDecimal
import java.math.RoundingMode

enum class PriceBand(val label: String, val range: String) {
    CHEAP("Halpaa", "Alle 5,000"),
    MODERATE("Kohtuullista", "5,000–9,999"),
    EXPENSIVE("Melko kallista", "10,000–14,999"),
    VERY_EXPENSIVE("Todella kallista", "15,000–19,999"),
    EXTREME("Poikkeuksellisen kallista", "Vähintään 20,000"),
}

/** Classify the displayed price, after VAT/averaging and the same rounding as Prices.format. */
fun priceBand(value: BigDecimal?): PriceBand? {
    val displayed = value?.setScale(3, RoundingMode.HALF_UP) ?: return null
    return when {
        displayed < BigDecimal("5") -> PriceBand.CHEAP
        displayed < BigDecimal("10") -> PriceBand.MODERATE
        displayed < BigDecimal("15") -> PriceBand.EXPENSIVE
        displayed < BigDecimal("20") -> PriceBand.VERY_EXPENSIVE
        else -> PriceBand.EXTREME
    }
}

data class PriceColors(val top: Color, val bottom: Color, val ink: Color, val accent: Color)

/** Price meaning keeps its own colors while navigation follows the phone's theme. */
@Composable
fun priceColors(band: PriceBand?): PriceColors {
    val theme = MaterialTheme.colorScheme
    val dark = isDarkTheme()
    return when (band) {
        PriceBand.CHEAP -> if (dark)
            PriceColors(Color(0xFF184E39), Color(0xFF142F29), Color(0xFFE2F9EB), Color(0xFF98F0BF))
        else PriceColors(Color(0xFFD3F2DF), Color(0xFFECF9EF), Color(0xFF183F2B), Color(0xFF105131))
        PriceBand.MODERATE -> warmColors(WarmTone.YELLOW).let { PriceColors(it.top, it.bottom, it.ink, it.accent) }
        PriceBand.EXPENSIVE -> warmColors(WarmTone.ORANGE).let { PriceColors(it.top, it.bottom, it.ink, it.accent) }
        PriceBand.VERY_EXPENSIVE -> warmColors(WarmTone.RED).let { PriceColors(it.top, it.bottom, it.ink, it.accent) }
        PriceBand.EXTREME -> if (dark)
            PriceColors(Color(0xFF452D61), Color(0xFF2C2440), Color(0xFFF4E9FF), Color(0xFFE0C1FF))
        else PriceColors(Color(0xFFEADBFF), Color(0xFFF6EFFF), Color(0xFF462363), Color(0xFF632692))
        null -> PriceColors(theme.surfaceContainer, theme.surfaceContainerLow, theme.onSurface, theme.onSurfaceVariant)
    }
}
