package de.davscha.raster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.raster.EditorState
import de.davscha.raster.Mode
import de.davscha.raster.Tool
import kotlin.math.roundToInt

class EditorActions(
    val export: () -> Unit,
    val save: () -> Unit,
    val saveAs: () -> Unit,
    val open: () -> Unit,
)

/** Feste Oberfläche wie das Studio-Layout von Lasur und Kontur: Werkzeuge links, Fenster rechts. */
@Composable
fun StudioLayout(state: EditorState, actions: EditorActions) {
    Column(Modifier.fillMaxSize().background(RasterColors.StudioBackground)) {
        // Kopfzeile
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(RasterColors.StudioPanel)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ToolButton(RasterIcons.NewFile, "Neues Projekt") { state.showNew = true }
            ToolButton(RasterIcons.Open, "Öffnen") { actions.open() }
            ToolButton(RasterIcons.Save, "Speichern") { actions.save() }
            ToolButton(RasterIcons.SaveAs, "Speichern unter") { actions.saveAs() }
            Column(Modifier.widthIn(max = 170.dp).padding(start = 6.dp)) {
                state.revision
                Text(
                    state.fileName ?: "Unbenannt",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = RasterColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${state.doc.screens.size} Screens · ${(state.view.zoom * 100).roundToInt()} %",
                    fontSize = 12.sp,
                    color = RasterColors.Muted,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            ToolButton(RasterIcons.Undo, "Rückgängig", enabled = state.canUndo) { state.undo() }
            ToolButton(RasterIcons.Redo, "Wiederholen", enabled = state.canRedo) { state.redo() }
            Spacer(Modifier.weight(1f))
            // Design oder Prototyp, wie die Reiter oben in XD
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Mode.entries.forEach { m -> Choice(m.label, state.mode == m) { state.switchMode(m) } }
            }
            Spacer(Modifier.width(8.dp))
            ToolButton(RasterIcons.Fit, "Alles zeigen") { state.fitAll() }
            ToolButton(RasterIcons.Play, "Vorschau") { state.openPreview() }
            ToolButton(RasterIcons.Settings, "Einstellungen") { state.showSettings = true }
            Button(onClick = actions.export, modifier = Modifier.padding(start = 4.dp)) {
                Icon(RasterIcons.Export, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Exportieren", maxLines = 1, softWrap = false)
            }
        }
        HorizontalDivider(color = RasterColors.StudioLine)

        Row(Modifier.weight(1f)) {
            // Werkzeugleiste
            Column(
                Modifier
                    .width(68.dp)
                    .fillMaxHeight()
                    .background(RasterColors.StudioPanel)
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ToolButtons(state)
                Spacer(Modifier.weight(1f))
                ToolButton(RasterIcons.Multi, "Mehrfachauswahl", selected = state.multiSelect) { state.multiSelect = !state.multiSelect }
            }
            VerticalDivider(color = RasterColors.StudioLine)

            // Arbeitsfläche
            Box(Modifier.weight(1f).fillMaxHeight()) {
                RasterCanvas(state, Modifier.fillMaxSize())
                ComponentEditBar(state, Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
            VerticalDivider(color = RasterColors.StudioLine)

            // Ebenen, Bausteine, Komponenten und Eigenschaften
            Column(
                Modifier
                    .width(360.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SidePanel(state)
            }
        }
    }
}

/** Leiste unten, solange eine Komponente bearbeitet wird. */
@Composable
fun ComponentEditBar(state: EditorState, modifier: Modifier = Modifier) {
    val comp = state.editingComponent?.let { state.doc.component(it) } ?: return
    ContextBar(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(RasterIcons.Component, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            Text(
                "Komponente „${comp.name}“ bearbeiten",
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Button(onClick = { state.finishComponentEdit() }) { Text("Fertig") }
        }
    }
}

/** Werkzeuge in der Reihenfolge der XD-Werkzeugleiste. */
@Composable
fun ToolButtons(state: EditorState) {
    val design = state.mode == Mode.DESIGN
    ToolButton(RasterIcons.Select, "Auswahl", selected = state.tool == Tool.SELECT) { state.selectTool(Tool.SELECT) }
    ToolButton(RasterIcons.Rect, "Rechteck", enabled = design, selected = state.tool == Tool.RECT) { state.selectTool(Tool.RECT) }
    ToolButton(RasterIcons.Ellipse, "Ellipse", enabled = design, selected = state.tool == Tool.ELLIPSE) { state.selectTool(Tool.ELLIPSE) }
    ToolButton(RasterIcons.Line, "Linie", enabled = design, selected = state.tool == Tool.LINE) { state.selectTool(Tool.LINE) }
    ToolButton(RasterIcons.TextTool, "Text", enabled = design, selected = state.tool == Tool.TEXT) { state.selectTool(Tool.TEXT) }
    ToolButton(
        RasterIcons.Screen, "Screen",
        enabled = design && state.editingComponent == null,
        selected = state.tool == Tool.SCREEN,
    ) { state.selectTool(Tool.SCREEN) }
}
