package fi.omasaasahko.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fi.omasaasahko.R
import fi.omasaasahko.domain.WarningLevel

enum class AppTab(val label: String, val icon: Int, val tag: String) {
    WEATHER("Sää", R.drawable.ic_tab_weather, "tab-weather"),
    PRICES("Sähkö", R.drawable.ic_tab_prices, "tab-prices"),
    WARNINGS("Varoitukset", R.drawable.ic_tab_warnings, "tab-warnings"),
}

/** Colours of the selected tab's pill; it echoes what the tab currently shows. */
data class NavTint(val container: Color, val content: Color)

@Composable
internal fun navTint(tab: AppTab, band: PriceBand?, level: WarningLevel?): NavTint {
    val scheme = MaterialTheme.colorScheme
    val dark = isDarkTheme()
    val calm = if (dark) NavTint(scheme.primary, scheme.onPrimary) else NavTint(scheme.primaryContainer, scheme.onPrimaryContainer)
    return when {
        tab == AppTab.PRICES && band != null -> priceColors(band).let { if (dark) NavTint(it.accent, it.bottom) else NavTint(it.top, it.ink) }
        tab == AppTab.WARNINGS && level != null -> warningColors(level).let { if (dark) NavTint(it.accent, it.onAccent) else NavTint(it.container, it.ink) }
        else -> calm
    }
}

@Composable
fun FloatingNavBar(selected: AppTab, tint: NavTint, onSelect: (AppTab) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = isDarkTheme()
    val container = if (dark) scheme.surfaceContainerHighest else scheme.inverseSurface
    val idle = if (dark) scheme.onSurfaceVariant else scheme.inverseOnSurface
    val density = LocalDensity.current
    // Three fixed slots cannot grow with the text; beyond 1.3x the longest label would be cut off.
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.3f))) {
    Row(modifier.fillMaxWidth().clip(PillShape).background(container).padding(6.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        AppTab.entries.forEach { tab ->
            val active = tab == selected
            val background by animateColorAsState(if (active) tint.container else Color.Transparent, label = "Välilehden tausta")
            val content = if (active) tint.content else idle
            val item = Modifier.weight(if (active) 1.8f else 1f).heightIn(min = 56.dp).clip(PillShape).background(background)
                .selectable(selected = active, role = Role.Tab, onClick = { onSelect(tab) }).testTag(tab.tag)
                .padding(horizontal = 8.dp, vertical = 6.dp)
            if (active) Row(item, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(tab.icon), contentDescription = null, tint = content)
                Text(tab.label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else Column(item, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painterResource(tab.icon), contentDescription = null, tint = content)
                Text(tab.label, style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    }
}
