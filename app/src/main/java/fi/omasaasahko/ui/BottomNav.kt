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
import androidx.compose.runtime.getValue
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

enum class AppTab(val label: String, val icon: Int, val tag: String) {
    WEATHER("Sää", R.drawable.ic_tab_weather, "tab-weather"),
    PRICES("Sähkö", R.drawable.ic_tab_prices, "tab-prices"),
    WARNINGS("Varoitukset", R.drawable.ic_tab_warnings, "tab-warnings"),
}

/** Colours of the selected tab's pill; it echoes what the tab currently shows. */
data class NavTint(val container: Color, val content: Color)

@Composable
fun FloatingNavBar(selected: AppTab, tint: NavTint, onSelect: (AppTab) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = isDarkTheme()
    val container = if (dark) scheme.surfaceContainerHighest else scheme.inverseSurface
    val idle = if (dark) scheme.onSurfaceVariant else scheme.inverseOnSurface
    Row(modifier.fillMaxWidth().clip(PillShape).background(container).padding(6.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        AppTab.entries.forEach { tab ->
            val active = tab == selected
            val background by animateColorAsState(if (active) tint.container else Color.Transparent, label = "Välilehden tausta")
            val content = if (active) tint.content else idle
            val item = Modifier.weight(if (active) 1.6f else 1f).heightIn(min = 56.dp).clip(PillShape).background(background)
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
