package de.davscha.raster.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import de.davscha.raster.EditorState
import de.davscha.raster.HANDLE_MOVE
import de.davscha.raster.Mode
import de.davscha.raster.Tool
import de.davscha.raster.model.Kind
import de.davscha.raster.model.Link
import kotlin.math.abs
import kotlin.math.max
import android.graphics.Canvas as NativeCanvas

private const val ACCENT = 0xFF3B7CF0.toInt()
private const val PROTO = 0xFFFF8A4C.toInt()
private const val COMPONENT = 0xFF2FB67C.toInt()
private const val GUIDE = 0xFFFF3D7F.toInt()
private const val MUTED = 0xFFA4A5AD.toInt()

private class Paints {
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFF2A2B30.toInt() }
    val title = Paint(Paint.ANTI_ALIAS_FLAG)
    val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val checker = checkerPaint()
    val path = Path()
}

/**
 * Die Arbeitsfläche mit allen Screens. Der Stift zeichnet und wählt aus, Finger zoomen und verschieben.
 * Antippen wählt aus (Stift und Finger). Verschieben geht nur mit bereits ausgewählten Elementen.
 */
@Composable
fun RasterCanvas(state: EditorState, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val focus = LocalFocusManager.current
    SideEffect { state.density = density }
    val p = remember { Paints() }

    Canvas(
        modifier
            .clipToBounds()
            .onSizeChanged { if (state.view.setViewport(it.width, it.height)) state.fitAll() }
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Tippen auf die Arbeitsfläche beendet die Eingabe in einem Textfeld
                    focus.clearFocus()
                    val stylus = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                    val draws = stylus || state.settings.fingerDraws
                    when {
                        state.mode == Mode.PROTOTYPE -> prototypeGesture(state, down, stylus)
                        state.tool == Tool.SELECT -> selectGesture(state, down, draws, stylus)
                        else -> createGesture(state, down, draws, stylus)
                    }
                }
            },
    ) {
        state.revision // Lesen sorgt für Neuzeichnen bei Änderungen
        state.view.version
        val zoom = state.view.zoom
        val dp = density / zoom // ein dp im Dokumentraum
        val proto = state.mode == Mode.PROTOTYPE
        val doc = state.doc
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            nc.save()
            nc.concat(state.view.matrix)

            // Screens mit Schatten und Titel
            for (b in state.boards()) {
                p.shadow.setShadowLayer(10f * dp, 0f, 2f * dp, 0x70000000)
                nc.drawRect(b.x, b.y, b.x + b.w, b.y + b.h, p.shadow)
                p.shadow.clearShadowLayer()
                if (b.background == null || (b.background!! ushr 24) < 255) nc.drawRect(b.x, b.y, b.x + b.w, b.y + b.h, p.checker)
                state.renderer.drawBoard(nc, b, doc)
                p.title.textSize = 13f * dp
                p.title.color = if (b.id == state.boardId) (if (proto) PROTO else ACCENT) else MUTED
                nc.drawText(state.title(b), b.x + 2f * dp, b.y - 8f * dp, p.title)
            }

            // Entwurf beim Aufziehen
            state.draft?.let { e ->
                val b = state.draftBoard
                if (b != null) {
                    nc.save()
                    nc.clipRect(b.x, b.y, b.x + b.w, b.y + b.h)
                    nc.translate(b.x, b.y)
                    state.renderer.drawElement(nc, e, doc)
                    nc.restore()
                } else {
                    p.fill.color = 0xFFFFFFFF.toInt()
                    nc.drawRect(e.x, e.y, e.x + e.w, e.y + e.h, p.fill)
                    p.stroke.color = ACCENT
                    p.stroke.strokeWidth = 2f * dp
                    nc.drawRect(e.x, e.y, e.x + e.w, e.y + e.h, p.stroke)
                }
            }

            if (proto) drawPrototype(nc, state, p, dp) else drawSelection(nc, state, p, dp)

            // Hilfslinien beim Einrasten
            p.stroke.color = GUIDE
            p.stroke.strokeWidth = 1f * dp
            for (g in state.guides) {
                if (g.vertical) nc.drawLine(g.pos, -100000f, g.pos, 100000f, p.stroke)
                else nc.drawLine(-100000f, g.pos, 100000f, g.pos, p.stroke)
            }

            state.marquee?.let { r ->
                p.dash.color = ACCENT
                p.dash.strokeWidth = 1.2f * dp
                p.dash.pathEffect = DashPathEffect(floatArrayOf(6f * dp, 4f * dp), 0f)
                nc.drawRect(r, p.dash)
            }
            nc.restore()
        }
    }
}

private fun drawSelection(nc: NativeCanvas, state: EditorState, p: Paints, dp: Float) {
    val b = state.activeBoard() ?: return
    val els = state.selectedElements()
    p.stroke.strokeWidth = 1.5f * dp
    for (e in els) {
        p.stroke.color = if (e.kind == Kind.INSTANCE) COMPONENT else ACCENT
        if (e.kind == Kind.LINE) {
            nc.drawLine(b.x + e.x, b.y + e.y, b.x + e.x + e.w, b.y + e.y + e.h, p.stroke)
        } else {
            val r = e.bounds()
            r.offset(b.x, b.y)
            nc.drawRect(r, p.stroke)
        }
    }
    if (state.tool != Tool.SELECT) return
    val r = state.selectionBounds() ?: return
    p.stroke.color = ACCENT
    p.stroke.strokeWidth = (if (state.boardSelected) 2f else 1f) * dp
    nc.drawRect(r, p.stroke)
    val s = 4.5f * dp
    for (h in state.handlePoints(r)) {
        p.fill.color = 0xFFFFFFFF.toInt()
        nc.drawRect(h.x - s, h.y - s, h.x + s, h.y + s, p.fill)
        nc.drawRect(h.x - s, h.y - s, h.x + s, h.y + s, p.stroke)
    }
}

/** Verbindungen als Kurven vom Element zum Ziel-Screen, wie in XD. */
private fun drawPrototype(nc: NativeCanvas, state: EditorState, p: Paints, dp: Float) {
    val doc = state.doc
    if (state.editingComponent != null) return
    for (b in doc.screens) for (e in b.children) {
        val l = e.link ?: continue
        if (!e.visible) continue
        val r = e.bounds()
        r.offset(b.x, b.y)
        p.stroke.color = PROTO
        p.stroke.strokeWidth = 1f * dp
        nc.drawRect(r, p.stroke)
        if (l.target == Link.BACK) continue
        val t = doc.screen(l.target) ?: continue
        drawArrow(nc, p, Offset(r.right, r.centerY()), Offset(t.x, t.y + 28f * dp), dp, dashed = false)
    }
    val b = state.activeBoard() ?: return
    if (state.boardSelected) {
        p.stroke.color = PROTO
        p.stroke.strokeWidth = 2f * dp
        nc.drawRect(b.x, b.y, b.x + b.w, b.y + b.h, p.stroke)
    }
    state.singleSelected()?.let { e ->
        val r = e.bounds()
        r.offset(b.x, b.y)
        p.stroke.color = PROTO
        p.stroke.strokeWidth = 2f * dp
        nc.drawRect(r, p.stroke)
    }
    state.linkDrag?.let { (from, to) -> drawArrow(nc, p, from, to, dp, dashed = true) }
    state.linkHandle()?.let { h ->
        p.fill.color = PROTO
        nc.drawCircle(h.x, h.y, 10f * dp, p.fill)
        p.stroke.color = 0xFFFFFFFF.toInt()
        p.stroke.strokeWidth = 2f * dp
        nc.drawLine(h.x - 4f * dp, h.y, h.x + 4f * dp, h.y, p.stroke)
        nc.drawLine(h.x + 1f * dp, h.y - 3f * dp, h.x + 4f * dp, h.y, p.stroke)
        nc.drawLine(h.x + 1f * dp, h.y + 3f * dp, h.x + 4f * dp, h.y, p.stroke)
    }
}

private fun drawArrow(nc: NativeCanvas, p: Paints, from: Offset, to: Offset, dp: Float, dashed: Boolean) {
    val k = max(40f * dp, abs(to.x - from.x) / 2f)
    p.path.reset()
    p.path.moveTo(from.x, from.y)
    p.path.cubicTo(from.x + k, from.y, to.x - k, to.y, to.x, to.y)
    val paint = if (dashed) p.dash else p.stroke
    paint.color = PROTO
    paint.strokeWidth = 2f * dp
    if (dashed) paint.pathEffect = DashPathEffect(floatArrayOf(6f * dp, 4f * dp), 0f)
    nc.drawPath(p.path, paint)
    p.fill.color = PROTO
    p.path.reset()
    p.path.moveTo(to.x, to.y)
    p.path.lineTo(to.x - 9f * dp, to.y - 5f * dp)
    p.path.lineTo(to.x - 9f * dp, to.y + 5f * dp)
    p.path.close()
    nc.drawPath(p.path, p.fill)
    nc.drawCircle(from.x, from.y, 3.5f * dp, p.fill)
}

// ---------------------------------------------------------------- Gesten

private sealed interface GestureStart {
    data object Tap : GestureStart
    class Drag(val event: PointerEvent) : GestureStart
    class Multi(val event: PointerEvent) : GestureStart

    /** Erst lag ein Handballen auf, jetzt setzt der Stift auf: Die Geste gehört dem Stift. */
    class PenJoined(val pen: PointerInputChange) : GestureStart
}

/** Wartet, bis klar ist: Antippen, Ziehen oder zweiter Finger. */
private suspend fun AwaitPointerEventScope.awaitStart(down: PointerInputChange): GestureStart {
    val slop = viewConfiguration.touchSlop * (if (down.type == PointerType.Stylus) 0.5f else 1f)
    while (true) {
        val e = awaitPointerEvent()
        // Ein aufliegender Handballen darf den Stift nicht aufs Navigieren umschalten
        if (down.type != PointerType.Stylus && down.type != PointerType.Eraser) {
            val others = e.changes.filter { it.pressed && it.id != down.id }
            others.firstOrNull { it.type == PointerType.Stylus }?.let { return GestureStart.PenJoined(it) }
            if (others.isNotEmpty()) return GestureStart.Multi(e)
        }
        val c = e.changes.firstOrNull { it.id == down.id } ?: return GestureStart.Tap
        if (!c.pressed) {
            c.consume()
            return GestureStart.Tap
        }
        if ((c.position - down.position).getDistance() > slop) return GestureStart.Drag(e)
    }
}

private suspend fun AwaitPointerEventScope.selectGesture(state: EditorState, down: PointerInputChange, draws: Boolean, stylus: Boolean) {
    val density = state.density
    val handle = state.hitHandle(down.position, 22f * density)
    val start = state.view.toDoc(down.position)
    val tol = 6f * density / state.view.zoom
    val title = state.titleAt(start)
    val b = state.boardAt(start)
    val hit = if (title == null) b?.let { state.elementAt(it, start, tol) } else null
    when (val g = awaitStart(down)) {
        GestureStart.Tap -> when {
            handle >= 0 -> Unit
            title != null -> state.selectBoard(title)
            b != null && hit != null -> state.select(b, hit, additive = state.multiSelect)
            b != null -> state.clickBoard(b)
            else -> state.clearSelection()
        }
        is GestureStart.Multi -> navigateLoop(state, g.event)
        is GestureStart.PenJoined -> selectGesture(state, g.pen, draws = true, stylus = true)
        is GestureStart.Drag -> when {
            handle >= 0 -> transformLoop(state, handle, start, stylus, down)
            // Titel ziehen verschiebt den Screen
            title != null && state.editingComponent == null -> {
                state.selectBoard(title)
                transformLoop(state, HANDLE_MOVE, start, stylus, down)
            }
            // Nur Ausgewähltes lässt sich ziehen. Innerhalb des Auswahlrahmens verschiebt Ziehen immer die Auswahl.
            (hit != null && hit.id in state.selection) || state.insideSelection(start) ->
                transformLoop(state, HANDLE_MOVE, start, stylus, down)
            draws && b != null -> marqueeLoop(state, down, start, stylus)
            else -> navigateLoop(state, g.event)
        }
    }
}

private suspend fun AwaitPointerEventScope.transformLoop(state: EditorState, handle: Int, start: Offset, stylus: Boolean, down: PointerInputChange) {
    state.beginTransform(handle, start)
    while (true) {
        val e = awaitPointerEvent()
        if (!stylus && e.changes.count { it.pressed } > 1) {
            state.cancelTransform()
            navigateLoop(state, e)
            return
        }
        // Nur der Zeiger, der die Geste begonnen hat: Ein aufliegender Handballen darf nichts bewegen
        val c = e.changes.firstOrNull { it.id == down.id } ?: break
        state.dragTransform(state.view.toDoc(c.position))
        c.consume()
        if (!c.pressed) break
    }
    state.endTransform()
}

private suspend fun AwaitPointerEventScope.marqueeLoop(state: EditorState, down: PointerInputChange, start: Offset, stylus: Boolean) {
    val b = state.boardAt(start) ?: return
    while (true) {
        val e = awaitPointerEvent()
        if (!stylus && e.changes.count { it.pressed } > 1) {
            state.marquee = null
            navigateLoop(state, e)
            return
        }
        val c = e.changes.firstOrNull { it.id == down.id } ?: break
        val d = state.view.toDoc(c.position)
        state.marquee = RectF(minOf(start.x, d.x), minOf(start.y, d.y), maxOf(start.x, d.x), maxOf(start.y, d.y))
        c.consume()
        if (!c.pressed) break
    }
    state.marquee?.let { state.selectInRect(b, it, state.multiSelect) }
    state.marquee = null
}

/** Rechteck, Ellipse, Linie, Text und Screen: aufziehen oder antippen. */
private suspend fun AwaitPointerEventScope.createGesture(state: EditorState, down: PointerInputChange, draws: Boolean, stylus: Boolean) {
    val start = state.view.toDoc(down.position)
    when (val g = awaitStart(down)) {
        GestureStart.Tap -> if (draws || state.tool == Tool.TEXT || state.tool == Tool.SCREEN) state.createAt(start)
        is GestureStart.Multi -> navigateLoop(state, g.event)
        is GestureStart.PenJoined -> createGesture(state, g.pen, draws = true, stylus = true)
        is GestureStart.Drag -> if (draws) draftLoop(state, down, start, stylus) else navigateLoop(state, g.event)
    }
}

private suspend fun AwaitPointerEventScope.draftLoop(state: EditorState, down: PointerInputChange, start: Offset, stylus: Boolean) {
    state.beginDraft(start)
    while (true) {
        val e = awaitPointerEvent()
        if (!stylus && e.changes.count { it.pressed } > 1) {
            state.cancelDraft()
            navigateLoop(state, e)
            return
        }
        val c = e.changes.firstOrNull { it.id == down.id } ?: break
        state.dragDraft(state.view.toDoc(c.position))
        c.consume()
        if (!c.pressed) break
    }
    state.endDraft()
}

/** Prototyp: antippen wählt aus, vom orangen Punkt aus zieht man eine Verbindung zu einem Screen. */
private suspend fun AwaitPointerEventScope.prototypeGesture(state: EditorState, down: PointerInputChange, stylus: Boolean) {
    val density = state.density
    val onHandle = state.hitLinkHandle(down.position, 22f * density)
    val start = state.view.toDoc(down.position)
    val tol = 6f * density / state.view.zoom
    val title = state.titleAt(start)
    val b = state.boardAt(start)
    val hit = if (title == null) b?.let { state.elementAt(it, start, tol) } else null
    when (val g = awaitStart(down)) {
        GestureStart.Tap -> when {
            onHandle -> Unit
            title != null -> state.selectBoard(title)
            b != null && hit != null -> state.select(b, hit, additive = false)
            b != null -> state.clickBoard(b)
            else -> state.clearSelection()
        }
        is GestureStart.Multi -> navigateLoop(state, g.event)
        is GestureStart.PenJoined -> prototypeGesture(state, g.pen, stylus = true)
        is GestureStart.Drag -> if (onHandle) linkLoop(state, down, stylus) else navigateLoop(state, g.event)
    }
}

private suspend fun AwaitPointerEventScope.linkLoop(state: EditorState, down: PointerInputChange, stylus: Boolean) {
    var last = state.view.toDoc(down.position)
    while (true) {
        val e = awaitPointerEvent()
        if (!stylus && e.changes.count { it.pressed } > 1) {
            state.cancelLinkDrag()
            navigateLoop(state, e)
            return
        }
        val c = e.changes.firstOrNull { it.id == down.id } ?: break
        last = state.view.toDoc(c.position)
        state.dragLink(last)
        c.consume()
        if (!c.pressed) break
    }
    state.endLinkDrag(last)
}

private suspend fun AwaitPointerEventScope.navigateLoop(state: EditorState, first: PointerEvent?) {
    var event = first ?: awaitPointerEvent()
    while (true) {
        val zoom = event.calculateZoom()
        val pan = event.calculatePan()
        val centroid = event.calculateCentroid(useCurrent = true)
        if (centroid.isSpecified) state.view.transform(pan, zoom, centroid)
        event.changes.forEach { if (it.positionChanged()) it.consume() }
        if (event.changes.none { it.pressed }) break
        event = awaitPointerEvent()
    }
}

private fun checkerPaint(): Paint {
    val cell = 12
    val b = Bitmap.createBitmap(cell * 2, cell * 2, Bitmap.Config.ARGB_8888)
    val c = NativeCanvas(b)
    c.drawColor(0xFFCFCFCF.toInt())
    val p = Paint().apply { color = 0xFFFFFFFF.toInt() }
    c.drawRect(0f, 0f, cell.toFloat(), cell.toFloat(), p)
    c.drawRect(cell.toFloat(), cell.toFloat(), cell * 2f, cell * 2f, p)
    return Paint().apply { shader = BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
}
