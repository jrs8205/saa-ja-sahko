package fi.omasaasahko.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
    val dark = theme.background.luminance() < 0.5f
    return when (band) {
        PriceBand.CHEAP -> if (dark)
            PriceColors(Color(0xFF184E39), Color(0xFF142F29), Color(0xFFE2F9EB), Color(0xFF87EDB5))
        else PriceColors(Color(0xFFD3F2DF), Color(0xFFECF9EF), Color(0xFF183F2B), Color(0xFF14623C))
        PriceBand.MODERATE -> if (dark)
            PriceColors(Color(0xFF50431A), Color(0xFF302B1D), Color(0xFFFFF3CC), Color(0xFFFFD873))
        else PriceColors(Color(0xFFFFEDB0), Color(0xFFFFF8DE), Color(0xFF4B3C13), Color(0xFF735700))
        PriceBand.EXPENSIVE -> if (dark)
            PriceColors(Color(0xFF58351F), Color(0xFF352720), Color(0xFFFFEBDC), Color(0xFFFFB27C))
        else PriceColors(Color(0xFFFFDEC2), Color(0xFFFFF0E3), Color(0xFF562E16), Color(0xFF964100))
        PriceBand.VERY_EXPENSIVE -> if (dark)
            PriceColors(Color(0xFF582731), Color(0xFF34232C), Color(0xFFFFE7EB), Color(0xFFFF9CAC))
        else PriceColors(Color(0xFFFFD9DF), Color(0xFFFFEFF2), Color(0xFF601F30), Color(0xFFA7213D))
        PriceBand.EXTREME -> if (dark)
            PriceColors(Color(0xFF452D61), Color(0xFF2C2440), Color(0xFFF4E9FF), Color(0xFFD7AFFF))
        else PriceColors(Color(0xFFEADBFF), Color(0xFFF6EFFF), Color(0xFF462363), Color(0xFF752EAB))
        null -> PriceColors(theme.surfaceContainer, theme.surfaceContainerLow, theme.onSurface, theme.onSurfaceVariant)
    }
}
