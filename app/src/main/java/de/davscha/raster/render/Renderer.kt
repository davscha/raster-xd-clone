package de.davscha.raster.render

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import de.davscha.raster.io.RasterJson
import de.davscha.raster.model.Board
import de.davscha.raster.model.Document
import de.davscha.raster.model.Element
import de.davscha.raster.model.HAlign
import de.davscha.raster.model.Kind
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Zeichnet Screens, Komponenten und Elemente für Bildschirm, Vorschau und PNG-Export.
 * Alle UI-Bausteine entstehen hier aus ihren Parametern (Text, Farben, Radius, Wert).
 */
class Renderer {
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dashP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF9A9CA5.toInt()
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }
    private val rect = RectF()
    private val path = Path()
    private val layouts = HashMap<String, StaticLayout>()

    /** Textblock mit Umbruch. Wird zwischengespeichert, weil jedes Bild neu gezeichnet wird. */
    fun layout(text: String, size: Float, color: Int, bold: Boolean, width: Int, align: HAlign): StaticLayout {
        val key = "$size|$color|$bold|$width|${align.ordinal}|$text"
        layouts[key]?.let { return it }
        if (layouts.size > 500) layouts.clear()
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val al = when (align) {
            HAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
            HAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            HAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val l = StaticLayout.Builder.obtain(text, 0, text.length, tp, width.coerceAtLeast(1))
            .setAlignment(al)
            .setIncludePad(false)
            .build()
        layouts[key] = l
        return l
    }

    /** Höhe eines Text-Elements bei seiner aktuellen Breite. */
    fun textHeight(e: Element): Float =
        layout(e.text.ifEmpty { " " }, e.textSize, e.textColor, e.bold, abs(e.w).toInt(), e.align).height.toFloat()

    fun drawBoard(c: Canvas, b: Board, doc: Document, background: Boolean = true) {
        c.save()
        c.translate(b.x, b.y)
        c.clipRect(0f, 0f, b.w, b.h)
        if (background) b.background?.let {
            fillP.color = it
            c.drawRect(0f, 0f, b.w, b.h, fillP)
        }
        drawElements(c, b.children, doc, null, 0)
        c.restore()
    }

    /** Verkleinerte Ansicht für Listen (Komponenten-Bibliothek). */
    fun drawThumb(c: Canvas, b: Board, doc: Document, w: Float, h: Float) {
        val s = min(w / b.w.coerceAtLeast(1f), h / b.h.coerceAtLeast(1f))
        c.save()
        c.translate((w - b.w * s) / 2f, (h - b.h * s) / 2f)
        c.scale(s, s)
        c.translate(-b.x, -b.y)
        drawBoard(c, b, doc)
        c.restore()
    }

    fun drawElements(c: Canvas, list: List<Element>, doc: Document, ov: Map<String, String>?, depth: Int) {
        for (e in list) if (e.visible) drawElement(c, e, doc, ov, depth)
    }

    fun drawElement(c: Canvas, e: Element, doc: Document, ov: Map<String, String>? = null, depth: Int = 0) {
        val alpha = (e.opacity * 255).toInt().coerceIn(0, 255)
        if (alpha == 0) return
        val count = if (alpha < 255) {
            val b = e.bounds()
            b.inset(-e.strokeWidth - 24f, -e.strokeWidth - 24f)
            c.saveLayerAlpha(b, alpha)
        } else c.save()
        c.translate(e.x, e.y)
        val text = ov?.get("${e.id}.text") ?: e.text
        val fill = ov?.get("${e.id}.fill")?.let { RasterJson.parseColor(it) } ?: e.fill
        drawContent(c, e, text, fill, doc, depth)
        c.restoreToCount(count)
    }

    private fun drawContent(c: Canvas, e: Element, text: String, fill: Int?, doc: Document, depth: Int) {
        val w = e.w
        val h = e.h
        when (e.kind) {
            Kind.RECT -> box(c, w, h, e.radius, fill, e.stroke, e.strokeWidth, e.shadow)
            Kind.ELLIPSE -> box(c, w, h, 0f, fill, e.stroke, e.strokeWidth, e.shadow, oval = true)
            Kind.LINE -> {
                strokeP.color = e.stroke ?: fill ?: Element.DARK
                strokeP.strokeWidth = e.strokeWidth
                strokeP.strokeCap = Paint.Cap.ROUND
                c.drawLine(0f, 0f, w, h, strokeP)
                strokeP.strokeCap = Paint.Cap.BUTT
            }
            Kind.TEXT -> {
                box(c, w, h, 0f, fill, e.stroke, e.strokeWidth, false)
                text(c, text, 0f, 0f, w, h, e, vCenter = false)
            }
            Kind.BUTTON -> {
                box(c, w, h, e.radius, fill, e.stroke, e.strokeWidth, e.shadow)
                text(c, text, 12f, 0f, w - 24f, h, e, vCenter = true)
            }
            Kind.INPUT -> {
                box(c, w, h, e.radius, fill, e.stroke, e.strokeWidth, e.shadow)
                if (text.isEmpty()) {
                    val muted = (e.textColor and 0x00FFFFFF) or 0x73000000
                    textWith(c, e.placeholder, 14f, 0f, w - 28f, h, e.textSize, muted, false, e.align, true)
                } else {
                    text(c, text, 14f, 0f, w - 28f, h, e, vCenter = true)
                }
            }
            Kind.SLIDER -> {
                val cy = h / 2f
                val r = (h / 2f - 1f).coerceAtLeast(2f)
                val track = max(4f, h * 0.16f)
                val x0 = r
                val x1 = (w - r).coerceAtLeast(x0)
                val kx = x0 + (x1 - x0) * e.value.coerceIn(0f, 1f)
                strokeP.strokeCap = Paint.Cap.ROUND
                strokeP.strokeWidth = track
                strokeP.color = e.stroke ?: Element.LINE_GRAY
                c.drawLine(x0, cy, x1, cy, strokeP)
                strokeP.color = fill ?: Element.ACCENT
                c.drawLine(x0, cy, kx, cy, strokeP)
                strokeP.strokeCap = Paint.Cap.BUTT
                fillP.color = Element.WHITE
                fillP.setShadowLayer(4f, 0f, 1f, 0x40000000)
                c.drawCircle(kx, cy, r, fillP)
                fillP.clearShadowLayer()
                strokeP.strokeWidth = 1.5f
                strokeP.color = fill ?: Element.ACCENT
                c.drawCircle(kx, cy, r - 0.75f, strokeP)
            }
            Kind.SWITCH -> {
                val on = e.on
                box(c, w, h, h / 2f, if (on) fill ?: Element.ACCENT else e.stroke ?: Element.LINE_GRAY, null, 0f, false)
                val r = h / 2f - 3f
                fillP.color = Element.WHITE
                fillP.setShadowLayer(3f, 0f, 1f, 0x40000000)
                c.drawCircle(if (on) w - h / 2f else h / 2f, h / 2f, r.coerceAtLeast(1f), fillP)
                fillP.clearShadowLayer()
            }
            Kind.CHECKBOX -> {
                val s = h
                if (e.on) {
                    box(c, s, s, s * 0.2f, fill ?: Element.ACCENT, null, 0f, false)
                    path.reset()
                    path.moveTo(s * 0.25f, s * 0.52f)
                    path.lineTo(s * 0.43f, s * 0.7f)
                    path.lineTo(s * 0.76f, s * 0.32f)
                    strokeP.color = Element.WHITE
                    strokeP.strokeWidth = s * 0.12f
                    strokeP.strokeCap = Paint.Cap.ROUND
                    strokeP.strokeJoin = Paint.Join.ROUND
                    c.drawPath(path, strokeP)
                    strokeP.strokeCap = Paint.Cap.BUTT
                    strokeP.strokeJoin = Paint.Join.MITER
                } else {
                    box(c, s, s, s * 0.2f, Element.WHITE, e.stroke ?: 0xFF8E909A.toInt(), 2f, false)
                }
                text(c, text, s + 10f, 0f, w - s - 10f, h, e, vCenter = true)
            }
            Kind.LIST -> {
                box(c, w, h, e.radius, fill, null, 0f, e.shadow)
                val items = text.split('\n')
                val rowH = h / items.size.coerceAtLeast(1)
                c.save()
                rect.set(0f, 0f, w, h)
                path.reset()
                path.addRoundRect(rect, e.radius, e.radius, Path.Direction.CW)
                c.clipPath(path)
                items.forEachIndexed { i, s ->
                    val top = i * rowH
                    text(c, s, 16f, top, w - 48f, rowH, e, vCenter = true)
                    // Kleiner Pfeil rechts, wie in Einstellungslisten
                    val cx = w - 22f
                    val cy = top + rowH / 2f
                    val a = min(6f, rowH * 0.15f)
                    strokeP.color = 0xFFA4A5AD.toInt()
                    strokeP.strokeWidth = 2f
                    strokeP.strokeCap = Paint.Cap.ROUND
                    c.drawLine(cx - a / 2f, cy - a, cx + a / 2f, cy, strokeP)
                    c.drawLine(cx + a / 2f, cy, cx - a / 2f, cy + a, strokeP)
                    strokeP.strokeCap = Paint.Cap.BUTT
                    if (i > 0) {
                        e.stroke?.let {
                            strokeP.color = it
                            strokeP.strokeWidth = e.strokeWidth
                            c.drawLine(16f, top, w, top, strokeP)
                        }
                    }
                }
                c.restore()
            }
            Kind.CARD -> {
                box(c, w, h, e.radius, fill, e.stroke, e.strokeWidth, e.shadow)
                if (text.isNotEmpty()) text(c, text, 20f, 18f, w - 40f, h, e, vCenter = false)
            }
            Kind.NAVBAR -> {
                box(c, w, h, 0f, fill, null, 0f, e.shadow)
                e.stroke?.let {
                    strokeP.color = it
                    strokeP.strokeWidth = e.strokeWidth
                    c.drawLine(0f, h - e.strokeWidth / 2f, w, h - e.strokeWidth / 2f, strokeP)
                }
                text(c, text, 24f, 0f, w - 48f, h, e, vCenter = true)
            }
            Kind.INSTANCE -> {
                val comp = doc.component(e.componentId)
                if (comp == null || depth > 4) {
                    rect.set(0f, 0f, w, h)
                    c.drawRect(rect, dashP)
                } else {
                    c.save()
                    c.clipRect(0f, 0f, w, h)
                    c.scale(w / comp.w.coerceAtLeast(1f), h / comp.h.coerceAtLeast(1f))
                    comp.background?.let {
                        fillP.color = it
                        c.drawRect(0f, 0f, comp.w, comp.h, fillP)
                    }
                    drawElements(c, comp.children, doc, e.overrides, depth + 1)
                    c.restore()
                }
            }
        }
    }

    private fun box(c: Canvas, w: Float, h: Float, r: Float, fill: Int?, stroke: Int?, sw: Float, shadow: Boolean, oval: Boolean = false) {
        rect.set(min(0f, w), min(0f, h), max(0f, w), max(0f, h))
        val rr = r.coerceIn(0f, min(rect.width(), rect.height()) / 2f)
        if (fill != null) {
            fillP.color = fill
            if (shadow) fillP.setShadowLayer(16f, 0f, 4f, 0x30000000)
            if (oval) c.drawOval(rect, fillP) else c.drawRoundRect(rect, rr, rr, fillP)
            fillP.clearShadowLayer()
        }
        if (stroke != null && sw > 0f) {
            strokeP.color = stroke
            strokeP.strokeWidth = sw
            rect.inset(sw / 2f, sw / 2f)
            val r2 = (rr - sw / 2f).coerceAtLeast(0f)
            if (oval) c.drawOval(rect, strokeP) else c.drawRoundRect(rect, r2, r2, strokeP)
        }
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, width: Float, height: Float, e: Element, vCenter: Boolean) =
        textWith(c, s, x, y, width, height, e.textSize, e.textColor, e.bold, e.align, vCenter)

    private fun textWith(
        c: Canvas, s: String, x: Float, y: Float, width: Float, height: Float,
        size: Float, color: Int, bold: Boolean, align: HAlign, vCenter: Boolean,
    ) {
        if (s.isEmpty() || width < 1f) return
        val l = layout(s, size, color, bold, width.toInt(), align)
        c.save()
        c.translate(x, if (vCenter) y + (height - l.height) / 2f else y)
        l.draw(c)
        c.restore()
    }
}
