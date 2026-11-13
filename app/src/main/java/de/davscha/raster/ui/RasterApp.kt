package de.davscha.raster.ui

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.davscha.raster.EditorState
import de.davscha.raster.io.Exporter
import de.davscha.raster.io.ProjectStore
import de.davscha.raster.io.RasterJson
import de.davscha.raster.settings.AppSettings
import de.davscha.raster.settings.LayoutMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val autosaveLock = Mutex()

/** Sichert das Projekt im App-Speicher, falls sich seit der letzten Sicherung etwas geändert hat. */
private fun autosave(scope: CoroutineScope, context: Context, state: EditorState) {
    val json = state.jsonForAutosave(force = false) ?: return
    scope.launch(Dispatchers.IO) {
        autosaveLock.withLock { runCatching { ProjectStore.writeAutosave(context, json) } }
    }
}

@Composable
fun RasterApp(settings: AppSettings) {
    val state = remember { EditorState(settings) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun saveTo(uri: Uri) {
        val json = state.json()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    ProjectStore.writeToUri(context, uri, json)
                    ProjectStore.displayName(context, uri)
                }
            }
            result.fold(
                onSuccess = { name ->
                    state.setFile(uri, name)
                    state.markSaved()
                    state.message = "Gespeichert: ${name ?: "Projekt"}"
                },
                onFailure = {
                    state.fileUri = null
                    state.message = "Speichern fehlgeschlagen: ${it.message}"
                },
            )
        }
    }

    // Mit „application/json“ würde Android ein .json anhängen (Projekt.raster.json), daher octet-stream
    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            ProjectStore.keepPermission(context, uri)
            saveTo(uri)
        }
    }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            ProjectStore.keepPermission(context, uri)
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { ProjectStore.readFromUri(context, uri) to ProjectStore.displayName(context, uri) }
                }
                result.fold(
                    onSuccess = { (doc, name) ->
                        state.loadDocument(doc, name, uri)
                        state.message = "Geöffnet: ${name ?: "Projekt"}"
                    },
                    onFailure = { state.message = "Öffnen fehlgeschlagen: ${it.message}" },
                )
            }
        }
    }

    val actions = remember(state) {
        EditorActions(
            export = { state.showExport = true },
            save = {
                val uri = state.fileUri
                if (uri != null) saveTo(uri) else createFile.launch("Projekt.${RasterJson.FORMAT}")
            },
            saveAs = {
                val base = state.fileName?.removeSuffix(".${RasterJson.FORMAT}") ?: "Projekt"
                createFile.launch("$base.${RasterJson.FORMAT}")
            },
            open = { openFile.launch(arrayOf("*/*")) },
        )
    }

    // Beim Start: letzte Sitzung wiederherstellen
    LaunchedEffect(Unit) {
        val restored = withContext(Dispatchers.IO) { ProjectStore.readAutosave(context) }
        if (restored != null) {
            val uri = settings.lastFileUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
            state.loadDocument(restored, settings.lastFileName, uri)
            state.autosavedRevision = state.revision
        }
    }

    // Alle 20 Sekunden sichern, wenn sich etwas geändert hat
    LaunchedEffect(state) {
        while (true) {
            delay(20_000)
            autosave(scope, context, state)
        }
    }

    // Beim Verlassen der App sofort sichern
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) autosave(scope, context, state)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            state.message = null
        }
    }

    // „Automatisch“: Querformat Studio, Hochformat Schwebend
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val layout = when (settings.layout) {
        LayoutMode.AUTO -> if (landscape) LayoutMode.STUDIO else LayoutMode.FLOATING
        else -> settings.layout
    }
    val floating = layout == LayoutMode.FLOATING

    RasterTheme(floating) {
        Box(
            Modifier
                .fillMaxSize()
                .background(if (floating) RasterColors.FloatingBackground else RasterColors.StudioBackground)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            if (floating) FloatingLayout(state, actions) else StudioLayout(state, actions)
            state.previewStart?.let { start ->
                PreviewOverlay(state, start) { state.previewStart = null }
            }
        }
        state.colorRequest?.let { req -> ColorPickerDialog(req) { state.colorRequest = null } }
        if (state.showSettings) SettingsDialog(state) { state.showSettings = false }
        if (state.showNew) NewDocumentDialog(state) { state.showNew = false }
        if (state.showExport) {
            ExportDialog(
                state,
                onDismiss = { state.showExport = false },
                onExport = { boards, format, scale ->
                    state.showExport = false
                    // Kopie, damit Weiterarbeiten den Export nicht stört
                    val doc = RasterJson.read(state.json())
                    val copies = boards.mapNotNull { b -> doc.allBoards().firstOrNull { it.id == b.id } }
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { Exporter.save(context, doc, copies, format, scale) }
                        }
                        state.message = result.fold(
                            onSuccess = { "${copies.size} Bild(er) gespeichert unter $it" },
                            onFailure = { "Export fehlgeschlagen: ${it.message}" },
                        )
                    }
                },
            )
        }
    }
}
