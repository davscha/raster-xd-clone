package de.davscha.raster.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 44.dp,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (selected) scheme.primary else Color.Transparent
    val fg = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.35f)
        selected -> scheme.onPrimary
        else -> scheme.onSurface
    }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
    }
}

/** Taste, die beim Gedrückthalten wiederholt (Feinjustieren). */
@Composable
fun RepeatButton(icon: ImageVector, label: String, size: Dp = 40.dp, onStep: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        step()
                        val job = launch {
                            delay(400)
                            while (true) {
                                step()
                                delay(60)
                            }
                        }
                        waitForUpOrCancellation()
                        job.cancel()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
    }
}

/** Kleines Farbfeld; `null` wird als „keine Farbe“ (rot durchgestrichen) gezeichnet. */
@Composable
fun ColorChip(color: Int?, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = 6.dp.toPx()
        // Karomuster für durchsichtige Farben
        drawRoundRect(Color.White, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
        if (color == null) {
            drawLine(Color(0xFFE53935), Offset(size.width * 0.15f, size.height * 0.85f), Offset(size.width * 0.85f, size.height * 0.15f), 3.dp.toPx(), StrokeCap.Round)
        } else {
            val c = 5.dp.toPx()
            var y = 0f
            var row = 0
            while (y < size.height) {
                var x = if (row % 2 == 0) 0f else c
                while (x < size.width) {
                    drawRect(Color(0xFFCFCFCF), Offset(x, y), androidx.compose.ui.geometry.Size(c, c))
                    x += 2 * c
                }
                y += c
                row++
            }
            drawRoundRect(Color(color), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
        }
    }
}

@Composable
fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(valueText, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
fun LabeledSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun Hint(text: String) {
    Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Kleine schwebende Leiste mit abgerundeten Ecken. */
@Composable
fun Pill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = RasterColors.FloatingPill,
        border = BorderStroke(1.dp, RasterColors.FloatingLine),
    ) {
        Box(Modifier.padding(4.dp)) { content() }
    }
}

/** Kleiner Auswahl-Knopf, ausgewählt in Akzentfarbe. */
@Composable
fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) scheme.primary else scheme.surfaceVariant)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 13.sp, color = if (selected) scheme.onPrimary else scheme.onSurface)
    }
}

/** Symbol mit Beschriftung darunter (Kontextleisten). */
@Composable
fun ActionButton(icon: ImageVector, label: String, enabled: Boolean = true, selected: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fg = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.35f)
        selected -> scheme.onPrimary
        else -> scheme.onSurface
    }
    Column(
        Modifier
            .width(66.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) scheme.primary else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, lineHeight = 12.sp, color = fg, textAlign = TextAlign.Center)
    }
}

/** Leiste am unteren Rand, wie die Kontextleisten in Lasur. */
@Composable
fun ContextBar(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Box(Modifier.padding(6.dp)) { content() }
    }
}
