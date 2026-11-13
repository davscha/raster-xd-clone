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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.raster.EditorState
import de.davscha.raster.model.Board
import de.davscha.raster.model.Link
import de.davscha.raster.model.Transition
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
            if (b != null) PreviewScreen(state, b) { follow(it) }
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
private fun PreviewScreen(state: EditorState, b: Board, onLink: (Link) -> Unit) {
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(b.id) {
                detectTapGestures { p ->
                    val s = min(size.width / b.w, size.height / b.h)
                    val ox = (size.width - b.w * s) / 2f
                    val oy = (size.height - b.h * s) / 2f
                    val x = (p.x - ox) / s
                    val y = (p.y - oy) / s
                    // Oberstes sichtbares Element mit Verbindung unter dem Finger
                    val hit = b.children.lastOrNull { it.visible && it.link != null && it.bounds().contains(x, y) }
                    hit?.link?.let(onLink)
                }
            },
    ) {
        val s = min(size.width / b.w, size.height / b.h)
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            nc.save()
            nc.translate((size.width - b.w * s) / 2f, (size.height - b.h * s) / 2f)
            nc.scale(s, s)
            nc.translate(-b.x, -b.y)
            state.renderer.drawBoard(nc, b, state.doc)
            nc.restore()
        }
    }
}
