package de.davscha.raster.io

import de.davscha.raster.model.Board
import de.davscha.raster.model.Document
import de.davscha.raster.model.Element
import de.davscha.raster.model.HAlign
import de.davscha.raster.model.Kind
import de.davscha.raster.model.Link
import de.davscha.raster.model.Transition
import org.json.JSONArray
import org.json.JSONObject

/**
 * Projektformat `.raster`: lesbares, verschachteltes JSON.
 *
 * ```
 * { "format": "raster", "version": 1, "startScreen": "…",
 *   "screens":    [ { "id", "name", "x", "y", "w", "h", "background", "elements": [ … ] } ],
 *   "components": [ { … wie ein Screen … } ] }
 * ```
 * Farben stehen als „#AARRGGBB“ drin, `null` heißt „keine“.
 */
object RasterJson {
    const val FORMAT = "raster"
    private const val VERSION = 1

    fun write(doc: Document, indent: Int = 2): String {
        val o = JSONObject()
        o.put("format", FORMAT)
        o.put("version", VERSION)
        o.put("startScreen", doc.startScreen ?: JSONObject.NULL)
        o.put("screens", JSONArray().apply { doc.screens.forEach { put(board(it)) } })
        o.put("components", JSONArray().apply { doc.components.forEach { put(board(it)) } })
        return if (indent > 0) o.toString(indent) else o.toString()
    }

    fun read(text: String): Document {
        val o = JSONObject(text)
        require(o.optString("format") == FORMAT) { "Keine Raster-Datei" }
        val d = Document()
        o.optJSONArray("screens")?.let { a -> for (i in 0 until a.length()) d.screens += board(a.getJSONObject(i)) }
        o.optJSONArray("components")?.let { a -> for (i in 0 until a.length()) d.components += board(a.getJSONObject(i)) }
        d.startScreen = o.optStringOrNull("startScreen")
        return d
    }

    /** Nur Elemente, für Kopieren und Einfügen. */
    fun writeElements(list: List<Element>): String = JSONArray().apply { list.forEach { put(element(it)) } }.toString()

    fun readElements(text: String): List<Element> {
        val a = JSONArray(text)
        return (0 until a.length()).map { element(a.getJSONObject(it)) }
    }

    private fun board(b: Board) = JSONObject().apply {
        put("id", b.id)
        put("name", b.name)
        put("x", b.x.toDouble())
        put("y", b.y.toDouble())
        put("w", b.w.toDouble())
        put("h", b.h.toDouble())
        put("background", color(b.background))
        put("elements", JSONArray().apply { b.children.forEach { put(element(it)) } })
    }

    private fun board(o: JSONObject): Board {
        val b = Board(
            o.getString("id"),
            o.optString("name", "Screen"),
            o.optDouble("x", 0.0).toFloat(),
            o.optDouble("y", 0.0).toFloat(),
            o.optDouble("w", 1280.0).toFloat(),
            o.optDouble("h", 800.0).toFloat(),
        )
        b.background = color(o, "background")
        o.optJSONArray("elements")?.let { a -> for (i in 0 until a.length()) b.children += element(a.getJSONObject(i)) }
        return b
    }

    private fun element(e: Element) = JSONObject().apply {
        put("id", e.id)
        put("kind", e.kind.name.lowercase())
        put("name", e.name)
        put("x", e.x.toDouble()); put("y", e.y.toDouble()); put("w", e.w.toDouble()); put("h", e.h.toDouble())
        put("style", JSONObject().apply {
            put("fill", color(e.fill))
            put("stroke", color(e.stroke))
            put("strokeWidth", e.strokeWidth.toDouble())
            put("radius", e.radius.toDouble())
            put("opacity", e.opacity.toDouble())
            put("shadow", e.shadow)
        })
        if (e.kind.hasText || e.text.isNotEmpty()) {
            put("text", JSONObject().apply {
                put("content", e.text)
                put("size", e.textSize.toDouble())
                put("color", color(e.textColor))
                put("bold", e.bold)
                put("align", e.align.name.lowercase())
            })
        }
        if (e.placeholder.isNotEmpty()) put("placeholder", e.placeholder)
        if (e.kind == Kind.SLIDER || e.kind == Kind.SWITCH || e.kind == Kind.CHECKBOX) put("value", e.value.toDouble())
        if (!e.visible) put("visible", false)
        if (e.locked) put("locked", true)
        e.link?.let { put("link", JSONObject().apply { put("target", it.target); put("transition", it.transition.name.lowercase()) }) }
        e.componentId?.let { put("component", it) }
        if (e.overrides.isNotEmpty()) put("overrides", JSONObject(e.overrides.toMap()))
    }

    private fun element(o: JSONObject): Element {
        val kind = runCatching { Kind.valueOf(o.getString("kind").uppercase()) }.getOrDefault(Kind.RECT)
        val e = Element(o.getString("id"), kind)
        e.name = o.optString("name", kind.label)
        e.x = o.optDouble("x", 0.0).toFloat()
        e.y = o.optDouble("y", 0.0).toFloat()
        e.w = o.optDouble("w", 100.0).toFloat()
        e.h = o.optDouble("h", 100.0).toFloat()
        o.optJSONObject("style")?.let { s ->
            e.fill = color(s, "fill")
            e.stroke = color(s, "stroke")
            e.strokeWidth = s.optDouble("strokeWidth", 1.0).toFloat()
            e.radius = s.optDouble("radius", 0.0).toFloat()
            e.opacity = s.optDouble("opacity", 1.0).toFloat()
            e.shadow = s.optBoolean("shadow", false)
        }
        o.optJSONObject("text")?.let { t ->
            e.text = t.optString("content", "")
            e.textSize = t.optDouble("size", 16.0).toFloat()
            e.textColor = color(t, "color") ?: Element.DARK
            e.bold = t.optBoolean("bold", false)
            e.align = runCatching { HAlign.valueOf(t.optString("align", "left").uppercase()) }.getOrDefault(HAlign.LEFT)
        }
        e.placeholder = o.optString("placeholder", "")
        e.value = o.optDouble("value", 0.5).toFloat()
        e.visible = o.optBoolean("visible", true)
        e.locked = o.optBoolean("locked", false)
        o.optJSONObject("link")?.let { l ->
            val tr = runCatching { Transition.valueOf(l.optString("transition", "none").uppercase()) }.getOrDefault(Transition.NONE)
            e.link = Link(l.getString("target"), tr)
        }
        e.componentId = o.optStringOrNull("component")
        o.optJSONObject("overrides")?.let { ov -> ov.keys().forEach { k -> e.overrides[k] = ov.getString(k) } }
        return e
    }

    private fun color(c: Int?): Any = c?.let { "#%08X".format(it) } ?: JSONObject.NULL

    private fun color(o: JSONObject, key: String): Int? {
        if (!o.has(key) || o.isNull(key)) return null
        return parseColor(o.getString(key))
    }

    /** „#RRGGBB“ oder „#AARRGGBB“ → Farbe, sonst `null`. */
    fun parseColor(s: String): Int? {
        val hex = s.trim().removePrefix("#")
        val v = hex.toLongOrNull(16) ?: return null
        return when (hex.length) {
            6 -> (0xFF000000 or v).toInt()
            8 -> v.toInt()
            else -> null
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
}
