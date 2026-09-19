package fi.omasaasahko.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import fi.omasaasahko.AppState
import fi.omasaasahko.WarningsState
import fi.omasaasahko.domain.*
import java.time.LocalDate

/** Share the expensive derivations between navigation and the visible screen. */
@Composable
internal fun rememberPriceRows(state: AppState, date: LocalDate): List<PriceSlot> =
    remember(state.prices, date, state.resolution, state.includeVat) {
        Prices.slots(state.prices?.quarters.orEmpty(), date, state.resolution, state.includeVat)
    }

@Composable
internal fun rememberLocalWarnings(state: AppState, warnings: WarningsState, permitted: Boolean): List<WeatherWarning> =
    remember(warnings.snapshot, state.place, state.now, permitted, state.selectedPlace) {
        if (permitted || state.selectedPlace != null) warnings.snapshot?.local(state.place, state.now).orEmpty() else emptyList()
    }
