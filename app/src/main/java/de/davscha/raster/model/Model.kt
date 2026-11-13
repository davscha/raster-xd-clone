package de.davscha.raster.model

import android.graphics.RectF
import java.util.UUID

/** Alle Elementarten. Grundformen und fertige UI-Bausteine, dazu Instanzen von Komponenten. */
enum class Kind(val label: String) {
    RECT("Rechteck"),
    ELLIPSE("Ellipse"),
    LINE("Linie"),
    TEXT("Text"),
    BUTTON("Knopf"),
    INPUT("Eingabefeld"),
    SLIDER("Slider"),
    SWITCH("Schalter"),
    CHECKBOX("Checkbox"),
    LIST("Liste"),
    CARD("Karte"),
    NAVBAR("Navigationsleiste"),
    INSTANCE("Instanz");

    /** Bausteine, bei denen ein Text zum Aussehen gehört. */
    val hasText: Boolean
        get() = this in setOf(TEXT, BUTTON, INPUT, CHECKBOX, LIST, CARD, NAVBAR)

    val hasRadius: Boolean
        get() = this in setOf(RECT, BUTTON, INPUT, LIST, CARD)
}

/** Wie der Übergang im Prototyp aussieht. */
enum class Transition(val label: String) {
    NONE("Sofort"),
    FADE("Überblenden"),
    SLIDE_LEFT("Nach links schieben"),
    SLIDE_RIGHT("Nach rechts schieben"),
    SLIDE_UP("Von unten"),
}

enum class HAlign(val label: String) { LEFT("Links"), CENTER("Mitte"), RIGHT("Rechts") }

/** Ziel beim Antippen in der Vorschau. `target` ist eine Screen-ID oder [BACK]. */
class Link(var target: String, var transition: Transition) {
    companion object {
        const val BACK = "back"
    }
}

fun newId(): String = UUID.randomUUID().toString().substring(0, 8)

/**
 * Ein Element auf einem Screen oder in einer Komponente.
 * `x`, `y` sind relativ zur linken oberen Ecke des Screens. Bei Linien geht der Strich
 * von (x, y) nach (x + w, y + h), `w` und `h` dürfen dort negativ sein.
 */
class Element(var id: String, var kind: Kind) {
    var name: String = kind.label
    var x = 0f
    var y = 0f
    var w = 100f
    var h = 100f
    var fill: Int? = 0xFFD9DBE1.toInt()
    var stroke: Int? = null
    var strokeWidth = 1f
    var radius = 0f
    var opacity = 1f
    var shadow = false

    var text = ""
    var textSize = 16f
    var textColor: Int = DARK
    var bold = false
    var align = HAlign.LEFT

    /** Grauer Hinweistext im Eingabefeld, solange `text` leer ist. */
    var placeholder = ""

    /** Slider: 0..1. Schalter und Checkbox: an, wenn ≥ 0,5. */
    var value = 0.5f

    var visible = true
    var locked = false
    var link: Link? = null

    /** Nur bei Instanzen: die Komponente, die gezeichnet wird. */
    var componentId: String? = null

    /** Nur bei Instanzen: geänderte Texte und Farben, Schlüssel „<Element-ID>.text“ bzw. „<Element-ID>.fill“. */
    val overrides = mutableMapOf<String, String>()

    val on: Boolean get() = value >= 0.5f

    /** Rahmen relativ zum Screen, immer mit positiver Breite und Höhe. */
    fun bounds(): RectF = RectF(minOf(x, x + w), minOf(y, y + h), maxOf(x, x + w), maxOf(y, y + h))

    fun copy(newId: String = id): Element {
        val e = Element(newId, kind)
        e.name = name; e.x = x; e.y = y; e.w = w; e.h = h
        e.fill = fill; e.stroke = stroke; e.strokeWidth = strokeWidth; e.radius = radius
        e.opacity = opacity; e.shadow = shadow
        e.text = text; e.textSize = textSize; e.textColor = textColor; e.bold = bold; e.align = align
        e.placeholder = placeholder; e.value = value
        e.visible = visible; e.locked = locked
        e.link = link?.let { Link(it.target, it.transition) }
        e.componentId = componentId
        e.overrides.putAll(overrides)
        return e
    }

    companion object {
        const val DARK = 0xFF1C1D21.toInt()
        const val ACCENT = 0xFF3B7CF0.toInt()
        const val LINE_GRAY = 0xFFD6D8DE.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}

/**
 * Ein Screen (Zeichenfläche) oder eine Komponente. Beide sind gleich aufgebaut:
 * Größe, Hintergrund und Elemente. Komponenten liegen immer bei (0, 0).
 */
class Board(var id: String, var name: String, var x: Float, var y: Float, var w: Float, var h: Float) {
    var background: Int? = Element.WHITE
    val children = mutableListOf<Element>()

    fun rect(): RectF = RectF(x, y, x + w, y + h)
    fun contains(px: Float, py: Float) = px >= x && px <= x + w && py >= y && py <= y + h
}

class Document {
    val screens = mutableListOf<Board>()
    val components = mutableListOf<Board>()

    /** Mit diesem Screen beginnt die Vorschau. */
    var startScreen: String? = null

    fun screen(id: String?): Board? = screens.firstOrNull { it.id == id }
    fun component(id: String?): Board? = components.firstOrNull { it.id == id }

    /** Alle Screens und Komponenten. */
    fun allBoards(): List<Board> = screens + components

    companion object {
        fun new(w: Float, h: Float): Document {
            val d = Document()
            d.screens += Board(newId(), "Screen 1", 0f, 0f, w, h)
            d.startScreen = d.screens[0].id
            return d
        }
    }
}

/** Vorlagen für neue Screens. */
enum class ScreenPreset(val label: String, val w: Int, val h: Int) {
    TABLET_LANDSCAPE("Tablet quer", 1280, 800),
    TABLET_PORTRAIT("Tablet hoch", 800, 1280),
    TAB_S9("Tab S9+ (Pixel)", 2800, 1752),
    PHONE("Handy", 360, 800),
    DESKTOP("Desktop", 1440, 900),
}
