package fi.omasaasahko.data

import fi.omasaasahko.domain.Condition

data class WeatherCode(val condition: Condition, val description: String, val night: Boolean = false)

/** Official FMI and Open-Meteo code tables checked on 2026-09-16. */
object WeatherCodes {
    private fun code(condition: Condition, description: String = condition.label) = WeatherCode(condition, description)
    private val unknown = code(Condition.UNKNOWN)
    // https://en.ilmatieteenlaitos.fi/weather-symbols (codes in the linked SVG names).
    private val fmi = buildMap {
        put(1, code(Condition.CLEAR)); put(2, code(Condition.MOSTLY_CLEAR))
        put(4, code(Condition.PARTLY_CLOUDY)); put(6, code(Condition.MOSTLY_CLOUDY)); put(7, code(Condition.CLOUDY))
        put(9, code(Condition.FOG)); put(11, code(Condition.DRIZZLE))
        put(14, code(Condition.FREEZING_DRIZZLE)); put(17, code(Condition.FREEZING_RAIN))
        put(21, code(Condition.RAIN, "Yksittäisiä sadekuuroja"))
        put(24, code(Condition.RAIN, "Paikoin sadekuuroja")); put(27, code(Condition.RAIN, "Sadekuuroja"))
        val intensity = listOf("heikkoa", "kohtalaista", "voimakasta")
        val plural = listOf("heikkoja", "kohtalaisia", "voimakkaita")
        for (i in 0..2) {
            put(31 + i, code(Condition.RAIN, "Puolipilvistä ja ajoittain ${intensity[i]} sadetta"))
            put(34 + i, code(Condition.RAIN, "Melkein pilvistä ja ajoittain ${intensity[i]} sadetta"))
            put(37 + i, code(Condition.RAIN, "${intensity[i].replaceFirstChar { it.titlecase() }} vesisadetta"))
            put(41 + i, code(Condition.SLEET, "Yksittäisiä ${plural[i]} räntäkuuroja"))
            put(44 + i, code(Condition.SLEET, "Paikoin ${plural[i]} räntäkuuroja"))
            put(47 + i, code(Condition.SLEET, "${intensity[i].replaceFirstChar { it.titlecase() }} räntäsadetta"))
            put(51 + i, code(Condition.SNOW, "Yksittäisiä ${plural[i]} lumikuuroja"))
            put(54 + i, code(Condition.SNOW, "Paikoin ${plural[i]} lumikuuroja"))
            put(57 + i, code(Condition.SNOW, "${intensity[i].replaceFirstChar { it.titlecase() }} lumisadetta"))
        }
        put(61, code(Condition.HAIL, "Yksittäisiä raekuuroja"))
        put(64, code(Condition.HAIL, "Paikoin raekuuroja")); put(67, code(Condition.HAIL, "Raekuuroja"))
        put(71, code(Condition.THUNDER, "Yksittäisiä ukkoskuuroja"))
        put(74, code(Condition.THUNDER, "Paikoin ukkoskuuroja")); put(77, code(Condition.THUNDER, "Ukkoskuuroja"))
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
        48 -> code(Condition.FOG, "Huurresumua")
        51 -> code(Condition.DRIZZLE, "Heikkoa tihkusadetta")
        53 -> code(Condition.DRIZZLE, "Kohtalaista tihkusadetta")
        55 -> code(Condition.DRIZZLE, "Voimakasta tihkusadetta")
        56 -> code(Condition.FREEZING_DRIZZLE, "Heikkoa jäätävää tihkua")
        57 -> code(Condition.FREEZING_DRIZZLE, "Voimakasta jäätävää tihkua")
        61 -> code(Condition.RAIN, "Heikkoa vesisadetta")
        63 -> code(Condition.RAIN, "Kohtalaista vesisadetta")
        65 -> code(Condition.RAIN, "Voimakasta vesisadetta")
        66 -> code(Condition.FREEZING_RAIN, "Heikkoa jäätävää sadetta")
        67 -> code(Condition.FREEZING_RAIN, "Voimakasta jäätävää sadetta")
        71 -> code(Condition.SNOW, "Heikkoa lumisadetta")
        73 -> code(Condition.SNOW, "Kohtalaista lumisadetta")
        75 -> code(Condition.SNOW, "Voimakasta lumisadetta")
        77 -> code(Condition.SNOW, "Lumijyväsiä")
        80 -> code(Condition.RAIN, "Heikkoja sadekuuroja")
        81 -> code(Condition.RAIN, "Kohtalaisia sadekuuroja")
        82 -> code(Condition.RAIN, "Voimakkaita sadekuuroja")
        85 -> code(Condition.SNOW, "Heikkoja lumikuuroja")
        86 -> code(Condition.SNOW, "Voimakkaita lumikuuroja")
        95 -> code(Condition.THUNDER)
        96 -> code(Condition.THUNDER_HAIL, "Ukkosta ja heikkoja rakeita")
        99 -> code(Condition.THUNDER_HAIL, "Ukkosta ja voimakkaita rakeita")
        else -> unknown
    }
}
