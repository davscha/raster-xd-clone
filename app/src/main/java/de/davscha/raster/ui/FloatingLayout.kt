package de.davscha.raster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.raster.EditorState
import de.davscha.raster.Mode
import kotlin.math.roundToInt

/** Variante B wie in Lasur und Kontur: Arbeitsfläche im Vollbild, Werkzeuge schweben darüber. */
@Composable
fun FloatingLayout(state: EditorState, actions: EditorActions) {
    BoxWithConstraints(Modifier.fillMaxSize().background(RasterColors.FloatingBackground)) {
        RasterCanvas(state, Modifier.fillMaxSize())
        val narrow = maxWidth < 1000.dp

        // Oben links: Datei, Ansicht, Vorschau
        Pill(Modifier.align(Alignment.TopStart).padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolButton(RasterIcons.NewFile, "Neues Projekt") { state.showNew = true }
                ToolButton(RasterIcons.Open, "Öffnen") { actions.open() }
                ToolButton(RasterIcons.Save, "Speichern") { actions.save() }
                ToolButton(RasterIcons.SaveAs, "Speichern unter") { actions.saveAs() }
                ToolButton(RasterIcons.Fit, "Alles zeigen") { state.fitAll() }
                ToolButton(RasterIcons.Play, "Vorschau") { state.openPreview() }
                ToolButton(RasterIcons.Export, "Exportieren") { actions.export() }
                ToolButton(RasterIcons.Settings, "Einstellungen") { state.showSettings = true }
                Spacer(Modifier.width(4.dp))
                Mode.entries.forEach { m -> Choice(m.label, state.mode == m) { state.switchMode(m) } }
            }
        }

        // Oben rechts: Werkzeuge. Schmal (Hochformat): unter die Dateileiste rücken
        Pill(Modifier.align(Alignment.TopEnd).padding(start = 12.dp, end = 12.dp, top = if (narrow) 72.dp else 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolButtons(state)
                ToolButton(RasterIcons.Multi, "Mehrfachauswahl", selected = state.multiSelect) { state.multiSelect = !state.multiSelect }
                ToolButton(RasterIcons.Layers, "Fenster", selected = state.panelOpen) { state.panelOpen = !state.panelOpen }
            }
        }

        // Links: Rückgängig für den Daumen
        Pill(Modifier.align(Alignment.CenterStart).padding(start = 12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ToolButton(RasterIcons.Undo, "Rückgängig", enabled = state.canUndo) { state.undo() }
                ToolButton(RasterIcons.Redo, "Wiederholen", enabled = state.canRedo) { state.redo() }
            }
        }

        // Ebenen, Bausteine, Komponenten und Eigenschaften als verschiebbares Fenster:
        // an der Kopfzeile ziehen, doppelt tippen setzt es zurück
        if (state.panelOpen) {
            val density = LocalDensity.current
            val panelW = with(density) { 350.dp.toPx() }
            val margin = with(density) { 12.dp.toPx() }
            val topDefault = with(density) { (if (narrow) 136.dp else 76.dp).toPx() }
            val areaW = with(density) { maxWidth.toPx() }
            val areaH = with(density) { maxHeight.toPx() }
            val pos = state.panelPos ?: Offset(areaW - panelW - margin, topDefault)
            val x = pos.x.coerceIn(0f, (areaW - panelW).coerceAtLeast(0f))
            val y = pos.y.coerceIn(0f, (areaH - with(density) { 120.dp.toPx() }).coerceAtLeast(0f))
            Surface(
                modifier = Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .width(350.dp)
                    .heightIn(max = (maxHeight - with(density) { y.toDp() } - 12.dp).coerceIn(160.dp, 760.dp)),
                shape = RoundedCornerShape(20.dp),
                color = RasterColors.FloatingSurface,
                shadowElevation = 12.dp,
            ) {
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { state.panelPos = null }) }
                            .pointerInput(Unit) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    val cur = state.panelPos ?: Offset(x, y)
                                    state.panelPos = cur + drag
                                }
                            }
                            .padding(start = 14.dp, end = 8.dp, top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(RasterIcons.Grip, contentDescription = "Fenster verschieben", tint = RasterColors.Muted, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Fenster", fontSize = 13.sp, color = RasterColors.Muted)
                        Spacer(Modifier.weight(1f))
                        ToolButton(RasterIcons.Close, "Fenster schließen") { state.panelOpen = false }
                    }
                    Column(
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SidePanel(state)
                    }
                }
            }
        }

        ComponentEditBar(state, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}
