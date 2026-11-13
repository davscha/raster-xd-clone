package de.davscha.raster

import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import de.davscha.raster.io.RasterJson
import de.davscha.raster.model.Board
import de.davscha.raster.model.Document
import de.davscha.raster.model.Element
import de.davscha.raster.model.Kind
import de.davscha.raster.model.Link
import de.davscha.raster.model.ScreenPreset
import de.davscha.raster.model.Transition
import de.davscha.raster.model.Widgets
import de.davscha.raster.model.newId
import de.davscha.raster.render.Renderer
import de.davscha.raster.render.ViewState
import de.davscha.raster.settings.AppSettings
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class Tool(val label: String) {
    SELECT("Auswahl"),
    RECT("Rechteck"),
    ELLIPSE("Ellipse"),
    LINE("Linie"),
    TEXT("Text"),
    SCREEN("Screen"),
}

/** Gestalten (Elemente bearbeiten) oder Prototyp (Verbindungen zwischen Screens). */
enum class Mode(val label: String) { DESIGN("Design"), PROTOTYPE("Prototyp") }

enum class PanelTab(val label: String) { LAYERS("Ebenen"), WIDGETS("Bausteine"), COMPONENTS("Komponenten") }

enum class AlignDir { LEFT, HCENTER, RIGHT, TOP, VCENTER, BOTTOM }

/** Offener Farbwähler: Titel, Startfarbe und was mit der gewählten Farbe passiert. */
class ColorRequest(val title: String, val initial: Int?, val allowNone: Boolean, val onPick: (Int?) -> Unit)

/** Magentafarbene Hilfslinie beim Einrasten, in Dokumentkoordinaten. */
class Guide(val vertical: Boolean, val pos: Float)

const val HANDLE_MOVE = -1

class EditorState(val settings: AppSettings) {
    var doc: Document = Document.new(ScreenPreset.TABLET_LANDSCAPE.w.toFloat(), ScreenPreset.TABLET_LANDSCAPE.h.toFloat())
        private set

    /** Zählt jede Änderung am Modell. Composables, die Modellwerte anzeigen, müssen das lesen. */
    var revision by mutableIntStateOf(0)
        private set

    val view = ViewState()
    val renderer = Renderer()
    var density = 2f

    var tool by mutableStateOf(Tool.SELECT)
        private set
    var mode by mutableStateOf(Mode.DESIGN)
        private set
    var tab by mutableStateOf(PanelTab.LAYERS)

    /** Wird gerade eine Komponente bearbeitet? Dann zeigt die Arbeitsfläche nur sie. */
    var editingComponent by mutableStateOf<String?>(null)
        private set
    var boardId by mutableStateOf<String?>(doc.screens.first().id)
        private set

    /** Der Screen selbst ist ausgewählt (Titel angetippt): Ziehen verschiebt ihn, Anfasser ändern die Größe. */
    var boardSelected by mutableStateOf(false)
        private set
    var selection by mutableStateOf<Set<String>>(emptySet())
        private set
    var multiSelect by mutableStateOf(false)

    var marquee by mutableStateOf<RectF?>(null)
    var draft by mutableStateOf<Element?>(null)
        private set

    /** Screen, in dem der Entwurf entsteht; `null` beim Screen-Werkzeug (dann Dokumentkoordinaten). */
    var draftBoard: Board? = null
        private set
    var guides by mutableStateOf<List<Guide>>(emptyList())
        private set
    var linkDrag by mutableStateOf<Pair<Offset, Offset>?>(null)
        private set

    // Dialoge
    var showSettings by mutableStateOf(false)
    var showExport by mutableStateOf(false)
    var showNew by mutableStateOf(false)
    var previewStart by mutableStateOf<String?>(null)
    var colorRequest by mutableStateOf<ColorRequest?>(null)
    var message by mutableStateOf<String?>(null)

    // Datei
    var fileUri: Uri? = null
    var fileName by mutableStateOf<String?>(null)
        private set
    private var savedRevision = 0
    var autosavedRevision = 0

    private var clipboard: String? = null
    private var clipboardBoard: String? = null

    fun touch() {
        revision++
    }

    // ---------------------------------------------------------------- Screens und Auswahl

    /** Was auf der Arbeitsfläche liegt: alle Screens oder nur die Komponente in Bearbeitung. */
    fun boards(): List<Board> = editingComponent?.let { listOfNotNull(doc.component(it)) } ?: doc.screens

    fun activeBoard(): Board? = boards().firstOrNull { it.id == boardId } ?: boards().firstOrNull()

    fun selectedElements(): List<Element> {
        val b = activeBoard() ?: return emptyList()
        return b.children.filter { it.id in selection }
    }

    fun singleSelected(): Element? = selectedElements().singleOrNull()

    fun select(b: Board, e: Element, additive: Boolean) {
        val sameBoard = b.id == boardId
        boardId = b.id
        boardSelected = false
        selection = when {
            additive && sameBoard -> if (e.id in selection) selection - e.id else selection + e.id
            else -> setOf(e.id)
        }
    }

    fun selectBoard(b: Board) {
        boardId = b.id
        boardSelected = true
        selection = emptySet()
    }

    /** Leere Stelle in einem Screen angetippt: Auswahl aufheben, dieser Screen wird aktiv. */
    fun clickBoard(b: Board) {
        boardId = b.id
        boardSelected = false
        selection = emptySet()
    }

    fun clearSelection() {
        boardSelected = false
        selection = emptySet()
    }

    fun selectTool(t: Tool) {
        tool = t
        if (t != Tool.SELECT) clearSelection()
    }

    fun switchMode(m: Mode) {
        mode = m
        tool = Tool.SELECT
        guides = emptyList()
        boardSelected = false
    }

    /** Wählt nur Elemente, die ganz im Rechteck liegen (wie in Kontur). */
    fun selectInRect(b: Board, r: RectF, additive: Boolean) {
        val hits = b.children.filter { e ->
            if (!e.visible || e.locked) return@filter false
            val eb = e.bounds()
            eb.offset(b.x, b.y)
            r.contains(eb)
        }.map { it.id }.toSet()
        val sameBoard = b.id == boardId
        boardId = b.id
        boardSelected = false
        selection = if (additive && sameBoard) selection + hits else hits
    }

    // ---------------------------------------------------------------- Treffertests

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun title(b: Board): String = when {
        editingComponent != null -> "◆ ${b.name}"
        b.id == doc.startScreen -> "▶ ${b.name}"
        else -> b.name
    }

    /** Bereich des Screen-Titels über der linken oberen Ecke, in Dokumentkoordinaten. */
    fun titleRect(b: Board): RectF {
        titlePaint.textSize = 13f * density
        val wPx = titlePaint.measureText(title(b)) + 12f * density
        val hPx = 24f * density
        val z = view.zoom
        return RectF(b.x, b.y - hPx / z, b.x + maxOf(wPx, 48f * density) / z, b.y)
    }

    fun boardAt(p: Offset): Board? = boards().lastOrNull { it.contains(p.x, p.y) }

    fun titleAt(p: Offset): Board? = boards().lastOrNull { titleRect(it).contains(p.x, p.y) }

    fun elementAt(b: Board, p: Offset, tol: Float): Element? {
        val lx = p.x - b.x
        val ly = p.y - b.y
        return b.children.lastOrNull { it.visible && !it.locked && hits(it, lx, ly, tol) }
    }

    private fun hits(e: Element, x: Float, y: Float, tol: Float): Boolean {
        if (e.kind == Kind.LINE) return distToSegment(x, y, e.x, e.y, e.x + e.w, e.y + e.h) <= tol + e.strokeWidth / 2f
        val r = e.bounds()
        r.inset(-tol, -tol)
        return r.contains(x, y)
    }

    private fun distToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val len = dx * dx + dy * dy
        val t = if (len == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    /** Rahmen um die Auswahl (oder um den ausgewählten Screen), in Dokumentkoordinaten. */
    fun selectionBounds(): RectF? {
        val b = activeBoard() ?: return null
        if (boardSelected) return b.rect()
        val els = selectedElements()
        if (els.isEmpty()) return null
        val r = els.first().bounds()
        els.drop(1).forEach { r.union(it.bounds()) }
        r.offset(b.x, b.y)
        return r
    }

    fun insideSelection(p: Offset): Boolean = selectionBounds()?.contains(p.x, p.y) == true

    /** Die acht Anfasser: links oben, oben, rechts oben, rechts, rechts unten, unten, links unten, links. */
    fun handlePoints(r: RectF): List<Offset> = listOf(
        Offset(r.left, r.top), Offset(r.centerX(), r.top), Offset(r.right, r.top), Offset(r.right, r.centerY()),
        Offset(r.right, r.bottom), Offset(r.centerX(), r.bottom), Offset(r.left, r.bottom), Offset(r.left, r.centerY()),
    )

    fun hitHandle(screen: Offset, radius: Float): Int {
        if (mode != Mode.DESIGN || tool != Tool.SELECT) return -1
        val r = selectionBounds() ?: return -1
        handlePoints(r).forEachIndexed { i, p ->
            val s = view.toScreen(p.x, p.y)
            if ((s - screen).getDistance() <= radius) return i
        }
        return -1
    }

    // ---------------------------------------------------------------- Verschieben und Skalieren

    private class Orig(val e: Element, val x: Float, val y: Float, val w: Float, val h: Float)

    private var tActive = false
    private var tHandle = HANDLE_MOVE
    private var tStart = Offset.Zero
    private val tBounds = RectF()
    private var tOrig: List<Orig> = emptyList()

    fun beginTransform(handle: Int, start: Offset) {
        val r = selectionBounds() ?: return
        if (!boardSelected && selectedElements().all { it.locked }) return
        checkpoint()
        tActive = true
        tHandle = handle
        tStart = start
        tBounds.set(r)
        tOrig = if (boardSelected) emptyList() else selectedElements().filter { !it.locked }.map { Orig(it, it.x, it.y, it.w, it.h) }
    }

    fun dragTransform(p: Offset) {
        if (!tActive) return
        val b = activeBoard() ?: return
        val dx = p.x - tStart.x
        val dy = p.y - tStart.y
        val nb = RectF(tBounds)
        val g = mutableListOf<Guide>()
        val thr = 6f * density / view.zoom
        val (xs, ys) = snapTargets(b)
        val snap = settings.snap
        if (tHandle == HANDLE_MOVE) {
            nb.offset(dx, dy)
            if (snap) {
                snapAxis(listOf(nb.left, nb.centerX(), nb.right), xs, thr)?.let { (d, pos) ->
                    nb.offset(d, 0f); g += Guide(true, pos)
                }
                snapAxis(listOf(nb.top, nb.centerY(), nb.bottom), ys, thr)?.let { (d, pos) ->
                    nb.offset(0f, d); g += Guide(false, pos)
                }
            }
            nb.offset(nb.left.roundToInt() - nb.left, nb.top.roundToInt() - nb.top)
        } else {
            val h = tHandle
            fun edge(v: Float, targets: List<Float>, vertical: Boolean): Float {
                if (!snap) return v.roundToInt().toFloat()
                val s = snapAxis(listOf(v), targets, thr) ?: return v.roundToInt().toFloat()
                g += Guide(vertical, s.second)
                return s.second
            }
            if (h == 0 || h == 6 || h == 7) nb.left = edge(tBounds.left + dx, xs, true)
            if (h == 2 || h == 3 || h == 4) nb.right = edge(tBounds.right + dx, xs, true)
            if (h == 0 || h == 1 || h == 2) nb.top = edge(tBounds.top + dy, ys, false)
            if (h == 4 || h == 5 || h == 6) nb.bottom = edge(tBounds.bottom + dy, ys, false)
            if (nb.right < nb.left + 1f) {
                if (h == 0 || h == 6 || h == 7) nb.left = nb.right - 1f else nb.right = nb.left + 1f
            }
            if (nb.bottom < nb.top + 1f) {
                if (h == 0 || h == 1 || h == 2) nb.top = nb.bottom - 1f else nb.bottom = nb.top + 1f
            }
        }
        guides = g
        if (boardSelected) {
            b.x = nb.left
            b.y = nb.top
            b.w = nb.width()
            b.h = nb.height()
        } else {
            val sx = if (tBounds.width() > 0f) nb.width() / tBounds.width() else 1f
            val sy = if (tBounds.height() > 0f) nb.height() / tBounds.height() else 1f
            for (o in tOrig) {
                o.e.x = nb.left + (b.x + o.x - tBounds.left) * sx - b.x
                o.e.y = nb.top + (b.y + o.y - tBounds.top) * sy - b.y
                o.e.w = o.w * sx
                o.e.h = o.h * sy
                fitText(o.e)
            }
        }
        touch()
    }

    fun endTransform() {
        tActive = false
        guides = emptyList()
        touch()
    }

    /** Zweiter Finger während des Ziehens: alles zurück wie vorher. */
    fun cancelTransform() {
        if (!tActive) return
        tActive = false
        guides = emptyList()
        undo()
        redoStack.removeLastOrNull()
        updateHistory()
    }

    /** Kanten und Mitten, an denen beim Ziehen eingerastet wird. */
    private fun snapTargets(b: Board): Pair<List<Float>, List<Float>> {
        val xs = mutableListOf<Float>()
        val ys = mutableListOf<Float>()
        if (boardSelected) {
            for (o in boards()) if (o !== b) {
                xs += listOf(o.x, o.x + o.w); ys += listOf(o.y, o.y + o.h)
            }
        } else {
            xs += listOf(b.x, b.x + b.w / 2f, b.x + b.w)
            ys += listOf(b.y, b.y + b.h / 2f, b.y + b.h)
            for (e in b.children) if (e.id !in selection && e.visible) {
                val r = e.bounds()
                r.offset(b.x, b.y)
                xs += listOf(r.left, r.centerX(), r.right)
                ys += listOf(r.top, r.centerY(), r.bottom)
            }
        }
        return xs to ys
    }

    /** Kleinste Verschiebung, mit der einer der Werte auf ein Ziel fällt: (Verschiebung, Zielposition). */
    private fun snapAxis(values: List<Float>, targets: List<Float>, thr: Float): Pair<Float, Float>? {
        var best: Pair<Float, Float>? = null
        for (v in values) for (t in targets) {
            val d = t - v
            if (abs(d) <= thr && (best == null || abs(d) < abs(best.first))) best = d to t
        }
        return best
    }

    // ---------------------------------------------------------------- Zeichnen neuer Elemente

    private var draftStart = Offset.Zero

    fun beginDraft(start: Offset) {
        draftStart = start
        draftBoard = if (tool == Tool.SCREEN) null else boardAt(start) ?: activeBoard()
        draft = null
    }

    fun dragDraft(p: Offset) {
        val b = draftBoard
        val ox = b?.x ?: 0f
        val oy = b?.y ?: 0f
        val sx = (draftStart.x - ox).roundToInt().toFloat()
        val sy = (draftStart.y - oy).roundToInt().toFloat()
        val px = (p.x - ox).roundToInt().toFloat()
        val py = (p.y - oy).roundToInt().toFloat()
        val kind = when (tool) {
            Tool.ELLIPSE -> Kind.ELLIPSE
            Tool.LINE -> Kind.LINE
            Tool.TEXT -> Kind.TEXT
            else -> Kind.RECT
        }
        val e = Widgets.create(kind, b?.w ?: 0f)
        if (kind == Kind.LINE) {
            e.x = sx; e.y = sy; e.w = px - sx; e.h = py - sy
        } else {
            e.x = minOf(sx, px); e.y = minOf(sy, py); e.w = abs(px - sx); e.h = abs(py - sy)
        }
        if (tool == Tool.SCREEN) {
            e.fill = Element.WHITE
            e.stroke = 0xFF3B7CF0.toInt()
            e.strokeWidth = 2f / view.zoom
        }
        if (kind == Kind.TEXT) fitText(e)
        draft = e
    }

    fun cancelDraft() {
        draft = null
    }

    fun endDraft() {
        val e = draft ?: return
        draft = null
        val big = abs(e.w) >= 4f || abs(e.h) >= 4f
        if (!big) {
            createAt(draftStart)
            return
        }
        checkpoint()
        if (tool == Tool.SCREEN) {
            addScreenAt(e.x, e.y, e.w, e.h)
        } else {
            val b = draftBoard ?: return
            e.name = uniqueName(b, e.kind.label)
            addElement(b, e)
        }
    }

    /** Antippen mit einem Zeichenwerkzeug: Element in Standardgröße. */
    fun createAt(p: Offset) {
        checkpoint()
        if (tool == Tool.SCREEN) {
            if (editingComponent != null) return
            val ref = activeBoard()
            addScreenAt(p.x.roundToInt().toFloat(), p.y.roundToInt().toFloat(), ref?.w ?: 1280f, ref?.h ?: 800f)
            return
        }
        val b = boardAt(p) ?: activeBoard() ?: return
        val kind = when (tool) {
            Tool.ELLIPSE -> Kind.ELLIPSE
            Tool.LINE -> Kind.LINE
            Tool.TEXT -> Kind.TEXT
            else -> Kind.RECT
        }
        val e = Widgets.create(kind, b.w)
        e.x = (p.x - b.x).roundToInt().toFloat()
        e.y = (p.y - b.y).roundToInt().toFloat()
        e.name = uniqueName(b, kind.label)
        fitText(e)
        addElement(b, e)
    }

    private fun addElement(b: Board, e: Element) {
        b.children += e
        boardId = b.id
        boardSelected = false
        selection = setOf(e.id)
        tool = Tool.SELECT
        touch()
    }

    private fun addScreenAt(x: Float, y: Float, w: Float, h: Float) {
        val s = Board(newId(), nextScreenName(), x, y, w.coerceAtLeast(1f), h.coerceAtLeast(1f))
        doc.screens += s
        if (doc.startScreen == null) doc.startScreen = s.id
        tool = Tool.SELECT
        selectBoard(s)
        touch()
    }

    private fun nextScreenName(): String {
        var n = doc.screens.size + 1
        while (doc.screens.any { it.name == "Screen $n" }) n++
        return "Screen $n"
    }

    private fun uniqueName(b: Board, base: String): String {
        var n = b.children.count { it.name.startsWith(base) } + 1
        while (b.children.any { it.name == "$base $n" }) n++
        return "$base $n"
    }

    /** Text-Elemente sind immer genau so hoch wie ihr Text. */
    fun fitText(e: Element) {
        if (e.kind == Kind.TEXT) e.h = renderer.textHeight(e)
    }

    /** Baustein aus der Bibliothek in den aktiven Screen setzen, mitten in den sichtbaren Bereich. */
    fun insertWidget(kind: Kind) {
        val b = activeBoard() ?: run { message = "Zuerst einen Screen anlegen."; return }
        checkpoint()
        val e = Widgets.create(kind, b.w)
        e.name = uniqueName(b, kind.label)
        placeInView(b, e)
        if (kind == Kind.NAVBAR) { e.x = 0f; e.y = 0f }
        addElement(b, e)
    }

    private fun placeInView(b: Board, e: Element) {
        val c = view.centerDoc()
        val cx = if (b.contains(c.x, c.y)) c.x - b.x else b.w / 2f
        val cy = if (b.contains(c.x, c.y)) c.y - b.y else b.h / 2f
        e.x = (cx - e.w / 2f).coerceIn(0f, (b.w - e.w).coerceAtLeast(0f)).roundToInt().toFloat()
        e.y = (cy - e.h / 2f).coerceIn(0f, (b.h - e.h).coerceAtLeast(0f)).roundToInt().toFloat()
    }

    // ---------------------------------------------------------------- Bearbeiten

    /** Änderung an einem Element mit Rückgängig-Schritt. Gleicher Schlüssel kurz hintereinander = ein Schritt. */
    fun edit(key: String, block: () -> Unit) {
        checkpoint(key)
        block()
        touch()
    }

    /** Wendet eine Änderung auf alle ausgewählten Elemente an. */
    fun editSelected(key: String, block: (Element) -> Unit) {
        val els = selectedElements()
        if (els.isEmpty()) return
        checkpoint(key)
        els.forEach {
            block(it)
            fitText(it)
        }
        touch()
    }

    fun toggleVisible(e: Element) = edit("visible") { e.visible = !e.visible }

    fun toggleLocked(e: Element) = edit("lock") {
        e.locked = !e.locked
        if (e.locked) selection = selection - e.id
    }

    fun delete() {
        val b = activeBoard() ?: return
        if (boardSelected) {
            if (editingComponent != null) return
            if (doc.screens.size <= 1) {
                message = "Der letzte Screen kann nicht gelöscht werden."
                return
            }
            checkpoint()
            doc.screens.remove(b)
            removeLinksTo(b.id)
            if (doc.startScreen == b.id) doc.startScreen = doc.screens.first().id
            boardId = doc.screens.first().id
            clearSelection()
            touch()
            return
        }
        if (selection.isEmpty()) return
        checkpoint()
        b.children.removeAll { it.id in selection }
        selection = emptySet()
        touch()
    }

    private fun removeLinksTo(screenId: String) {
        for (bd in doc.allBoards()) for (e in bd.children) if (e.link?.target == screenId) e.link = null
    }

    fun duplicate() {
        val b = activeBoard() ?: return
        val els = selectedElements()
        if (els.isEmpty()) return
        checkpoint()
        val copies = els.map { it.copy(newId()).also { c -> c.x += 16f; c.y += 16f } }
        b.children += copies
        selection = copies.map { it.id }.toSet()
        touch()
    }

    fun copy() {
        val els = selectedElements()
        if (els.isEmpty()) return
        clipboard = RasterJson.writeElements(els)
        clipboardBoard = boardId
        message = "${els.size} kopiert"
    }

    fun paste() {
        val text = clipboard ?: return
        val b = activeBoard() ?: return
        checkpoint()
        val same = clipboardBoard == b.id
        val els = RasterJson.readElements(text).map { e ->
            e.copy(newId()).also { if (same) { it.x += 16f; it.y += 16f } }
        }.filter { it.componentId != b.id }
        b.children += els
        boardSelected = false
        selection = els.map { it.id }.toSet()
        touch()
    }

    val canPaste: Boolean get() = clipboard != null

    /** Eine Stufe nach vorne (+1) oder nach hinten (-1). */
    fun arrange(step: Int) {
        val b = activeBoard() ?: return
        if (selection.isEmpty()) return
        checkpoint()
        val list = b.children
        val order = if (step > 0) list.indices.reversed() else list.indices
        for (i in order) {
            val e = list[i]
            if (e.id !in selection) continue
            val j = i + step
            if (j !in list.indices || list[j].id in selection) continue
            list[i] = list[j]
            list[j] = e
        }
        touch()
    }

    /** Ein Element am Screen ausrichten, mehrere aneinander. */
    fun align(dir: AlignDir) {
        val b = activeBoard() ?: return
        val els = selectedElements().filter { !it.locked }
        if (els.isEmpty()) return
        val ref = if (els.size == 1) RectF(0f, 0f, b.w, b.h) else {
            val r = els.first().bounds()
            els.drop(1).forEach { r.union(it.bounds()) }
            r
        }
        checkpoint()
        for (e in els) {
            val r = e.bounds()
            val dx = when (dir) {
                AlignDir.LEFT -> ref.left - r.left
                AlignDir.HCENTER -> ref.centerX() - r.centerX()
                AlignDir.RIGHT -> ref.right - r.right
                else -> 0f
            }
            val dy = when (dir) {
                AlignDir.TOP -> ref.top - r.top
                AlignDir.VCENTER -> ref.centerY() - r.centerY()
                AlignDir.BOTTOM -> ref.bottom - r.bottom
                else -> 0f
            }
            e.x = (e.x + dx).roundToInt().toFloat()
            e.y = (e.y + dy).roundToInt().toFloat()
        }
        touch()
    }

    // ---------------------------------------------------------------- Screens

    fun addScreen() {
        if (editingComponent != null) return
        checkpoint()
        val ref = activeBoard() ?: doc.screens.lastOrNull()
        val right = doc.screens.maxOfOrNull { it.x + it.w } ?: 0f
        val top = doc.screens.minOfOrNull { it.y } ?: 0f
        addScreenAt(right + 120f, top, ref?.w ?: 1280f, ref?.h ?: 800f)
        activeBoard()?.let { view.fit(it.rect()) }
    }

    fun focusBoard(b: Board) {
        selectBoard(b)
        view.fit(b.rect())
    }

    fun setStart(b: Board) = edit("start") { doc.startScreen = b.id }

    // ---------------------------------------------------------------- Komponenten

    /** Macht aus der Auswahl eine Komponente und setzt an ihre Stelle eine Instanz. */
    fun makeComponent() {
        val b = activeBoard() ?: return
        val els = selectedElements()
        if (els.isEmpty() || editingComponent != null) return
        checkpoint()
        val r = els.first().bounds()
        els.drop(1).forEach { r.union(it.bounds()) }
        val left = r.left.roundToInt().toFloat()
        val top = r.top.roundToInt().toFloat()
        var n = doc.components.size + 1
        while (doc.components.any { it.name == "Komponente $n" }) n++
        val comp = Board(newId(), "Komponente $n", 0f, 0f, r.width().coerceAtLeast(1f), r.height().coerceAtLeast(1f))
        comp.background = null
        for (e in els) comp.children += e.copy().also { it.x -= left; it.y -= top }
        doc.components += comp
        val inst = Widgets.create(Kind.INSTANCE, b.w)
        inst.name = comp.name
        inst.componentId = comp.id
        inst.x = left; inst.y = top; inst.w = comp.w; inst.h = comp.h
        // Links der Elemente gehen auf die Instanz über (das oberste gewinnt)
        inst.link = els.lastOrNull { it.link != null }?.link
        val index = b.children.indexOfLast { it.id in selection } - (els.size - 1)
        b.children.removeAll { it.id in selection }
        b.children.add(index.coerceIn(0, b.children.size), inst)
        selection = setOf(inst.id)
        tab = PanelTab.COMPONENTS
        touch()
    }

    fun insertInstance(compId: String) {
        val comp = doc.component(compId) ?: return
        if (compId == editingComponent) {
            message = "Eine Komponente kann sich nicht selbst enthalten."
            return
        }
        val b = activeBoard() ?: return
        checkpoint()
        val e = Widgets.create(Kind.INSTANCE, b.w)
        e.name = comp.name
        e.componentId = comp.id
        e.w = comp.w
        e.h = comp.h
        placeInView(b, e)
        addElement(b, e)
    }

    fun editComponent(id: String) {
        if (doc.component(id) == null) return
        mode = Mode.DESIGN
        tool = Tool.SELECT
        editingComponent = id
        boardId = id
        clearSelection()
        fitAll()
    }

    fun finishComponentEdit() {
        editingComponent = null
        boardId = doc.screens.firstOrNull()?.id
        clearSelection()
        fitAll()
    }

    /** Instanz in normale Elemente zurückverwandeln. */
    fun detach(e: Element) {
        val b = activeBoard() ?: return
        checkpoint()
        val copies = detachIn(b, e)
        boardSelected = false
        selection = copies.map { it.id }.toSet()
        touch()
    }

    private fun detachIn(b: Board, e: Element): List<Element> {
        val comp = doc.component(e.componentId) ?: return emptyList()
        val sx = e.w / comp.w.coerceAtLeast(1f)
        val sy = e.h / comp.h.coerceAtLeast(1f)
        val copies = comp.children.map { c ->
            c.copy(newId()).also {
                it.x = e.x + c.x * sx
                it.y = e.y + c.y * sy
                it.w = c.w * sx
                it.h = c.h * sy
                e.overrides["${c.id}.text"]?.let { t -> it.text = t }
                e.overrides["${c.id}.fill"]?.let { f -> it.fill = RasterJson.parseColor(f) ?: it.fill }
            }
        }
        val i = b.children.indexOf(e)
        b.children.remove(e)
        b.children.addAll(i.coerceIn(0, b.children.size), copies)
        return copies
    }

    /** Komponente löschen; ihre Instanzen werden vorher zu normalen Elementen. */
    fun deleteComponent(id: String) {
        val comp = doc.component(id) ?: return
        checkpoint()
        if (editingComponent == id) {
            editingComponent = null
            boardId = doc.screens.firstOrNull()?.id
        }
        for (b in doc.allBoards()) if (b !== comp) {
            b.children.filter { it.componentId == id }.forEach { detachIn(b, it) }
        }
        doc.components.remove(comp)
        clearSelection()
        touch()
    }

    fun setOverride(e: Element, key: String, value: String?) = edit("ov-${e.id}-$key") {
        if (value == null) e.overrides.remove(key) else e.overrides[key] = value
    }

    // ---------------------------------------------------------------- Prototyp

    fun setLink(e: Element, target: String?, transition: Transition) = edit("link") {
        e.link = target?.let { Link(it, transition) }
    }

    /** Punkt am rechten Rand des ausgewählten Elements, von dem aus man eine Verbindung zieht. */
    fun linkHandle(): Offset? {
        if (mode != Mode.PROTOTYPE || editingComponent != null) return null
        val e = singleSelected() ?: return null
        val b = activeBoard() ?: return null
        val r = e.bounds()
        return Offset(b.x + r.right, b.y + r.centerY())
    }

    fun hitLinkHandle(screen: Offset, radius: Float): Boolean {
        val p = linkHandle() ?: return false
        return (view.toScreen(p.x, p.y) - screen).getDistance() <= radius
    }

    fun dragLink(p: Offset) {
        val from = linkHandle() ?: return
        linkDrag = from to p
    }

    fun cancelLinkDrag() {
        linkDrag = null
    }

    fun endLinkDrag(p: Offset) {
        linkDrag = null
        val e = singleSelected() ?: return
        val target = doc.screens.lastOrNull { it.contains(p.x, p.y) || titleRect(it).contains(p.x, p.y) } ?: return
        if (target.id == boardId) {
            message = "Ziel muss ein anderer Screen sein."
            return
        }
        setLink(e, target.id, e.link?.transition ?: Transition.SLIDE_LEFT)
    }

    fun openPreview() {
        val b = activeBoard()
        previewStart = if (editingComponent == null && b != null && doc.screen(b.id) != null) b.id
        else doc.startScreen ?: doc.screens.firstOrNull()?.id
    }

    // ---------------------------------------------------------------- Ansicht

    fun fitAll() {
        val list = boards()
        if (list.isEmpty()) return
        val r = list.first().rect()
        list.drop(1).forEach { r.union(it.rect()) }
        view.fit(r)
    }

    // ---------------------------------------------------------------- Rückgängig (ganze Kopien als JSON)

    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    private var lastKey: String? = null
    private var lastTime = 0L

    fun checkpoint(key: String? = null) {
        val now = SystemClock.uptimeMillis()
        if (key != null && key == lastKey && now - lastTime < 1500) {
            lastTime = now
            return
        }
        lastKey = key
        lastTime = now
        undoStack.addLast(RasterJson.write(doc, indent = 0))
        while (undoStack.size > 100) undoStack.removeFirst()
        redoStack.clear()
        updateHistory()
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(RasterJson.write(doc, indent = 0))
        restore(prev)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(RasterJson.write(doc, indent = 0))
        restore(next)
    }

    private fun restore(json: String) {
        doc = RasterJson.read(json)
        lastKey = null
        if (editingComponent != null && doc.component(editingComponent) == null) editingComponent = null
        if (boards().none { it.id == boardId }) boardId = boards().firstOrNull()?.id
        val ids = activeBoard()?.children?.map { it.id }?.toSet() ?: emptySet()
        selection = selection intersect ids
        updateHistory()
        touch()
    }

    private fun updateHistory() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    // ---------------------------------------------------------------- Dateien

    fun newDocument(preset: ScreenPreset) {
        loadDocument(Document.new(preset.w.toFloat(), preset.h.toFloat()), null, null)
        savedRevision = revision
    }

    fun loadDocument(d: Document, name: String?, uri: Uri?) {
        if (d.screens.isEmpty()) d.screens += Board(newId(), "Screen 1", 0f, 0f, 1280f, 800f)
        doc = d
        editingComponent = null
        boardId = d.screens.first().id
        mode = Mode.DESIGN
        tool = Tool.SELECT
        clearSelection()
        undoStack.clear()
        redoStack.clear()
        updateHistory()
        setFile(uri, name)
        touch()
        savedRevision = revision
        fitAll()
    }

    fun setFile(uri: Uri?, name: String?) {
        fileUri = uri
        fileName = name
        settings.lastFileUri = uri?.toString()
        settings.lastFileName = name
    }

    fun json(): String = RasterJson.write(doc)

    fun markSaved() {
        savedRevision = revision
    }

    val dirty: Boolean get() = revision != savedRevision

    /** JSON für die automatische Sicherung, oder `null`, wenn sich nichts geändert hat. */
    fun jsonForAutosave(force: Boolean): String? {
        if (!force && revision == autosavedRevision) return null
        autosavedRevision = revision
        return RasterJson.write(doc, indent = 0)
    }
}
