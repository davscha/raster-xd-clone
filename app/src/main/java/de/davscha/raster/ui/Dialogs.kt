package de.davscha.raster.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.raster.ColorRequest
import de.davscha.raster.EditorState
import de.davscha.raster.io.ImageFormat
import de.davscha.raster.io.RasterJson
import de.davscha.raster.model.Board
import de.davscha.raster.model.ScreenPreset

private val palette = listOf(
    0xFF000000, 0xFF1C1D21, 0xFF4A4C54, 0xFF8E909A, 0xFFC7C9D1, 0xFFE9EAEE, 0xFFFFFFFF,
    0xFF3B7CF0, 0xFF1E88E5, 0xFF29B6F6, 0xFF00ACC1, 0xFF26A69A, 0xFF2FB67C, 0xFF7CB342,
    0xFFFDD835, 0xFFFFB300, 0xFFFF8A4C, 0xFFF4511E, 0xFFE53935, 0xFFD81B60, 0xFF8E24AA,
    0xFFE3F2FD, 0xFFE8F5E9, 0xFFFFF8E1, 0xFFFCE4EC, 0xFFF3E5F5, 0xFFEDE7F6, 0xFFFFF3E0,
).map { it.toInt() }

private fun hexOf(c: Int): String = if ((c ushr 24) == 0xFF) "#%06X".format(c and 0xFFFFFF) else "#%08X".format(c)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(req: ColorRequest, onDismiss: () -> Unit) {
    var color by remember { mutableIntStateOf(req.initial ?: 0xFF3B7CF0.toInt()) }
    var hex by remember { mutableStateOf(hexOf(color)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(req.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorChip(color, Modifier.size(52.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)))
                    Spacer(Modifier.width(12.dp))
                    TextInput("Hex", hex) { v ->
                        hex = v
                        RasterJson.parseColor(v)?.let { color = it }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in palette) {
                        ColorChip(
                            c,
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .border(if (c == color) 3.dp else 1.dp, if (c == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable(role = Role.Button) {
                                    color = c
                                    hex = hexOf(c)
                                },
                        )
                    }
                }
                val alpha = (color ushr 24) / 255f
                LabeledSlider("Deckkraft", "${(alpha * 100).toInt()} %", alpha, 0f..1f) { a ->
                    color = ((a * 255).toInt() shl 24) or (color and 0xFFFFFF)
                    hex = hexOf(color)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                req.onPick(color)
                onDismiss()
            }) { Text("Übernehmen") }
        },
        dismissButton = {
            Row {
                if (req.allowNone) {
                    TextButton(onClick = {
                        req.onPick(null)
                        onDismiss()
                    }) { Text("Keine") }
                }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}

@Composable
fun SettingsDialog(state: EditorState, onDismiss: () -> Unit) {
    val s = state.settings
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einstellungen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledSwitch("Auch mit dem Finger zeichnen", s.fingerDraws) { s.fingerDraws = it }
                Hint("Aus: Nur der Stift zeichnet und zieht Auswahlrechtecke auf. Finger verschieben und zoomen die Ansicht.")
                LabeledSwitch("Einrasten", s.snap) { s.snap = it }
                Hint("Beim Verschieben und Skalieren an Screen-Kanten, Mitten und anderen Elementen ausrichten.")
                Text("Raster 0.1.0", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewDocumentDialog(state: EditorState, onDismiss: () -> Unit) {
    var preset by remember { mutableStateOf(ScreenPreset.TABLET_LANDSCAPE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neues Projekt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Hint("Größe des ersten Screens. Weitere Screens kommen über das Screen-Werkzeug oder das Plus in der Ebenenliste dazu.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (p in ScreenPreset.entries) Choice("${p.label} ${p.w}×${p.h}", preset == p) { preset = p }
                }
                if (state.dirty) Hint("Nicht gespeicherte Änderungen gehen verloren.")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                state.newDocument(preset)
                onDismiss()
            }) { Text("Anlegen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportDialog(state: EditorState, onDismiss: () -> Unit, onExport: (List<Board>, ImageFormat, Float) -> Unit) {
    var all by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(1f) }
    var format by remember { mutableStateOf(ImageFormat.PNG) }
    val active = state.activeBoard()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exportieren") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Was", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Choice("Aktiver Screen", !all) { all = false }
                    if (state.editingComponent == null) Choice("Alle Screens (${state.doc.screens.size})", all) { all = true }
                }
                Text("Format", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (f in ImageFormat.entries) Choice(f.label, format == f) { format = f }
                }
                Text("Größe", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (s in listOf(0.5f, 1f, 2f, 3f)) Choice("${formatNum(s)}×", scale == s) { scale = s }
                }
                Hint("Die Bilder landen in „Bilder/Raster“.")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val list = if (all && state.editingComponent == null) state.doc.screens.toList() else listOfNotNull(active)
                onExport(list, format, scale)
            }) { Text("Exportieren") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
