package fi.omasaasahko.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fi.omasaasahko.R
import fi.omasaasahko.domain.WeatherSource

object Radius {
    val hero = 36.dp; val block = 32.dp; val panel = 28.dp; val tile = 24.dp; val row = 20.dp
}
val PillShape: Shape = RoundedCornerShape(percent = 50)

@Composable fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** A flat, solid-colour container. Meaning comes from the colour, never from a gradient or border. */
@Composable
fun Block(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
          contentColor: Color = MaterialTheme.colorScheme.onSurface, radius: Dp = Radius.block,
          padding: PaddingValues = PaddingValues(20.dp), content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier, shape = RoundedCornerShape(radius), color = color, contentColor = contentColor) {
        Column(Modifier.fillMaxWidth().padding(padding), content = content)
    }
}

@Composable
fun SourceDot(source: WeatherSource, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).background(forecastColors(source).accent, CircleShape))
}

@Composable
fun TonePill(text: String, container: Color, content: Color, modifier: Modifier = Modifier, icon: Int? = null) {
    Row(modifier.clip(PillShape).background(container).heightIn(min = 36.dp).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        icon?.let { Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(16.dp), tint = content) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

@Composable
fun RefreshButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledIconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(52.dp)) {
        Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Päivitä")
    }
}

@Composable
fun LocationPill(name: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier.heightIn(min = 52.dp).clip(PillShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .then(if (onClick != null) Modifier.clickable(onClickLabel = "Hae paikka tai suosikit", role = Role.Button, onClick = onClick)
            .testTag("open-place-search") else Modifier)
        .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(R.drawable.ic_place), contentDescription = null, modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary)
        Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (onClick != null) Icon(painterResource(R.drawable.ic_expand), contentDescription = null,
            modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingSwitchRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit,
                     modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp)
        .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
        .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
