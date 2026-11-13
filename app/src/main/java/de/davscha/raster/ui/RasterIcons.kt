package de.davscha.raster.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Eigene Strich-Icons (24er Raster) wie in Lasur und Kontur. Pfade mit „F:“ davor werden gefüllt. */
object RasterIcons {
    private fun icon(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        for (raw in paths) {
            val filled = raw.startsWith("F:")
            val p = raw.removePrefix("F:")
            b.addPath(
                pathData = addPathNodes(p),
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    // Werkzeuge
    val Select by lazy { icon("select", "F:M6 3l13 8-6 1.5L9.5 19z") }
    val Rect by lazy { icon("rect", "M4 6h16v12H4z") }
    val Ellipse by lazy { icon("ellipse", "M3 12a9 6 0 1 0 18 0a9 6 0 1 0 -18 0") }
    val Line by lazy { icon("line", "M5 19L19 5") }
    val TextTool by lazy { icon("text", "M5 6V4h14v2M12 4v16M9 20h6") }
    val Screen by lazy { icon("screen", "M7 3v18M17 3v18M3 7h18M3 17h18") }
    val Multi by lazy { icon("multi", "M3 3h8v8H3z", "M13 13h8v8h-8z", "M17 4v6M14 7h6") }

    // Bausteine
    val Button by lazy { icon("button", "M3 8h18v8H3z", "M8 12h8") }
    val Input by lazy { icon("input", "M3 7h18v10H3z", "M7 10v4") }
    val Slider by lazy { icon("slider", "M3 12h18", "F:M10 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0") }
    val Toggle by lazy { icon("toggle", "M8 7h8a5 5 0 0 1 0 10H8a5 5 0 0 1 0-10z", "F:M13.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0") }
    val Checkbox by lazy { icon("checkbox", "M4 4h16v16H4z", "M8 12l3 3 5-6") }
    val ListIcon by lazy { icon("list", "M4 5h16v14H4z", "M4 9.7h16M4 14.3h16") }
    val Card by lazy { icon("card", "M4 4h16v16H4z", "M4 12h16", "M7 15.5h7") }
    val NavBar by lazy { icon("navbar", "M3 4h18v16H3z", "F:M3 4h18v4H3z") }

    // Komponenten und Prototyp
    val Component by lazy { icon("component", "F:M12 2l3 3-3 3-3-3z", "F:M5 9l3 3-3 3-3-3z", "F:M19 9l3 3-3 3-3-3z", "F:M12 16l3 3-3 3-3-3z") }
    val Instance by lazy { icon("instance", "M12 3l9 9-9 9-9-9z") }
    val Play by lazy { icon("play", "F:M7 4l13 8-13 8z") }
    val Link by lazy { icon("link", "M10 14l4-4", "M8 12l-2 2a3 3 0 0 0 4 4l2-2", "M16 12l2-2a3 3 0 0 0 -4-4l-2 2") }
    val Detach by lazy { icon("detach", "M12 3l9 9-9 9-9-9z", "M4 20L20 4") }

    // Datei und Bearbeiten
    val NewFile by lazy { icon("newFile", "M6 3h8l4 4v14H6z", "M14 3v4h4", "M12 11v6M9 14h6") }
    val Open by lazy { icon("open", "M3 6h6l2 2h10v11H3z") }
    val Save by lazy { icon("save", "M5 3h11l3 3v15H5z", "M8 3v5h7V3", "M8 21v-7h8v7") }
    val SaveAs by lazy { icon("saveAs", "M12 21H5V3h11l3 3v5", "M8 3v5h7V3", "M18 14v7M14.5 17.5h7") }
    val Export by lazy { icon("export", "M12 15V3M7 8l5-5 5 5", "M5 14v6h14v-6") }
    val Settings by lazy { icon("settings", "M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0", "M16 4v4M10 10v4M18 16v4") }
    val Undo by lazy { icon("undo", "M9 14L4 9l5-5", "M4 9h10a6 6 0 0 1 0 12h-3") }
    val Redo by lazy { icon("redo", "M15 14l5-5-5-5", "M20 9H10a6 6 0 0 0 0 12h3") }
    val Fit by lazy { icon("fit", "M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5") }
    val Copy by lazy { icon("copy", "M8 8h12v12H8z", "M4 16V4h12") }
    val Paste by lazy { icon("paste", "M9 3h6v3H9z", "M8 5H5v16h14V5h-3") }
    val Duplicate by lazy { icon("duplicate", "M8 8h12v12H8z", "M4 16V4h12", "M14 11v6M11 14h6") }
    val Trash by lazy { icon("trash", "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13") }
    val Forward by lazy { icon("forward", "M12 20V5M7 10l5-5 5 5") }
    val Backward by lazy { icon("backward", "M12 4v15M7 14l5 5 5-5") }
    val Eye by lazy { icon("eye", "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0") }
    val EyeOff by lazy { icon("eyeOff", "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z", "M3 3l18 18") }
    val Lock by lazy { icon("lock", "M5 11h14v10H5z", "M8 11V7a4 4 0 0 1 8 0v4") }
    val Unlock by lazy { icon("unlock", "M5 11h14v10H5z", "M8 11V7a4 4 0 0 1 7.5-2") }
    val Plus by lazy { icon("plus", "M12 5v14M5 12h14") }
    val Close by lazy { icon("close", "M6 6l12 12M18 6L6 18") }
    val Check by lazy { icon("check", "M5 12l5 5 9-10") }
    val Edit by lazy { icon("edit", "M4 20h4L19 9l-4-4L4 16z") }
    val ArrowLeft by lazy { icon("left", "M15 6l-6 6 6 6") }
    val ChevronRight by lazy { icon("chevRight", "M9 6l6 6-6 6") }
    val ChevronDown by lazy { icon("chevDown", "M6 9l6 6 6-6") }

    // Ausrichten
    val AlignLeft by lazy { icon("alignL", "M4 3v18", "M8 6h11v4H8z", "M8 14h7v4H8z") }
    val AlignCenterH by lazy { icon("alignCH", "M12 3v18", "M6 6h12v4H6z", "M8 14h8v4H8z") }
    val AlignRight by lazy { icon("alignR", "M20 3v18", "M5 6h11v4H5z", "M9 14h7v4H9z") }
    val AlignTop by lazy { icon("alignT", "M3 4h18", "M6 8h4v11H6z", "M14 8h4v7h-4z") }
    val AlignCenterV by lazy { icon("alignCV", "M3 12h18", "M6 6h4v12H6z", "M14 8h4v8h-4z") }
    val AlignBottom by lazy { icon("alignB", "M3 20h18", "M6 5h4v11H6z", "M14 9h4v7h-4z") }
}
