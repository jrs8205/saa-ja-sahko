package fi.omasaasahko.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fi.omasaasahko.AppState
import fi.omasaasahko.domain.PlaceResult

@Composable
internal fun PlacePicker(state: AppState, onQuery: (String) -> Unit, onSelect: (PlaceResult) -> Unit,
                         onFavorite: (PlaceResult) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Hae paikka", style = MaterialTheme.typography.headlineSmall)
                Text("Paikkahaku · Maanmittauslaitos", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = state.searchQuery, onValueChange = onQuery, singleLine = true,
                    label = { Text("Kunta tai paikannimi") }, modifier = Modifier.fillMaxWidth().testTag("place-search"))
                if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.searchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                LazyColumn(Modifier.weight(1f).testTag("place-results"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.favorites.isNotEmpty()) {
                        item { Text("Suosikit", style = MaterialTheme.typography.titleMedium) }
                        items(state.favorites, key = { "favorite-${it.id}" }) { place ->
                            PlaceChoice(place, true, { onSelect(place); onDismiss() }, { onFavorite(place) })
                        }
                    }
                    if (state.searchResults.isNotEmpty()) {
                        item { Text("Hakutulokset", style = MaterialTheme.typography.titleMedium) }
                        items(state.searchResults, key = { "result-${it.id}" }) { place ->
                            PlaceChoice(place, state.favorites.any { it.id == place.id },
                                { onSelect(place); onDismiss() }, { onFavorite(place) })
                        }
                    } else if (state.searchQuery.trim().length >= 2 && !state.searching && state.searchError == null) {
                        item { Text("Paikkoja ei löytynyt. Kokeile kunnan tai paikan nimeä.") }
                    } else if (state.searchQuery.trim().length < 2) {
                        item { Text("Kirjoita vähintään kaksi merkkiä. Tähdestä tallennat paikan suosikiksi.") }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Sulje") }
            }
        }
}

@Composable
private fun PlaceChoice(place: PlaceResult, favorite: Boolean, onSelect: () -> Unit, onFavorite: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onSelect, modifier = Modifier.weight(1f).testTag("place-choice-${place.id}")) {
            Column(Modifier.fillMaxWidth()) {
                Text(place.label, style = MaterialTheme.typography.bodyLarge)
                if (place.kind.isNotBlank()) Text(place.kind, style = MaterialTheme.typography.bodySmall)
            }
        }
        TextButton(onClick = onFavorite, modifier = Modifier.semantics {
            contentDescription = (if (favorite) "Poista suosikki " else "Lisää suosikiksi ") + place.label
        }) { Text(if (favorite) "★" else "☆", style = MaterialTheme.typography.headlineSmall) }
    }
}
