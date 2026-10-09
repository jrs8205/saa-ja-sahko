package fi.omasaasahko.domain

import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The language the app's resources resolved to. Finland-Swedish keeps Finnish clock and
 * date punctuation; English uses its own.
 */
enum class AppLanguage(
    val tag: String, val locale: Locale, val decimalSeparator: Char,
    clock: String, shortDate: String, dateTime: String, longDay: String, day: String, weekday: String,
) {
    FI("fi", Locale.forLanguageTag("fi-FI"), ',', "HH.mm", "d.M.", "d.M. HH.mm", "EEEE d. MMMM", "EEEE d.M.", "EEE"),
    SV("sv", Locale.forLanguageTag("sv-FI"), ',', "HH.mm", "d.M.", "d.M. HH.mm", "EEEE d MMMM", "EEEE d.M.", "EEE"),
    EN("en", Locale.ENGLISH, '.', "HH:mm", "d MMM", "d MMM HH:mm", "EEEE d MMMM", "EEEE d MMM", "EEE");

    private val clockFormat = DateTimeFormatter.ofPattern(clock, locale)
    private val shortDateFormat = DateTimeFormatter.ofPattern(shortDate, locale)
    private val dateTimeFormat = DateTimeFormatter.ofPattern(dateTime, locale)
    private val longDayFormat = DateTimeFormatter.ofPattern(longDay, locale)
    private val dayFormat = DateTimeFormatter.ofPattern(day, locale)
    private val weekdayFormat = DateTimeFormatter.ofPattern(weekday, locale)
    private val weekdayLongFormat = DateTimeFormatter.ofPattern("EEEE", locale)

    fun clock(time: ZonedDateTime): String = clockFormat.format(time)
    fun shortDate(date: LocalDate): String = shortDateFormat.format(date)
    fun dateTime(time: ZonedDateTime): String = dateTimeFormat.format(time)
    /** "Torstai 17. syyskuuta" */
    fun longDay(date: LocalDate): String = capitalized(longDayFormat.format(date))
    /** "Torstaina 17.9." */
    fun day(date: LocalDate): String = capitalized(dayFormat.format(date))
    /** "to" */
    fun weekday(date: LocalDate): String = weekdayFormat.format(date)
    /** "Torstaina" */
    fun weekdayLong(date: LocalDate): String = capitalized(weekdayLongFormat.format(date))
    fun number(value: Double, places: Int): String = String.format(locale, "%.${places}f", value)

    private fun capitalized(text: String) = text.replaceFirstChar { it.titlecase(locale) }

    companion object {
        fun fromTag(tag: String): AppLanguage = entries.firstOrNull { it.tag == tag } ?: EN
    }
}
