package fi.omasaasahko.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import fi.omasaasahko.AppState
import fi.omasaasahko.SourceState
import fi.omasaasahko.domain.*
import java.math.BigDecimal
import java.time.*
import kotlin.math.sin

/** Deterministic design data: used by previews/tests only, never by the production screen. */
object PreviewData {
    val now: Instant = Instant.parse("2026-09-16T09:10:00Z")
    val place = Place(60.29, 24.84, "Tikkurila, Vantaa", now)
    val state: AppState get() {
        val start = now.atZone(HELSINKI).toLocalDate().atStartOfDay(HELSINKI).toInstant()
        val weather = WeatherSource.entries.associateWith { source ->
            val hours = (0 until 168).map { i -> WeatherHour(start.plusSeconds(i * 3600L),
                14.0 + sin(i / 6.0) * 3 + source.ordinal, 12.0 + sin(i / 6.0) * 3,
                4.0, 185.0, if (i < 20) 0.3 + i % 3 * 0.4 else 0.0,
                if (source == WeatherSource.OPEN_METEO) 80.0 else null,
                if (i < 20) Condition.RAIN else Condition.PARTLY_CLOUDY, i % 24 < 4 || i % 24 > 18)
            }
            SourceState(Forecast(source, place, now.minusSeconds(60), hours,
                dailyForecast(hours, HELSINKI).map { it.copy(sunrise = it.date.atTime(6, 50).atZone(HELSINKI).toInstant(),
                    sunset = it.date.atTime(19, 39).atZone(HELSINKI).toInstant(), uvMax = 2.0) }))
        }
        val prices = (0 until 192).map { i -> QuarterPrice(start.plusSeconds(i * 900L), BigDecimal.valueOf(90 + 120 * sin((i - 16) / 16.0))) }
        return AppState(now, place, weather = weather, prices = PriceData(now, prices))
    }
}

@Preview(name = "Sää · vaalea", showBackground = true, widthDp = 412, heightDp = 915)
@Composable fun LightPreview() { Preview(false) }
@Preview(name = "Sää · tumma", showBackground = true, widthDp = 412, heightDp = 915)
@Composable fun DarkPreview() { Preview(true) }
@Composable private fun Preview(dark: Boolean) {
    AppTheme(dark = dark, dynamic = false) {
        AppScreen(PreviewData.state, true, {}, {}, {}, {}, {}, {})
    }
}
