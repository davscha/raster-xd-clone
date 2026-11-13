package de.davscha.raster.io

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Environment
import android.provider.MediaStore
import de.davscha.raster.model.Board
import de.davscha.raster.model.Document
import de.davscha.raster.render.Renderer

enum class ImageFormat(val label: String, val ext: String, val mime: String) {
    PNG("PNG", "png", "image/png"),
    JPG("JPG", "jpg", "image/jpeg"),
}

/** Screens als Bilder nach „Bilder/Raster“ speichern. */
object Exporter {
    /** Liefert den Ordner, in dem die Bilder gelandet sind. */
    fun save(context: Context, doc: Document, boards: List<Board>, format: ImageFormat, scale: Float): String {
        val renderer = Renderer()
        for (b in boards) {
            val w = (b.w * scale).toInt().coerceIn(1, 8000)
            val h = (b.h * scale).toInt().coerceIn(1, 8000)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            if (format == ImageFormat.JPG) c.drawColor(0xFFFFFFFF.toInt())
            c.scale(w / b.w, h / b.h)
            c.translate(-b.x, -b.y)
            renderer.drawBoard(c, b, doc)
            write(context, bmp, safeName(b.name), format)
            bmp.recycle()
        }
        return "Bilder/Raster"
    }

    private fun write(context: Context, bmp: Bitmap, name: String, format: ImageFormat) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.${format.ext}")
            put(MediaStore.Images.Media.MIME_TYPE, format.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Raster")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Bild konnte nicht angelegt werden")
        resolver.openOutputStream(uri)?.use { out ->
            val cf = if (format == ImageFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            bmp.compress(cf, 95, out)
        } ?: error("Bild konnte nicht geschrieben werden")
    }

    private fun safeName(s: String): String = s.replace(Regex("[^A-Za-z0-9ÄÖÜäöüß _-]"), "_").ifBlank { "Screen" }
}
