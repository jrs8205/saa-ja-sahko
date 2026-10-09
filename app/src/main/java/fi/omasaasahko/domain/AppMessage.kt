package fi.omasaasahko.domain

/** User-facing failures; the screen resolves each to the current language. */
enum class AppMessage {
    WEATHER_REFRESH_FAILED, PRICES_REFRESH_FAILED, PLACE_SEARCH_FAILED,
    LOCATION_PERMISSION_MISSING, LOCATION_DISABLED, LOCATION_UNAVAILABLE, LOCATION_STALE,
    WARNINGS_TARGETING_FAILED, WARNINGS_REFRESH_FAILED,
}
