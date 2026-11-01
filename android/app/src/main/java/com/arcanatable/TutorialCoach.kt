@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.arcanatable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.composed
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bounds of opt-in Compose tutorial targets. These are only layout coordinates, never card data.
 * Each is kept with the window it was laid out in: Settings, the Play sheet, the deck picker and
 * other dialogs are separate windows, and a coach can only highlight controls in its own window.
 */
internal object TutorialTargets {
    /** Bounds in the target's own window. */
    val bounds = mutableStateMapOf<String, Rect>()
    /** The root view of the window each target is in. */
    val windows = mutableStateMapOf<String, android.view.View>()
    /** The id actually on screen in [root]: "hand-card:any" means whichever card in hand is showing. */
    fun resolve(target: String, root: android.view.View): String? {
        if (!target.endsWith(":any")) return target.takeIf { windows[it] === root }
        val prefix = target.removeSuffix("any")
        return bounds.entries.filter { (id, r) -> id.startsWith(prefix) && windows[id] === root && r.width > 1f && r.height > 1f }
            .minByOrNull { (_, r) -> r.left + r.top * 10f }?.key
    }
    /** Which on-screen control last reported each id (two copies of a card share one id). */
    internal val owners = HashMap<String, Any>()
}
internal fun Modifier.tutorialTarget(id: String): Modifier = composed {
    val root = LocalView.current.rootView
    // When this control's step comes up, scroll any list it sits in so it is on screen.
    val requester = remember { BringIntoViewRequester() }
    val wanted = TutorialCoachState.model?.let { m -> m.lesson.steps.getOrNull(m.index)?.target } == id
    LaunchedEffect(wanted) { if (wanted) { delay(150); runCatching { requester.bringIntoView() } } }
    val owner = remember { Any() }
    // A control that leaves the screen must not leave its highlight behind on an empty spot,
    // but another copy still showing (a second Forest, the other hand button) keeps it.
    DisposableEffect(id, root) {
        onDispose {
            if (TutorialTargets.owners[id] === owner) {
                TutorialTargets.owners.remove(id); TutorialTargets.bounds.remove(id); TutorialTargets.windows.remove(id)
            }
        }
    }
    bringIntoViewRequester(requester).onGloballyPositioned {
        if (it.isAttached) {
            val rect = it.boundsInWindow()
            // An offscreen or collapsed copy never replaces a visible one.
            if (rect.width > 1f && rect.height > 1f || TutorialTargets.owners[id] == null || TutorialTargets.owners[id] === owner) {
                TutorialTargets.bounds[id] = rect; TutorialTargets.windows[id] = root; TutorialTargets.owners[id] = owner
            }
        }
    }
}

/** What the coach shows right now; set by the activity, drawn by the topmost [TutorialLayer]. */
internal class TutorialCoachModel(
    val lesson: ArcanaLesson,
    val index: Int,
    val practice: Boolean,
    val onNext: () -> Unit,
    val onSkip: () -> Unit,
)

internal object TutorialCoachState {
    var model by mutableStateOf<TutorialCoachModel?>(null)
    /** Layers in the order their windows opened; the last one is on top and draws the coach. */
    internal val layers = mutableStateListOf<Any>()
    /** The window the coach was last drawn in (checked by the tutorial audit test). */
    internal var drawnIn: android.view.View? = null
}

/**
 * Where the coach is drawn. The activity has one, and every dialog or sheet a lesson visits ends
 * its content with one. Only the most recently opened layer draws, so the coach card and its
 * buttons are always on the window the player is looking at, never hidden behind it.
 */
@Composable
internal fun TutorialLayer(modifier: Modifier = Modifier.fillMaxSize(), base: Boolean = false, compact: Boolean = false) {
    val key = remember { Any() }
    DisposableEffect(key) {
        // The activity's own layer is always underneath every dialog, whatever composed first.
        if (base) TutorialCoachState.layers.add(0, key) else TutorialCoachState.layers.add(key)
        onDispose { TutorialCoachState.layers.remove(key) }
    }
    if (TutorialCoachState.layers.lastOrNull() !== key) return
    val model = TutorialCoachState.model ?: return
    val window = LocalView.current.rootView
    SideEffect { TutorialCoachState.drawnIn = window }
    if (compact) TutorialCompactCard(model, modifier)
    else TutorialCoachOverlay(model.lesson, model.index, model.practice, model.onNext, model.onSkip, modifier = modifier)
}

/** The coach inside a small popup (the game menu): same words and buttons, no screen shade. */
@Composable
private fun TutorialCompactCard(model: TutorialCoachModel, modifier: Modifier) {
    val step = model.lesson.steps.getOrNull(model.index) ?: return
    Column(modifier.padding(horizontal = 10.dp, vertical = 6.dp).background(Color(0xFF12241F), RoundedCornerShape(10.dp))
        .border(1.dp, Color(0xFFE6B65C), RoundedCornerShape(10.dp)).padding(10.dp).testTag("tutorial-coach-tip"),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${model.index + 1} / ${model.lesson.steps.size} · " + step.title, color = Color(0xFFE6B65C), fontWeight = FontWeight.Black, fontSize = 13.sp)
        Text(step.body, color = Color.White, fontSize = 12.sp, lineHeight = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = model.onSkip, modifier = Modifier.height(40.dp)) { Text("EXIT") }
            if (stepHasNext(step, model.practice)) Button(onClick = model.onNext, modifier = Modifier.height(40.dp)) { Text("NEXT") }
            else Text(step.hint.ifBlank { "Use the highlighted control" }, color = Color(0xFFE6B65C), fontSize = 11.sp, modifier = Modifier.weight(1f))
        }
    }
}

/** Gated drill steps wait for the real control; everything else can always move on with NEXT. */
internal fun stepHasNext(step: ArcanaLessonStep, practice: Boolean): Boolean = step.actionKind == "next" || !practice

/** Pure gate: only a corresponding engine event or gesture may complete an actionable step. */
internal object TutorialStepGate {
    fun accepts(step: ArcanaLessonStep, kind: String, target: String): Boolean {
        if (step.actionKind != kind) return false
        if (kind == "event") return step.event == target
        if (step.target == target) return true
        return (step.target == "hand-card:any" && target.startsWith("hand-card:")) ||
            (step.target == "battlefield-card:any" && target.startsWith("battlefield-card:"))
    }
}

/** Facts already confirmed by the engine while a player is reading an explanation.
 * Priority passes remain fresh gestures: an earlier pass must not satisfy a later prompt.
 */
internal class TutorialMilestones {
    private val confirmed = mutableSetOf<String>()
    fun observe(event: String) {
        if (event in setOf("SpellResolved", "AttackersDeclared", "CombatDamageDealt", "TurnAdvanced"))
            confirmed.add(event)
    }
    fun completes(step: ArcanaLessonStep?): Boolean =
        step?.actionKind == "event" && step.event in confirmed
    fun clear() = confirmed.clear()
}

/** Activity-local actions emitted by real control handlers, not by touching the coach overlay. */
internal object TutorialActions {
    var onGesture: ((String, String) -> Unit)? = null
    fun tap(target: String) { onGesture?.invoke(target, "tap") }
    fun hold(target: String) { onGesture?.invoke(target, "hold") }
}


/**
 * The coach lives in a region disjoint from the highlighted control; the real
 * card content can scroll inside that region instead of relying on a fixed
 * estimated text height. Coordinates are relative to the coach window.
 */
internal data class TutorialCoachTarget(val left: Dp, val top: Dp, val right: Dp, val bottom: Dp)
internal data class TutorialCoachRegion(
    val x: Dp, val y: Dp, val width: Dp, val height: Dp,
    val bottomAligned: Boolean = false,
    val dock: String = "top",
)

internal fun tutorialCoachRegion(maxW: Dp, maxH: Dp, target: TutorialCoachTarget?): TutorialCoachRegion {
    val margin = 12.dp
    val gap = 10.dp
    val cardWidth = (maxW - margin * 2).coerceAtLeast(1.dp).coerceAtMost(360.dp)
    val fullHeight = (maxH - margin * 2).coerceAtLeast(1.dp)
    if (target == null) return TutorialCoachRegion(margin, margin, cardWidth, fullHeight)
    val above = (target.top - gap - margin).coerceAtLeast(0.dp)
    val below = (maxH - target.bottom - gap - margin).coerceAtLeast(0.dp)
    val left = (target.left - gap - margin).coerceAtLeast(0.dp)
    val right = (maxW - target.right - gap - margin).coerceAtLeast(0.dp)
    // A wide unfolded phone can use a side rail when the target splits a short window.
    if (maxOf(above, below) < 180.dp && maxOf(left, right) >= 220.dp) {
        return if (right >= left)
            TutorialCoachRegion(target.right + gap, margin, right.coerceAtMost(360.dp), fullHeight, dock = "right")
        else TutorialCoachRegion(margin, margin, left.coerceAtMost(360.dp), fullHeight, dock = "left")
    }
    return if (below >= above)
        TutorialCoachRegion(margin, target.bottom + gap, cardWidth, below.coerceAtLeast(1.dp), dock = "below")
    else TutorialCoachRegion(margin, margin, cardWidth, above.coerceAtLeast(1.dp), bottomAligned = true, dock = "above")
}

/**
 * Coach marks displayed above the actual table. The genuine game remains underneath.
 * Gated steps have no Next control; the tutorial advances only on matching gestures or
 * state changes from Argentum. Skipping always remains possible.
 */
@Composable
internal fun TutorialCoachOverlay(
    lesson: ArcanaLesson,
    index: Int,
    practice: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    avoidTableOverlay: Boolean = false,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val step = lesson.steps.getOrNull(index) ?: return
    val gold = Color(0xFFE6B65C)
    val muted = Color(0xFFBAC6BE)
    val density = LocalDensity.current
    val root = LocalView.current.rootView
    var origin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier.testTag("tutorial-coach").onGloballyPositioned { origin = it.positionInWindow() }) {
        val maxW = maxWidth
        val maxH = maxHeight
        // A target from a scrolled list can extend outside its window. Clip
        // to the visible area; only the visible control may receive the tap.
        val rect = if (avoidTableOverlay) null else step.target?.let { TutorialTargets.resolve(it, root) }
            ?.let(TutorialTargets.bounds::get)?.translate(-origin)
        val target = rect?.let {
            TutorialCoachTarget(
                with(density) { it.left.toDp() }.coerceIn(0.dp, maxW),
                with(density) { it.top.toDp() }.coerceIn(0.dp, maxH),
                with(density) { it.right.toDp() }.coerceIn(0.dp, maxW),
                with(density) { it.bottom.toDp() }.coerceIn(0.dp, maxH),
            )
        }?.takeIf { it.right > it.left && it.bottom > it.top }
        if (target != null) {
            val dark = Color.Black.copy(alpha = 0.58f)
            @Composable fun Shade(x: Dp, y: Dp, w: Dp, h: Dp) {
                if (w > 0.dp && h > 0.dp) Box(
                    Modifier.offset(x, y).size(w, h).background(dark)
                        .pointerInput(Unit) { detectTapGestures {} }
                )
            }
            Shade(0.dp, 0.dp, maxW, target.top)
            Shade(0.dp, target.bottom, maxW, maxH - target.bottom)
            Shade(0.dp, target.top, target.left, target.bottom - target.top)
            Shade(target.right, target.top, maxW - target.right, target.bottom - target.top)
            // Not clickable: touches in the highlighted hole reach the real control.
            Box(Modifier.offset(target.left, target.top).size(target.right - target.left, target.bottom - target.top)
                .border(3.dp, gold, RoundedCornerShape(9.dp)))
        }
        // The card's actual height is bounded by free space. Unlike the old
        // 216dp guess, this also works for long text and smaller folded windows.
        val region = tutorialCoachRegion(maxW, maxH, target)
        val scroll = rememberScrollState()
        LaunchedEffect(lesson, index) { scroll.scrollTo(0) }
        Box(
            Modifier.offset(region.x, region.y).size(region.width, region.height),
            contentAlignment = if (region.bottomAligned) Alignment.BottomStart else Alignment.TopStart,
        ) {
            Surface(
                Modifier.fillMaxWidth().heightIn(max = region.height).testTag("tutorial-coach-tip"),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF12241F), shadowElevation = 10.dp,
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Column(Modifier.weight(1f, fill = false).verticalScroll(scroll),
                        verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("ARCANA  /  " + lesson.title.uppercase(), color = gold,
                                fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                            Text((index + 1).toString() + " / " + lesson.steps.size, color = muted, fontSize = 11.sp)
                        }
                        Text(step.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(step.body, color = Color(0xFFF0EEE6), fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    // Actions stay accessible while the instruction copy scrolls.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = onSkip, modifier = Modifier.height(45.dp)) { Text("EXIT") }
                        if (stepHasNext(step, practice)) {
                            Button(onClick = onNext, modifier = Modifier.height(45.dp)) { Text("NEXT") }
                        } else {
                            Text(step.hint.ifBlank { "Use the highlighted control" },
                                color = gold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
