package de.davscha.raster.model

/** Fertige UI-Bausteine mit sinnvollen Startwerten (helles UI-Kit wie in XD). */
object Widgets {
    /** Die Bausteine in der Reihenfolge der Bibliothek. */
    val library = listOf(Kind.BUTTON, Kind.INPUT, Kind.SLIDER, Kind.SWITCH, Kind.CHECKBOX, Kind.LIST, Kind.CARD, Kind.NAVBAR)

    fun create(kind: Kind, boardW: Float): Element {
        val e = Element(newId(), kind)
        when (kind) {
            Kind.RECT, Kind.ELLIPSE -> {
                e.w = 120f; e.h = 120f
            }
            Kind.LINE -> {
                e.w = 160f; e.h = 0f
                e.fill = null; e.stroke = Element.DARK; e.strokeWidth = 2f
            }
            Kind.TEXT -> {
                e.w = 240f; e.h = 24f
                e.fill = null; e.text = "Text"; e.textSize = 18f
            }
            Kind.BUTTON -> {
                e.w = 160f; e.h = 48f
                e.fill = Element.ACCENT; e.radius = 10f
                e.text = "Knopf"; e.textColor = Element.WHITE; e.bold = true; e.align = HAlign.CENTER
            }
            Kind.INPUT -> {
                e.w = 280f; e.h = 48f
                e.fill = Element.WHITE; e.stroke = 0xFFC7C9D1.toInt(); e.strokeWidth = 1.5f; e.radius = 8f
                e.placeholder = "Eingabe …"
            }
            Kind.SLIDER -> {
                e.w = 240f; e.h = 28f
                e.fill = Element.ACCENT; e.stroke = Element.LINE_GRAY; e.value = 0.5f
            }
            Kind.SWITCH -> {
                e.w = 52f; e.h = 30f
                e.fill = Element.ACCENT; e.stroke = 0xFFC7C9D1.toInt(); e.value = 1f
            }
            Kind.CHECKBOX -> {
                e.w = 200f; e.h = 26f
                e.fill = Element.ACCENT; e.stroke = 0xFF8E909A.toInt(); e.value = 1f
                e.text = "Option"
            }
            Kind.LIST -> {
                e.w = 320f; e.h = 208f
                e.fill = Element.WHITE; e.stroke = 0xFFE3E4E8.toInt(); e.radius = 12f
                e.text = "Eintrag 1\nEintrag 2\nEintrag 3\nEintrag 4"
            }
            Kind.CARD -> {
                e.w = 300f; e.h = 200f
                e.fill = Element.WHITE; e.radius = 16f; e.shadow = true
                e.text = "Karte"; e.bold = true; e.textSize = 18f
            }
            Kind.NAVBAR -> {
                e.w = boardW; e.h = 64f
                e.fill = Element.WHITE; e.stroke = 0xFFE3E4E8.toInt()
                e.text = "Titel"; e.bold = true; e.textSize = 20f
            }
            Kind.INSTANCE -> {
                e.fill = null
            }
        }
        return e
    }
}
