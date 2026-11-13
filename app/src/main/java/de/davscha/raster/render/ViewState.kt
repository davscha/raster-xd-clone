package de.davscha.raster.render

import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.min

/** Zoom und Verschiebung der Arbeitsfläche (Dokument → Bildschirm), wie in Lasur und Kontur. */
class ViewState {
    val matrix = Matrix()
    private val inverse = Matrix()

    var version by mutableIntStateOf(0)
        private set
    var zoom by mutableFloatStateOf(1f)
        private set

    var viewW = 0
        private set
    var viewH = 0
        private set

    /** Liefert `true` beim allerersten Mal, dann wird eingepasst. */
    fun setViewport(w: Int, h: Int): Boolean {
        val first = viewW == 0
        val oldW = viewW
        val oldH = viewH
        viewW = w
        viewH = h
        // Gerät gedreht: Was in der Mitte war, bleibt in der Mitte. Nur die Höhe geändert (Tastatur): nichts tun.
        if (!first && w != oldW) {
            matrix.postTranslate((w - oldW) / 2f, (h - oldH) / 2f)
            changed()
        }
        return first
    }

    fun fit(r: RectF, margin: Float = 0.88f) {
        if (viewW == 0 || viewH == 0 || r.width() <= 0f || r.height() <= 0f) return
        val s = min(viewW * margin / r.width(), viewH * margin / r.height()).coerceIn(0.02f, 8f)
        matrix.reset()
        matrix.setScale(s, s)
        matrix.postTranslate(viewW / 2f - r.centerX() * s, viewH / 2f - r.centerY() * s)
        zoom = s
        changed()
    }

    fun transform(pan: Offset, zoomFactor: Float, centroid: Offset) {
        val newZoom = (zoom * zoomFactor).coerceIn(0.02f, 16f)
        val f = newZoom / zoom
        matrix.postScale(f, f, centroid.x, centroid.y)
        matrix.postTranslate(pan.x, pan.y)
        zoom = newZoom
        changed()
    }

    fun toDoc(p: Offset): Offset {
        val a = floatArrayOf(p.x, p.y)
        inverse.mapPoints(a)
        return Offset(a[0], a[1])
    }

    fun toScreen(x: Float, y: Float): Offset {
        val a = floatArrayOf(x, y)
        matrix.mapPoints(a)
        return Offset(a[0], a[1])
    }

    /** Mitte des sichtbaren Bereichs im Dokument. */
    fun centerDoc(): Offset = toDoc(Offset(viewW / 2f, viewH / 2f))

    private fun changed() {
        matrix.invert(inverse)
        version++
    }
}
