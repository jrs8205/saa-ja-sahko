package fi.omasaasahko.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import fi.omasaasahko.domain.WeatherSource

data class ForecastColors(val top: Color, val ink: Color, val muted: Color, val accent: Color)

/** Weather has its own illustrative accents; navigation still uses the phone's dynamic theme. */
@Composable
fun forecastColors(source: WeatherSource): ForecastColors {
    val dark = isDarkTheme()
    return if (source == WeatherSource.FMI) {
        if (dark) ForecastColors(Color(0xFF1A426B), Color(0xFFF0F6FF), Color(0xFFC0DBEF), Color(0xFFAADDFF))
        else ForecastColors(Color(0xFFD6E9FF), Color(0xFF183652), Color(0xFF344D63), Color(0xFF10476F))
    } else {
        if (dark) ForecastColors(Color(0xFF11463B), Color(0xFFE9FFF6), Color(0xFFB5DED1), Color(0xFF90EAC9))
        else ForecastColors(Color(0xFFC8F0DF), Color(0xFF173D33), Color(0xFF335047), Color(0xFF0E4E3B))
    }
}

/** Drawn weather symbols. In light themes a pale sun cannot reach 3:1, so its edge and rays carry the contrast. */
data class SymbolColors(val cloud: Color, val rearCloud: Color, val rain: Color, val snow: Color, val sun: Color, val sunEdge: Color)

@Composable
fun symbolColors(): SymbolColors = if (isDarkTheme())
    SymbolColors(cloud = Color(0xFFD0E5F7), rearCloud = Color(0xFF86B0D4), rain = Color(0xFF61DBFF), snow = Color(0xFFE6F7FF),
        sun = Color(0xFFFFD16D), sunEdge = Color(0xFFFFD16D))
else SymbolColors(cloud = Color(0xFF3A5C8A), rearCloud = Color(0xFF5376A3), rain = Color(0xFF0A75A8), snow = Color(0xFF3B70AD),
    sun = Color(0xFFFFC640), sunEdge = Color(0xFF8F5F08))

@Composable
fun sunColors(): ForecastColors = if (isDarkTheme())
    ForecastColors(Color(0xFF503B27), Color(0xFFFFEDCF), Color(0xFFE7D1B6), Color(0xFFFFCB79))
else ForecastColors(Color(0xFFFFE4AB), Color(0xFF593A16), Color(0xFF5D4628), Color(0xFF723D00))
