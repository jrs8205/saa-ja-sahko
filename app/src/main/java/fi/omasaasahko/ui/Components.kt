package fi.omasaasahko.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fi.omasaasahko.domain.Condition
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WeatherSymbol(condition: Condition, night: Boolean, modifier: Modifier, description: String = condition.label) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val cloud = if (dark) Color(0xFFD0E5F7) else Color(0xFF6589B3)
    val rearCloud = if (dark) Color(0xFF7EAACF) else Color(0xFFAAC8E5)
    val rain = if (dark) Color(0xFF61DBFF) else Color(0xFF087FB7)
    val snow = if (dark) Color(0xFFE6F7FF) else Color(0xFF427CBB)
    val sun = if (dark) Color(0xFFFFD16D) else Color(0xFFECAA1F)
    Canvas(modifier.semantics { contentDescription = description + if (night) ", yö" else "" }) {
        scale(size.width / 100f, size.height / 100f, pivot = Offset.Zero) {
            fun sunshine(x: Float, y: Float, radius: Float) {
                if (night) {
                    val moon = Path().apply {
                        moveTo(x + radius * 0.4f, y - radius)
                        cubicTo(x - radius * 1.6f, y - radius, x - radius * 1.3f, y + radius * 1.6f, x + radius, y + radius * 0.55f)
                        cubicTo(x - radius * 0.25f, y + radius * 0.7f, x - radius * 0.6f, y - radius * 0.1f, x + radius * 0.4f, y - radius)
                        close()
                    }
                    drawPath(moon, sun)
                } else {
                    for (i in 0..7) {
                        val a = i * Math.PI / 4
                        drawLine(sun, Offset(x + cos(a).toFloat() * (radius + 5), y + sin(a).toFloat() * (radius + 5)),
                            Offset(x + cos(a).toFloat() * (radius + 11), y + sin(a).toFloat() * (radius + 11)), 3f, StrokeCap.Round)
                    }
                    drawCircle(sun, radius, Offset(x, y))
                }
            }
            fun cloudShape(x: Float, y: Float, color: Color, scale: Float = 1f) {
                val path = Path().apply {
                    moveTo(x + 8 * scale, y + 26 * scale)
                    cubicTo(x - 9 * scale, y + 26 * scale, x - 9 * scale, y + 4 * scale, x + 8 * scale, y + 4 * scale)
                    cubicTo(x + 12 * scale, y - 16 * scale, x + 42 * scale, y - 16 * scale, x + 46 * scale, y + 5 * scale)
                    cubicTo(x + 68 * scale, y + 1 * scale, x + 72 * scale, y + 26 * scale, x + 53 * scale, y + 26 * scale)
                    close()
                }
                drawPath(path, color)
            }
            when (condition) {
                Condition.CLEAR -> sunshine(50f, 49f, 22f)
                Condition.MOSTLY_CLEAR -> {
                    sunshine(43f, 40f, 21f)
                    cloudShape(44f, 57f, cloud, 0.55f)
                }
                Condition.UNKNOWN -> {
                    drawCircle(rearCloud, 23f, Offset(50f, 48f), style = Stroke(2f))
                    drawLine(cloud, Offset(42f, 48f), Offset(58f, 48f), 3f, StrokeCap.Round)
                }
                else -> {
                    if (condition == Condition.PARTLY_CLOUDY || condition == Condition.MOSTLY_CLOUDY)
                        sunshine(38f, 34f, if (condition == Condition.PARTLY_CLOUDY) 17f else 12f)
                    else cloudShape(25f, 31f, rearCloud, 0.8f)
                    cloudShape(19f, 42f, cloud)
                    when (condition) {
                        Condition.RAIN, Condition.FREEZING_RAIN -> for (i in 0..2) {
                            drawLine(rain, Offset(31f + i * 18, 76f), Offset(27f + i * 18, 86f), 4f, StrokeCap.Round)
                        }
                        Condition.DRIZZLE, Condition.FREEZING_DRIZZLE -> for (i in 0..2) {
                            drawCircle(rain, 2f, Offset(29f + i * 20, 79f))
                            drawCircle(rain, 2f, Offset(26f + i * 20, 88f))
                        }
                        Condition.HAIL -> for (i in 0..2) drawCircle(snow, 4f, Offset(29f + i * 20, 81f), style = Stroke(2.5f))
                        Condition.SLEET -> {
                            drawLine(rain, Offset(31f, 76f), Offset(27f, 86f), 4f, StrokeCap.Round)
                            drawLine(rain, Offset(67f, 76f), Offset(63f, 86f), 4f, StrokeCap.Round)
                            drawLine(snow, Offset(44f, 81f), Offset(54f, 81f), 2f)
                            drawLine(snow, Offset(49f, 76f), Offset(49f, 86f), 2f)
                        }
                        Condition.SNOW -> for (i in 0..2) {
                            val x = 29f + i * 20
                            drawLine(snow, Offset(x - 4, 80f), Offset(x + 4, 80f), 2f, StrokeCap.Round)
                            drawLine(snow, Offset(x, 76f), Offset(x, 84f), 2f, StrokeCap.Round)
                        }
                        Condition.THUNDER, Condition.THUNDER_HAIL -> drawPath(Path().apply {
                            moveTo(50f, 64f); lineTo(39f, 80f); lineTo(49f, 80f); lineTo(44f, 95f)
                            lineTo(62f, 73f); lineTo(51f, 73f); close()
                        }, sun)
                        Condition.FOG -> {
                            drawLine(rearCloud, Offset(20f, 78f), Offset(77f, 78f), 3f, StrokeCap.Round)
                            drawLine(rearCloud, Offset(30f, 86f), Offset(67f, 86f), 3f, StrokeCap.Round)
                        }
                        else -> Unit
                    }
                    if (condition == Condition.FREEZING_RAIN || condition == Condition.FREEZING_DRIZZLE) {
                        drawLine(snow, Offset(28f, 94f), Offset(70f, 94f), 2.5f, StrokeCap.Round)
                        drawLine(snow, Offset(37f, 91f), Offset(40f, 97f), 2f)
                        drawLine(snow, Offset(57f, 91f), Offset(60f, 97f), 2f)
                    }
                    if (condition == Condition.THUNDER_HAIL) {
                        drawCircle(snow, 4f, Offset(28f, 83f), style = Stroke(2.5f))
                        drawCircle(snow, 4f, Offset(73f, 83f), style = Stroke(2.5f))
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandArrow(expanded: Boolean, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "Viikon päivän nuoli")
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(28.dp)) {
        withTransform({ rotate(rotation) }) {
            val path = Path().apply {
                moveTo(size.width * 0.22f, size.height * 0.35f)
                lineTo(size.width * 0.5f, size.height * 0.65f)
                lineTo(size.width * 0.78f, size.height * 0.35f)
            }
            drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun Notice(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Block(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        radius = Radius.tile, padding = PaddingValues(16.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
fun ChoiceRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.clip(PillShape).background(scheme.surfaceContainerHigh).padding(4.dp).selectableGroup()) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(PillShape)
                .background(if (active) scheme.inverseSurface else Color.Transparent)
                .selectable(selected = active, role = Role.RadioButton, onClick = { onSelect(index) })
                .padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    color = if (active) scheme.inverseOnSurface else scheme.onSurfaceVariant)
            }
        }
    }
}
