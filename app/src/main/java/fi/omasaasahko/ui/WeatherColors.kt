package fi.omasaasahko.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import fi.omasaasahko.domain.WeatherSource

data class ForecastColors(val top: Color, val bottom: Color, val ink: Color, val muted: Color, val accent: Color)

/** Weather has its own illustrative accents; navigation still uses the phone's dynamic theme. */
@Composable
fun forecastColors(source: WeatherSource): ForecastColors {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (source == WeatherSource.FMI) {
        if (dark) ForecastColors(Color(0xFF1A426B), Color(0xFF172A46), Color(0xFFF0F6FF), Color(0xFFBBD8ED), Color(0xFF97D5FF))
        else ForecastColors(Color(0xFFD6E9FF), Color(0xFFEBF3FF), Color(0xFF183652), Color(0xFF415F7A), Color(0xFF17669E))
    } else {
        if (dark) ForecastColors(Color(0xFF155448), Color(0xFF172F32), Color(0xFFE9FFF6), Color(0xFFB5DED1), Color(0xFF90EAC9))
        else ForecastColors(Color(0xFFC8F0DF), Color(0xFFE7F7EF), Color(0xFF173D33), Color(0xFF42685C), Color(0xFF146C51))
    }
}

@Composable
fun sunColors(): ForecastColors = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    ForecastColors(Color(0xFF503B27), Color(0xFF342930), Color(0xFFFFEDCF), Color(0xFFE5CDB1), Color(0xFFFFCB79))
else ForecastColors(Color(0xFFFFE4AB), Color(0xFFFFF3DB), Color(0xFF593A16), Color(0xFF7B5D35), Color(0xFF995200))
