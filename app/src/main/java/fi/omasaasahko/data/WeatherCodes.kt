package fi.omasaasahko.data

import fi.omasaasahko.domain.Condition
import fi.omasaasahko.domain.WeatherText
import fi.omasaasahko.domain.WeatherText.*

data class WeatherCode(val condition: Condition, val description: WeatherText, val night: Boolean = false)

/** Official FMI and Open-Meteo code tables checked on 2026-09-16. */
object WeatherCodes {
    private fun code(condition: Condition, description: WeatherText = condition.text) = WeatherCode(condition, description)
    private val unknown = code(Condition.UNKNOWN)
    // https://en.ilmatieteenlaitos.fi/weather-symbols (codes in the linked SVG names).
    private val fmi = buildMap {
        put(1, code(Condition.CLEAR)); put(2, code(Condition.MOSTLY_CLEAR))
        put(4, code(Condition.PARTLY_CLOUDY)); put(6, code(Condition.MOSTLY_CLOUDY)); put(7, code(Condition.CLOUDY))
        put(9, code(Condition.FOG)); put(11, code(Condition.DRIZZLE))
        put(14, code(Condition.FREEZING_DRIZZLE)); put(17, code(Condition.FREEZING_RAIN))
        put(21, code(Condition.RAIN, ISOLATED_SHOWERS))
        put(24, code(Condition.RAIN, SCATTERED_SHOWERS)); put(27, code(Condition.RAIN, SHOWERS))
        // Codes 31..59 come in light/moderate/heavy triplets.
        listOf(PARTLY_CLOUDY_LIGHT_RAIN, PARTLY_CLOUDY_MODERATE_RAIN, PARTLY_CLOUDY_HEAVY_RAIN).forEachIndexed { i, text -> put(31 + i, code(Condition.RAIN, text)) }
        listOf(MOSTLY_CLOUDY_LIGHT_RAIN, MOSTLY_CLOUDY_MODERATE_RAIN, MOSTLY_CLOUDY_HEAVY_RAIN).forEachIndexed { i, text -> put(34 + i, code(Condition.RAIN, text)) }
        listOf(LIGHT_RAIN, MODERATE_RAIN, HEAVY_RAIN).forEachIndexed { i, text -> put(37 + i, code(Condition.RAIN, text)) }
        listOf(ISOLATED_LIGHT_SLEET_SHOWERS, ISOLATED_MODERATE_SLEET_SHOWERS, ISOLATED_HEAVY_SLEET_SHOWERS).forEachIndexed { i, text -> put(41 + i, code(Condition.SLEET, text)) }
        listOf(SCATTERED_LIGHT_SLEET_SHOWERS, SCATTERED_MODERATE_SLEET_SHOWERS, SCATTERED_HEAVY_SLEET_SHOWERS).forEachIndexed { i, text -> put(44 + i, code(Condition.SLEET, text)) }
        listOf(LIGHT_SLEET, MODERATE_SLEET, HEAVY_SLEET).forEachIndexed { i, text -> put(47 + i, code(Condition.SLEET, text)) }
        listOf(ISOLATED_LIGHT_SNOW_SHOWERS, ISOLATED_MODERATE_SNOW_SHOWERS, ISOLATED_HEAVY_SNOW_SHOWERS).forEachIndexed { i, text -> put(51 + i, code(Condition.SNOW, text)) }
        listOf(SCATTERED_LIGHT_SNOW_SHOWERS, SCATTERED_MODERATE_SNOW_SHOWERS, SCATTERED_HEAVY_SNOW_SHOWERS).forEachIndexed { i, text -> put(54 + i, code(Condition.SNOW, text)) }
        listOf(LIGHT_SNOW, MODERATE_SNOW, HEAVY_SNOW).forEachIndexed { i, text -> put(57 + i, code(Condition.SNOW, text)) }
        put(61, code(Condition.HAIL, ISOLATED_HAIL_SHOWERS))
        put(64, code(Condition.HAIL, SCATTERED_HAIL_SHOWERS)); put(67, code(Condition.HAIL, HAIL_SHOWERS))
        put(71, code(Condition.THUNDER, ISOLATED_THUNDER_SHOWERS))
        put(74, code(Condition.THUNDER, SCATTERED_THUNDER_SHOWERS)); put(77, code(Condition.THUNDER, THUNDER_SHOWERS))
    }

    fun fmi(symbol: Int?): WeatherCode {
        if (symbol == null) return unknown
        val night = symbol in 101..177
        // Only +100 is documented; 201 must not become clear weather.
        return fmi[if (night) symbol - 100 else symbol]?.copy(night = night) ?: unknown
    }

    // https://open-meteo.com/en/docs : WMO Weather interpretation codes (WW).
    fun wmo(symbol: Int?): WeatherCode = when (symbol) {
        0 -> code(Condition.CLEAR)
        1 -> code(Condition.MOSTLY_CLEAR)
        2 -> code(Condition.PARTLY_CLOUDY)
        3 -> code(Condition.CLOUDY)
        45 -> code(Condition.FOG)
        48 -> code(Condition.FOG, FREEZING_FOG)
        51 -> code(Condition.DRIZZLE, LIGHT_DRIZZLE)
        53 -> code(Condition.DRIZZLE, MODERATE_DRIZZLE)
        55 -> code(Condition.DRIZZLE, DENSE_DRIZZLE)
        56 -> code(Condition.FREEZING_DRIZZLE, LIGHT_FREEZING_DRIZZLE)
        57 -> code(Condition.FREEZING_DRIZZLE, DENSE_FREEZING_DRIZZLE)
        61 -> code(Condition.RAIN, LIGHT_RAIN)
        63 -> code(Condition.RAIN, MODERATE_RAIN)
        65 -> code(Condition.RAIN, HEAVY_RAIN)
        66 -> code(Condition.FREEZING_RAIN, LIGHT_FREEZING_RAIN)
        67 -> code(Condition.FREEZING_RAIN, HEAVY_FREEZING_RAIN)
        71 -> code(Condition.SNOW, LIGHT_SNOW)
        73 -> code(Condition.SNOW, MODERATE_SNOW)
        75 -> code(Condition.SNOW, HEAVY_SNOW)
        77 -> code(Condition.SNOW, SNOW_GRAINS)
        80 -> code(Condition.RAIN, LIGHT_RAIN_SHOWERS)
        81 -> code(Condition.RAIN, MODERATE_RAIN_SHOWERS)
        82 -> code(Condition.RAIN, HEAVY_RAIN_SHOWERS)
        85 -> code(Condition.SNOW, LIGHT_SNOW_SHOWERS)
        86 -> code(Condition.SNOW, HEAVY_SNOW_SHOWERS)
        95 -> code(Condition.THUNDER)
        96 -> code(Condition.THUNDER_HAIL, THUNDER_LIGHT_HAIL)
        99 -> code(Condition.THUNDER_HAIL, THUNDER_HEAVY_HAIL)
        else -> unknown
    }
}
