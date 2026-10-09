package fi.omasaasahko

import android.content.Context
import androidx.annotation.StringRes
import fi.omasaasahko.domain.*

/** The resources decide the language; this keeps code and text in the same language. */
fun AppLanguage.Companion.of(context: Context): AppLanguage = fromTag(context.getString(R.string.language_tag))

@StringRes fun AppMessage.res(): Int = when (this) {
    AppMessage.WEATHER_REFRESH_FAILED -> R.string.message_weather_refresh_failed
    AppMessage.PRICES_REFRESH_FAILED -> R.string.message_prices_refresh_failed
    AppMessage.PLACE_SEARCH_FAILED -> R.string.message_place_search_failed
    AppMessage.LOCATION_PERMISSION_MISSING -> R.string.message_location_permission
    AppMessage.LOCATION_DISABLED -> R.string.message_location_disabled
    AppMessage.LOCATION_UNAVAILABLE -> R.string.message_location_unavailable
    AppMessage.LOCATION_STALE -> R.string.message_location_stale
    AppMessage.WARNINGS_TARGETING_FAILED -> R.string.message_warnings_targeting_failed
    AppMessage.WARNINGS_REFRESH_FAILED -> R.string.message_warnings_refresh_failed
}

@StringRes fun WeatherSource.titleRes(): Int = when (this) {
    WeatherSource.FMI -> R.string.source_fmi
    WeatherSource.OPEN_METEO -> R.string.source_open_meteo
}

@StringRes fun WarningLevel.labelRes(): Int = when (this) {
    WarningLevel.YELLOW -> R.string.level_yellow
    WarningLevel.ORANGE -> R.string.level_orange
    WarningLevel.RED -> R.string.level_red
}

@StringRes fun Resolution.labelRes(): Int = when (this) {
    Resolution.QUARTER -> R.string.resolution_quarter
    Resolution.HOUR -> R.string.resolution_hour
}

@StringRes fun WeatherText.res(): Int = when (this) {
    WeatherText.CLEAR -> R.string.weather_clear
    WeatherText.MOSTLY_CLEAR -> R.string.weather_mostly_clear
    WeatherText.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
    WeatherText.MOSTLY_CLOUDY -> R.string.weather_mostly_cloudy
    WeatherText.CLOUDY -> R.string.weather_cloudy
    WeatherText.DRIZZLE -> R.string.weather_drizzle
    WeatherText.FREEZING_DRIZZLE -> R.string.weather_freezing_drizzle
    WeatherText.FREEZING_RAIN -> R.string.weather_freezing_rain
    WeatherText.RAIN -> R.string.weather_rain
    WeatherText.SLEET -> R.string.weather_sleet
    WeatherText.SNOW -> R.string.weather_snow
    WeatherText.HAIL -> R.string.weather_hail
    WeatherText.THUNDER -> R.string.weather_thunder
    WeatherText.THUNDER_HAIL -> R.string.weather_thunder_hail
    WeatherText.FOG -> R.string.weather_fog
    WeatherText.UNKNOWN -> R.string.weather_unknown
    WeatherText.ISOLATED_SHOWERS -> R.string.weather_isolated_showers
    WeatherText.SCATTERED_SHOWERS -> R.string.weather_scattered_showers
    WeatherText.SHOWERS -> R.string.weather_showers
    WeatherText.PARTLY_CLOUDY_LIGHT_RAIN -> R.string.weather_partly_cloudy_light_rain
    WeatherText.PARTLY_CLOUDY_MODERATE_RAIN -> R.string.weather_partly_cloudy_moderate_rain
    WeatherText.PARTLY_CLOUDY_HEAVY_RAIN -> R.string.weather_partly_cloudy_heavy_rain
    WeatherText.MOSTLY_CLOUDY_LIGHT_RAIN -> R.string.weather_mostly_cloudy_light_rain
    WeatherText.MOSTLY_CLOUDY_MODERATE_RAIN -> R.string.weather_mostly_cloudy_moderate_rain
    WeatherText.MOSTLY_CLOUDY_HEAVY_RAIN -> R.string.weather_mostly_cloudy_heavy_rain
    WeatherText.LIGHT_RAIN -> R.string.weather_light_rain
    WeatherText.MODERATE_RAIN -> R.string.weather_moderate_rain
    WeatherText.HEAVY_RAIN -> R.string.weather_heavy_rain
    WeatherText.ISOLATED_LIGHT_SLEET_SHOWERS -> R.string.weather_isolated_light_sleet_showers
    WeatherText.ISOLATED_MODERATE_SLEET_SHOWERS -> R.string.weather_isolated_moderate_sleet_showers
    WeatherText.ISOLATED_HEAVY_SLEET_SHOWERS -> R.string.weather_isolated_heavy_sleet_showers
    WeatherText.SCATTERED_LIGHT_SLEET_SHOWERS -> R.string.weather_scattered_light_sleet_showers
    WeatherText.SCATTERED_MODERATE_SLEET_SHOWERS -> R.string.weather_scattered_moderate_sleet_showers
    WeatherText.SCATTERED_HEAVY_SLEET_SHOWERS -> R.string.weather_scattered_heavy_sleet_showers
    WeatherText.LIGHT_SLEET -> R.string.weather_light_sleet
    WeatherText.MODERATE_SLEET -> R.string.weather_moderate_sleet
    WeatherText.HEAVY_SLEET -> R.string.weather_heavy_sleet
    WeatherText.ISOLATED_LIGHT_SNOW_SHOWERS -> R.string.weather_isolated_light_snow_showers
    WeatherText.ISOLATED_MODERATE_SNOW_SHOWERS -> R.string.weather_isolated_moderate_snow_showers
    WeatherText.ISOLATED_HEAVY_SNOW_SHOWERS -> R.string.weather_isolated_heavy_snow_showers
    WeatherText.SCATTERED_LIGHT_SNOW_SHOWERS -> R.string.weather_scattered_light_snow_showers
    WeatherText.SCATTERED_MODERATE_SNOW_SHOWERS -> R.string.weather_scattered_moderate_snow_showers
    WeatherText.SCATTERED_HEAVY_SNOW_SHOWERS -> R.string.weather_scattered_heavy_snow_showers
    WeatherText.LIGHT_SNOW -> R.string.weather_light_snow
    WeatherText.MODERATE_SNOW -> R.string.weather_moderate_snow
    WeatherText.HEAVY_SNOW -> R.string.weather_heavy_snow
    WeatherText.ISOLATED_HAIL_SHOWERS -> R.string.weather_isolated_hail_showers
    WeatherText.SCATTERED_HAIL_SHOWERS -> R.string.weather_scattered_hail_showers
    WeatherText.HAIL_SHOWERS -> R.string.weather_hail_showers
    WeatherText.ISOLATED_THUNDER_SHOWERS -> R.string.weather_isolated_thunder_showers
    WeatherText.SCATTERED_THUNDER_SHOWERS -> R.string.weather_scattered_thunder_showers
    WeatherText.THUNDER_SHOWERS -> R.string.weather_thunder_showers
    WeatherText.FREEZING_FOG -> R.string.weather_freezing_fog
    WeatherText.LIGHT_DRIZZLE -> R.string.weather_light_drizzle
    WeatherText.MODERATE_DRIZZLE -> R.string.weather_moderate_drizzle
    WeatherText.DENSE_DRIZZLE -> R.string.weather_dense_drizzle
    WeatherText.LIGHT_FREEZING_DRIZZLE -> R.string.weather_light_freezing_drizzle
    WeatherText.DENSE_FREEZING_DRIZZLE -> R.string.weather_dense_freezing_drizzle
    WeatherText.LIGHT_FREEZING_RAIN -> R.string.weather_light_freezing_rain
    WeatherText.HEAVY_FREEZING_RAIN -> R.string.weather_heavy_freezing_rain
    WeatherText.SNOW_GRAINS -> R.string.weather_snow_grains
    WeatherText.LIGHT_RAIN_SHOWERS -> R.string.weather_light_rain_showers
    WeatherText.MODERATE_RAIN_SHOWERS -> R.string.weather_moderate_rain_showers
    WeatherText.HEAVY_RAIN_SHOWERS -> R.string.weather_heavy_rain_showers
    WeatherText.LIGHT_SNOW_SHOWERS -> R.string.weather_light_snow_showers
    WeatherText.HEAVY_SNOW_SHOWERS -> R.string.weather_heavy_snow_showers
    WeatherText.THUNDER_LIGHT_HAIL -> R.string.weather_thunder_light_hail
    WeatherText.THUNDER_HEAVY_HAIL -> R.string.weather_thunder_heavy_hail
}
