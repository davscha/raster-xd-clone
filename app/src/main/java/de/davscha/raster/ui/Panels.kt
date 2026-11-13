package de.davscha.raster.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.raster.AlignDir
import de.davscha.raster.ColorRequest
import de.davscha.raster.EditorState
import de.davscha.raster.Mode
import de.davscha.raster.PanelTab
import de.davscha.raster.io.RasterJson
import de.davscha.raster.model.Board
import de.davscha.raster.model.Element
import de.davscha.raster.model.HAlign
import de.davscha.raster.model.Kind
import de.davscha.raster.model.Link
import de.davscha.raster.model.ScreenPreset
import de.davscha.raster.model.Transition
import de.davscha.raster.model.Widgets

fun kindIcon(k: Kind): ImageVector = when (k) {
    Kind.RECT -> RasterIcons.Rect
    Kind.ELLIPSE -> RasterIcons.Ellipse
    Kind.LINE -> RasterIcons.Line
    Kind.TEXT -> RasterIcons.TextTool
    Kind.BUTTON -> RasterIcons.Button
    Kind.INPUT -> RasterIcons.Input
    Kind.SLIDER -> RasterIcons.Slider
    Kind.SWITCH -> RasterIcons.Toggle
    Kind.CHECKBOX -> RasterIcons.Checkbox
    Kind.LIST -> RasterIcons.ListIcon
    Kind.CARD -> RasterIcons.Card
    Kind.NAVBAR -> RasterIcons.NavBar
    Kind.INSTANCE -> RasterIcons.Instance
}

/** Rechtes Fenster: oben Ebenen, Bausteine oder Komponenten, darunter die Eigenschaften. */
@Composable
fun SidePanel(state: EditorState) {
    state.revision
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelTab.entries.forEach { t -> Choice(t.label, state.tab == t) { state.tab = t } }
    }
    when (state.tab) {
        PanelTab.LAYERS -> LayersSection(state)
        PanelTab.WIDGETS -> WidgetsSection(state)
        PanelTab.COMPONENTS -> ComponentsSection(state)
    }
    HorizontalDivider(color = RasterColors.StudioLine)
    if (state.mode == Mode.PROTOTYPE) PrototypePanel(state) else PropertiesPanel(state)
}

// ---------------------------------------------------------------- Ebenen

@Composable
private fun LayersSection(state: EditorState) {
    state.revision
    val comp = state.editingComponent
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle(if (comp != null) "Komponente" else "Screens")
        Spacer(Modifier.weight(1f))
        if (comp != null) {
            Button(onClick = { state.finishComponentEdit() }) { Text("Fertig") }
        } else {
            ToolButton(RasterIcons.Plus, "Neuer Screen", size = 36.dp) { state.addScreen() }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (b in state.boards()) {
            val active = b.id == state.boardId
            ListRow(
                icon = if (comp != null) RasterIcons.Component else RasterIcons.Screen,
                title = state.title(b),
                subtitle = "${b.children.size} Elemente",
                selected = active && state.boardSelected,
                highlighted = active,
                onClick = { state.focusBoard(b) },
            )
            if (active) {
                for (e in b.children.asReversed()) ElementRow(state, b, e)
            }
        }
    }
}

@Composable
private fun ElementRow(state: EditorState, b: Board, e: Element) {
    val selected = e.id in state.selection
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) scheme.primary.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(role = Role.Button) { if (!e.locked) state.select(b, e, additive = state.multiSelect) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            kindIcon(e.kind), contentDescription = null, modifier = Modifier.size(18.dp),
            tint = if (e.kind == Kind.INSTANCE) Color(0xFF2FB67C) else scheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            e.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            color = if (e.visible) scheme.onSurface else scheme.onSurfaceVariant,
        )
        if (e.link != null) Icon(RasterIcons.Link, contentDescription = "Hat Verbindung", tint = RasterColors.Prototype, modifier = Modifier.size(16.dp))
        ToolButton(if (e.visible) RasterIcons.Eye else RasterIcons.EyeOff, "Sichtbar", size = 34.dp) { state.toggleVisible(e) }
        ToolButton(if (e.locked) RasterIcons.Lock else RasterIcons.Unlock, "Sperren", size = 34.dp) { state.toggleLocked(e) }
    }
}

@Composable
private fun ListRow(icon: ImageVector, title: String, subtitle: String, selected: Boolean, highlighted: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    selected -> scheme.primary.copy(alpha = 0.35f)
                    highlighted -> scheme.surfaceVariant
                    else -> Color.Transparent
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = scheme.onSurface)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = scheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- Bausteine

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetsSection(state: EditorState) {
    SectionTitle("UI-Bausteine")
    Hint("Antippen setzt den Baustein in die Mitte des aktiven Screens.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (k in Widgets.library) ActionButton(kindIcon(k), k.label) { state.insertWidget(k) }
    }
    SectionTitle("Grundformen")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (k in listOf(Kind.RECT, Kind.ELLIPSE, Kind.LINE, Kind.TEXT)) ActionButton(kindIcon(k), k.label) { state.insertWidget(k) }
    }
}

// ---------------------------------------------------------------- Komponenten

@Composable
private fun ComponentsSection(state: EditorState) {
    state.revision
    val doc = state.doc
    SectionTitle("Komponenten")
    Hint("Einmal bauen, mehrfach verwenden. Änderungen an der Komponente wirken auf alle Instanzen.")
    Button(
        onClick = { state.makeComponent() },
        enabled = state.selection.isNotEmpty() && state.editingComponent == null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(RasterIcons.Component, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Aus Auswahl erstellen")
    }
    if (doc.components.isEmpty()) Hint("Noch keine Komponenten.")
    for (c in doc.components) {
        val uses = doc.allBoards().sumOf { b -> b.children.count { it.componentId == c.id } }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (state.editingComponent == c.id) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(
                Modifier
                    .size(72.dp, 48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFE9EAEE)),
            ) {
                state.revision
                drawIntoCanvas { state.renderer.drawThumb(it.nativeCanvas, c, doc, size.width, size.height) }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                Text("$uses Instanzen", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ToolButton(RasterIcons.Plus, "Instanz einfügen", size = 36.dp, enabled = state.editingComponent != c.id) { state.insertInstance(c.id) }
            ToolButton(RasterIcons.Edit, "Bearbeiten", size = 36.dp) { state.editComponent(c.id) }
            ToolButton(RasterIcons.Trash, "Löschen", size = 36.dp) { state.deleteComponent(c.id) }
        }
    }
}

// ---------------------------------------------------------------- Eigenschaften

@Composable
private fun PropertiesPanel(state: EditorState) {
    state.revision
    val b = state.activeBoard() ?: return
    val els = state.selectedElements()
    when {
        state.boardSelected || els.isEmpty() -> BoardProps(state, b)
        els.size == 1 -> ElementProps(state, b, els[0])
        else -> MultiProps(state, els)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardProps(state: EditorState, b: Board) {
    val isComp = state.editingComponent != null
    SectionTitle(if (isComp) "Komponente" else "Screen")
    if (!state.boardSelected) Hint("Titel über dem Screen antippen, um ihn zu verschieben oder seine Größe zu ändern.")
    TextInput("Name", b.name, Modifier.fillMaxWidth()) { v -> state.edit("name-${b.id}") { b.name = v } }
    if (!isComp) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (p in ScreenPreset.entries) {
                Choice("${p.label} ${p.w}×${p.h}", b.w.toInt() == p.w && b.h.toInt() == p.h) {
                    state.edit("size") { b.w = p.w.toFloat(); b.h = p.h.toFloat() }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!isComp) {
            NumberField("X", b.x, Modifier.weight(1f)) { v -> state.edit("bx") { b.x = v } }
            NumberField("Y", b.y, Modifier.weight(1f)) { v -> state.edit("by") { b.y = v } }
        }
        NumberField("B", b.w, Modifier.weight(1f)) { v -> state.edit("bw") { b.w = v.coerceAtLeast(1f) } }
        NumberField("H", b.h, Modifier.weight(1f)) { v -> state.edit("bh") { b.h = v.coerceAtLeast(1f) } }
    }
    ColorButton("Hintergrund", b.background) {
        state.colorRequest = ColorRequest("Hintergrund", b.background, allowNone = isComp) { c -> state.edit("bg") { b.background = c } }
    }
    if (!isComp) {
        LabeledSwitch("Start-Screen der Vorschau", state.doc.startScreen == b.id) { if (it) state.setStart(b) }
        if (state.boardSelected) {
            OutlinedButton(onClick = { state.delete() }, enabled = state.doc.screens.size > 1) {
                Icon(RasterIcons.Trash, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Screen löschen")
            }
        }
    }
}

@Composable
private fun ElementProps(state: EditorState, b: Board, e: Element) {
    SectionTitle(e.kind.label)
    TextInput("Name", e.name, Modifier.fillMaxWidth()) { v -> state.edit("name-${e.id}") { e.name = v } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField("X", e.x, Modifier.weight(1f)) { v -> state.editSelected("x") { it.x = v } }
        NumberField("Y", e.y, Modifier.weight(1f)) { v -> state.editSelected("y") { it.y = v } }
        NumberField("B", e.w, Modifier.weight(1f)) { v -> state.editSelected("w") { it.w = if (it.kind == Kind.LINE) v else v.coerceAtLeast(1f) } }
        NumberField("H", e.h, Modifier.weight(1f)) { v -> state.editSelected("h") { it.h = if (it.kind == Kind.LINE) v else v.coerceAtLeast(1f) } }
    }
    ActionsRow(state)
    AlignRow(state)

    if (e.kind == Kind.INSTANCE) {
        InstanceProps(state, e)
        return
    }

    SectionTitle("Aussehen")
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (e.kind != Kind.LINE) ColorButton("Fläche", e.fill) { pickColor(state, "Fläche", e.fill, true) { el, c -> el.fill = c } }
        ColorButton(if (e.kind == Kind.SLIDER || e.kind == Kind.SWITCH) "Aus / Spur" else "Kontur", e.stroke) {
            pickColor(state, "Kontur", e.stroke, true) { el, c -> el.stroke = c }
        }
    }
    if (e.kind !in setOf(Kind.SLIDER, Kind.SWITCH)) {
        LabeledSlider("Konturstärke", "${formatNum(e.strokeWidth)} px", e.strokeWidth, 0f..24f) { v ->
            state.editSelected("sw") { it.strokeWidth = v }
        }
    }
    if (e.kind.hasRadius) {
        val max = (minOf(e.w, e.h) / 2f).coerceAtLeast(1f)
        LabeledSlider("Eckenradius", "${formatNum(e.radius)} px", e.radius, 0f..max) { v -> state.editSelected("radius") { it.radius = v } }
    }
    LabeledSlider("Deckkraft", "${(e.opacity * 100).toInt()} %", e.opacity, 0f..1f) { v -> state.editSelected("opacity") { it.opacity = v } }
    if (e.kind !in setOf(Kind.LINE, Kind.TEXT, Kind.SLIDER, Kind.SWITCH, Kind.CHECKBOX)) {
        LabeledSwitch("Schatten", e.shadow) { v -> state.editSelected("shadow") { it.shadow = v } }
    }

    when (e.kind) {
        Kind.SLIDER -> LabeledSlider("Wert", "${(e.value * 100).toInt()} %", e.value, 0f..1f) { v -> state.editSelected("value") { it.value = v } }
        Kind.SWITCH, Kind.CHECKBOX -> LabeledSwitch("Eingeschaltet", e.on) { v -> state.editSelected("value") { it.value = if (v) 1f else 0f } }
        else -> Unit
    }

    if (e.kind.hasText) TextProps(state, e)
    if (e.kind == Kind.INPUT) {
        TextInput("Platzhalter", e.placeholder, Modifier.fillMaxWidth()) { v -> state.edit("ph-${e.id}") { e.placeholder = v } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextProps(state: EditorState, e: Element) {
    SectionTitle("Text")
    val multi = e.kind == Kind.TEXT || e.kind == Kind.LIST
    TextInput(if (e.kind == Kind.LIST) "Einträge (ein Eintrag pro Zeile)" else "Text", e.text, Modifier.fillMaxWidth(), singleLine = !multi) { v ->
        state.edit("text-${e.id}") {
            e.text = v
            state.fitText(e)
        }
    }
    LabeledSlider("Schriftgröße", "${formatNum(e.textSize)} px", e.textSize, 6f..120f) { v ->
        state.editSelected("ts") { it.textSize = Math.round(v).toFloat() }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ColorButton("Textfarbe", e.textColor) { pickColor(state, "Textfarbe", e.textColor, false) { el, c -> el.textColor = c ?: Element.DARK } }
        Spacer(Modifier.weight(1f))
        Choice("Fett", e.bold) { state.editSelected("bold") { it.bold = !e.bold } }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (a in HAlign.entries) Choice(a.label, e.align == a) { state.editSelected("align") { it.align = a } }
    }
}

@Composable
private fun InstanceProps(state: EditorState, e: Element) {
    val comp = state.doc.component(e.componentId)
    SectionTitle("Instanz von ${comp?.name ?: "?"}")
    if (comp == null) {
        Hint("Die Komponente gibt es nicht mehr.")
        return
    }
    Hint("Texte und Farben lassen sich hier überschreiben. Alles andere kommt von der Komponente.")
    for (c in comp.children) {
        if (c.kind.hasText) {
            val key = "${c.id}.text"
            TextInput(c.name, e.overrides[key] ?: c.text, Modifier.fillMaxWidth(), singleLine = c.kind != Kind.LIST && c.kind != Kind.TEXT) { v ->
                state.setOverride(e, key, if (v == c.text) null else v)
            }
        }
        if (c.fill != null && c.kind != Kind.LINE) {
            val key = "${c.id}.fill"
            val current = e.overrides[key]?.let { RasterJson.parseColor(it) } ?: c.fill
            ColorButton("Farbe: ${c.name}", current) {
                state.colorRequest = ColorRequest("Farbe: ${c.name}", current, allowNone = false) { col ->
                    state.setOverride(e, key, col?.let { "#%08X".format(it) })
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { state.editComponent(comp.id) }) { Text("Komponente bearbeiten") }
        OutlinedButton(onClick = { state.edit("ov-reset") { e.overrides.clear() } }, enabled = e.overrides.isNotEmpty()) { Text("Zurücksetzen") }
    }
    OutlinedButton(onClick = { state.detach(e) }) {
        Icon(RasterIcons.Detach, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Lösen (in Elemente umwandeln)")
    }
}

@Composable
private fun MultiProps(state: EditorState, els: List<Element>) {
    SectionTitle("${els.size} Elemente")
    Hint("Ausrichten bezieht sich auf die Auswahl. Ein einzelnes Element richtet sich am Screen aus.")
    ActionsRow(state)
    AlignRow(state)
    SectionTitle("Aussehen")
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ColorButton("Fläche", els.first().fill) { pickColor(state, "Fläche", els.first().fill, true) { el, c -> el.fill = c } }
        ColorButton("Kontur", els.first().stroke) { pickColor(state, "Kontur", els.first().stroke, true) { el, c -> el.stroke = c } }
    }
    val o = els.first().opacity
    LabeledSlider("Deckkraft", "${(o * 100).toInt()} %", o, 0f..1f) { v -> state.editSelected("opacity") { it.opacity = v } }
}

private fun pickColor(state: EditorState, title: String, initial: Int?, allowNone: Boolean, apply: (Element, Int?) -> Unit) {
    state.colorRequest = ColorRequest(title, initial, allowNone) { c -> state.editSelected("color-$title") { apply(it, c) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionsRow(state: EditorState) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ActionButton(RasterIcons.Duplicate, "Duplizieren") { state.duplicate() }
        ActionButton(RasterIcons.Copy, "Kopieren") { state.copy() }
        ActionButton(RasterIcons.Paste, "Einfügen", enabled = state.canPaste) { state.paste() }
        ActionButton(RasterIcons.Forward, "Nach vorne") { state.arrange(1) }
        ActionButton(RasterIcons.Backward, "Nach hinten") { state.arrange(-1) }
        ActionButton(RasterIcons.Component, "Komponente", enabled = state.editingComponent == null) { state.makeComponent() }
        ActionButton(RasterIcons.Trash, "Löschen") { state.delete() }
    }
}

@Composable
private fun AlignRow(state: EditorState) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        ToolButton(RasterIcons.AlignLeft, "Links ausrichten", size = 40.dp) { state.align(AlignDir.LEFT) }
        ToolButton(RasterIcons.AlignCenterH, "Horizontal mittig", size = 40.dp) { state.align(AlignDir.HCENTER) }
        ToolButton(RasterIcons.AlignRight, "Rechts ausrichten", size = 40.dp) { state.align(AlignDir.RIGHT) }
        ToolButton(RasterIcons.AlignTop, "Oben ausrichten", size = 40.dp) { state.align(AlignDir.TOP) }
        ToolButton(RasterIcons.AlignCenterV, "Vertikal mittig", size = 40.dp) { state.align(AlignDir.VCENTER) }
        ToolButton(RasterIcons.AlignBottom, "Unten ausrichten", size = 40.dp) { state.align(AlignDir.BOTTOM) }
    }
}

// ---------------------------------------------------------------- Prototyp

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrototypePanel(state: EditorState) {
    state.revision
    val doc = state.doc
    SectionTitle("Prototyp")
    if (state.editingComponent != null) {
        Hint("Verbindungen gibt es nur auf Screens. Erst die Komponente mit „Fertig“ schließen.")
        return
    }
    Hint("Element antippen, dann den orangen Punkt auf einen Screen ziehen. Oder unten ein Ziel wählen.")
    Button(onClick = { state.openPreview() }, modifier = Modifier.fillMaxWidth()) {
        Icon(RasterIcons.Play, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Vorschau starten")
    }
    SectionTitle("Start-Screen")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (s in doc.screens) Choice(s.name, doc.startScreen == s.id) { state.setStart(s) }
    }
    val e = state.singleSelected()
    if (e == null) {
        Hint("Kein Element ausgewählt.")
        return
    }
    Spacer(Modifier.height(4.dp))
    SectionTitle("Verbindung: ${e.name}")
    val link = e.link
    val transition = link?.transition ?: Transition.SLIDE_LEFT
    Text("Beim Antippen", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Choice("Nichts", link == null) { state.setLink(e, null, transition) }
        Choice("Zurück", link?.target == Link.BACK) { state.setLink(e, Link.BACK, transition) }
        for (s in doc.screens) if (s.id != state.boardId) {
            Choice("→ ${s.name}", link?.target == s.id) { state.setLink(e, s.id, transition) }
        }
    }
    if (link != null) {
        Text("Übergang", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (t in Transition.entries) Choice(t.label, link.transition == t) { state.setLink(e, link.target, t) }
        }
    }
}
