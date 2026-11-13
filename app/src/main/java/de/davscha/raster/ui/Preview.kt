package de.davscha.raster.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.raster.EditorState
import de.davscha.raster.model.Board
import de.davscha.raster.model.Kind
import de.davscha.raster.model.Link
import de.davscha.raster.model.Transition
import de.davscha.raster.render.LiveState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/** Ein Eintrag im Verlauf der Vorschau: Screen und der Übergang, mit dem man hingekommen ist. */
private data class Visit(val screen: String, val transition: Transition, val n: Int)

/** Vollbild-Vorschau: Antippen eines verbundenen Elements wechselt den Screen. Zurück-Taste geht zurück. */
@Composable
fun PreviewOverlay(state: EditorState, startId: String, onClose: () -> Unit) {
    val doc = state.doc
    val stack = remember { mutableStateListOf(Visit(startId, Transition.NONE, 0)) }
    var anim by remember { mutableStateOf(Transition.NONE) }
    var back by remember { mutableStateOf(false) }
    var counter by remember { mutableStateOf(1) }
    val live = remember { LiveState() }
    var tick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun flashHints() {
        live.hints = true
        tick++
        scope.launch {
            delay(600)
            live.hints = false
            tick++
        }
    }

    fun goBack() {
        if (stack.size <= 1) {
            onClose()
            return
        }
        anim = stack.last().transition
        back = true
        stack.removeAt(stack.lastIndex)
    }

    fun follow(link: Link) {
        if (link.target == Link.BACK) {
            if (stack.size > 1) goBack()
            return
        }
        if (doc.screen(link.target) == null) return
        anim = link.transition
        back = false
        live.focused = null
        stack.add(Visit(link.target, link.transition, counter++))
    }

    BackHandler { goBack() }

    Box(Modifier.fillMaxSize().background(Color(0xFF0E0E10))) {
        AnimatedContent(
            targetState = stack.last(),
            transitionSpec = { spec(anim, back) },
            label = "Vorschau",
            modifier = Modifier.fillMaxSize(),
        ) { visit ->
            val b = doc.screen(visit.screen)
            if (b != null) PreviewScreen(state, b, live, tick, onChange = { tick++ }, onHint = { flashHints() }) { follow(it) }
        }
        // Eingabefeld mit Fokus: unsichtbares Textfeld nimmt die Tastatur entgegen
        tick.let { }
        val focusedId = live.focused
        if (focusedId != null) {
            val requester = remember { FocusRequester() }
            val keyboard = LocalSoftwareKeyboardController.current
            val current = live.texts[focusedId]
                ?: doc.screens.firstNotNullOfOrNull { b -> b.children.firstOrNull { it.id == focusedId } }?.text ?: ""
            BasicTextField(
                value = TextFieldValue(current, TextRange(current.length)),
                onValueChange = {
                    live.texts[focusedId] = it.text
                    tick++
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    live.focused = null
                    tick++
                }),
                modifier = Modifier.size(1.dp).alpha(0f).focusRequester(requester),
            )
            LaunchedEffect(focusedId) {
                requester.requestFocus()
                keyboard?.show()
            }
        }
        Row(
            Modifier.align(Alignment.TopStart).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ToolButton(RasterIcons.ArrowLeft, "Zurück", enabled = stack.size > 1, size = 40.dp) { goBack() }
                    Text(
                        doc.screen(stack.last().screen)?.name ?: "",
                        fontSize = 14.sp,
                        color = RasterColors.Text,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    ToolButton(RasterIcons.Close, "Vorschau schließen", size = 40.dp) { onClose() }
                }
            }
        }
    }
}

private fun spec(t: Transition, back: Boolean): ContentTransform {
    val d = 300
    return when (t) {
        Transition.NONE -> EnterTransition.None togetherWith ExitTransition.None
        Transition.FADE -> fadeIn(tween(d)) togetherWith fadeOut(tween(d))
        Transition.SLIDE_LEFT, Transition.SLIDE_RIGHT -> {
            // Nach links schieben: Der neue Screen kommt von rechts. Zurück läuft es umgekehrt.
            val fromRight = (t == Transition.SLIDE_LEFT) != back
            if (fromRight) slideInHorizontally(tween(d)) { it } togetherWith slideOutHorizontally(tween(d)) { -it }
            else slideInHorizontally(tween(d)) { -it } togetherWith slideOutHorizontally(tween(d)) { it }
        }
        Transition.SLIDE_UP ->
            if (!back) slideInVertically(tween(d)) { it } togetherWith fadeOut(tween(d))
            else fadeIn(tween(d)) togetherWith slideOutVertically(tween(d)) { it }
    }
}

@Composable
private fun PreviewScreen(state: EditorState, b: Board, live: LiveState, tick: Int, onChange: () -> Unit, onHint: () -> Unit, onLink: (Link) -> Unit) {
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(b.id) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val s = min(size.width / b.w, size.height / b.h)
                    val ox = (size.width - b.w * s) / 2f
                    val oy = (size.height - b.h * s) / 2f
                    fun local(p: Offset) = Offset((p.x - ox) / s, (p.y - oy) / s)
                    val start = local(down.position)
                    // Oberstes sichtbares Element unter dem Finger
                    val hit = b.children.lastOrNull { it.visible && it.bounds().contains(start.x, start.y) }

                    // Slider ohne Verbindung: ziehen ändert den Wert
                    if (hit != null && hit.kind == Kind.SLIDER && hit.link == null) {
                        fun set(p: Offset) {
                            val r = (hit.h / 2f - 1f).coerceAtLeast(2f)
                            val x0 = hit.x + r
                            val x1 = (hit.x + hit.w - r).coerceAtLeast(x0 + 1f)
                            live.values[hit.id] = ((local(p).x - x0) / (x1 - x0)).coerceIn(0f, 1f)
                            onChange()
                        }
                        live.focused = null
                        set(down.position)
                        down.consume()
                        while (true) {
                            val e = awaitPointerEvent()
                            val c = e.changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) break
                            set(c.position)
                            c.consume()
                        }
                        return@awaitEachGesture
                    }

                    if (hit != null && (hit.link != null || hit.kind == Kind.BUTTON)) {
                        live.pressed = hit.id
                        onChange()
                    }
                    var moved = false
                    while (true) {
                        val e = awaitPointerEvent()
                        val c = e.changes.firstOrNull { it.id == down.id } ?: break
                        if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                        if (!c.pressed) break
                    }
                    live.pressed = null
                    if (!moved) {
                        val link = hit?.link
                        when {
                            link != null -> {
                                live.focused = null
                                onLink(link)
                            }
                            hit != null && (hit.kind == Kind.SWITCH || hit.kind == Kind.CHECKBOX) -> {
                                val v = live.values[hit.id] ?: hit.value
                                live.values[hit.id] = if (v >= 0.5f) 0f else 1f
                            }
                            hit != null && hit.kind == Kind.INPUT -> live.focused = hit.id
                            hit != null && hit.kind == Kind.BUTTON -> live.focused = null
                            else -> {
                                // Daneben getippt: kurz zeigen, wo man tippen kann (wie in XD)
                                live.focused = null
                                onHint()
                            }
                        }
                    }
                    onChange()
                }
            },
    ) {
        tick // Lesen sorgt für Neuzeichnen
        val s = min(size.width / b.w, size.height / b.h)
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            nc.save()
            nc.translate((size.width - b.w * s) / 2f, (size.height - b.h * s) / 2f)
            nc.scale(s, s)
            nc.translate(-b.x, -b.y)
            state.renderer.drawBoard(nc, b, state.doc, live = live)
            nc.restore()
        }
    }
}
