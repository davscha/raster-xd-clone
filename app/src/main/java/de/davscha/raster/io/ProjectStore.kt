package de.davscha.raster.io

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import de.davscha.raster.model.Document
import java.io.File

/** Speichern und Laden: eigene Dateien über die Dateiauswahl und die automatische Sicherung. */
object ProjectStore {
    private fun autosaveFile(context: Context) = File(context.filesDir, "autosave.raster")

    /** Schreibt erst in eine Hilfsdatei und benennt sie dann um, damit nie eine halbe Sicherung übrig bleibt. */
    fun writeAutosave(context: Context, json: String) {
        val target = autosaveFile(context)
        val tmp = File(context.filesDir, "autosave.tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    fun readAutosave(context: Context): Document? {
        val f = autosaveFile(context)
        if (!f.exists()) return null
        return runCatching { RasterJson.read(f.readText()) }.getOrNull()
    }

    fun writeToUri(context: Context, uri: Uri, json: String) {
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Datei konnte nicht geöffnet werden")
        out.use { it.write(json.toByteArray()) }
    }

    fun readFromUri(context: Context, uri: Uri): Document {
        val input = context.contentResolver.openInputStream(uri) ?: error("Datei konnte nicht geöffnet werden")
        val text = input.use { it.readBytes().toString(Charsets.UTF_8) }
        return RasterJson.read(text)
    }

    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** Damit „Speichern“ auch nach einem Neustart in dieselbe Datei schreiben darf. */
    fun keepPermission(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }
}
