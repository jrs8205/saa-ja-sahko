package fi.omasaasahko.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import fi.omasaasahko.R
import fi.omasaasahko.domain.AppLanguage
import fi.omasaasahko.domain.AppMessage
import fi.omasaasahko.domain.CURRENT_LOCATION_NAME
import fi.omasaasahko.domain.Place
import fi.omasaasahko.domain.WeatherSource
import fi.omasaasahko.domain.WeatherText
import fi.omasaasahko.res
import fi.omasaasahko.titleRes

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.FI }

@Composable fun WeatherSource.title(): String = stringResource(titleRes())

@Composable fun WeatherText.label(): String = stringResource(res())

@Composable fun AppMessage.text(): String = stringResource(res())

@Composable fun Place.displayName(): String = if (name == CURRENT_LOCATION_NAME) stringResource(R.string.current_location) else name

