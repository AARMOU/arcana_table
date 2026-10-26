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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bounds of opt-in Compose tutorial targets. These are only layout coordinates, never card data.
 * A missing/hidden target is shown as a non-blocking instructional panel, not a fake hotspot.
 */
internal object TutorialTargets {
    val bounds = mutableStateMapOf<String, Rect>()
}
internal fun Modifier.tutorialTarget(id: String): Modifier =
    this.onGloballyPositioned { TutorialTargets.bounds[id] = it.boundsInRoot() }

/** Activity-local actions emitted by real control handlers, not by touching the coach overlay. */
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

internal object TutorialActions {
    var onGesture: ((String, String) -> Unit)? = null
    fun tap(target: String) { onGesture?.invoke(target, "tap") }
    fun hold(target: String) { onGesture?.invoke(target, "hold") }
}

@Composable
internal fun TutorialInlineCue(title: String, hint: String) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF29392F), RoundedCornerShape(10.dp))
        .border(1.dp, Color(0xFFE6B65C), RoundedCornerShape(10.dp))
        .padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("ARCANA TUTORIAL · " + title, fontWeight = FontWeight.Black,
            color = Color(0xFFE6B65C), fontSize = 12.sp)
        Text(hint, color = Color.White, fontSize = 12.sp)
    }
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
) {
    val step = lesson.steps.getOrNull(index) ?: return
    val gold = Color(0xFFE6B65C)
    val muted = Color(0xFFBAC6BE)
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().testTag("tutorial-coach")) {
        val maxW = maxWidth
        val maxH = maxHeight
        val rect = if (avoidTableOverlay) null else step.target?.let(TutorialTargets.bounds::get)
        val valid = rect != null && rect.width > 1f && rect.height > 1f
        val target = if (valid) rect else null
        val left = if (target == null) 0.dp else with(density) { target.left.toDp() }.coerceIn(0.dp, maxW)
        val top = if (target == null) 0.dp else with(density) { target.top.toDp() }.coerceIn(0.dp, maxH)
        val right = if (target == null) maxW else with(density) { target.right.toDp() }.coerceIn(0.dp, maxW)
        val bottom = if (target == null) maxH else with(density) { target.bottom.toDp() }.coerceIn(0.dp, maxH)
        if (target != null) {
            val dark = Color.Black.copy(alpha = 0.58f)
            @Composable fun Shade(x: androidx.compose.ui.unit.Dp, y: androidx.compose.ui.unit.Dp,
                                  w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp) {
                if (w > 0.dp && h > 0.dp) Box(
                    Modifier.offset(x, y).size(w, h).background(dark)
                        .pointerInput(Unit) { detectTapGestures {} }
                )
            }
            Shade(0.dp, 0.dp, maxW, top)
            Shade(0.dp, bottom, maxW, maxH - bottom)
            Shade(0.dp, top, left, bottom - top)
            Shade(right, top, maxW - right, bottom - top)
            // Not clickable: touches in the highlighted hole reach the real control.
            Box(Modifier.offset(left, top).size(right - left, bottom - top)
                .border(3.dp, gold, RoundedCornerShape(9.dp)))
        }
        val cardY = when {
            target == null -> (maxH * 0.23f).coerceAtLeast(24.dp)
            top > maxH * 0.43f -> 16.dp
            else -> (maxH - 216.dp).coerceAtLeast((bottom + 8.dp).coerceAtMost(maxH - 216.dp))
        }
        Surface(Modifier.offset(x = 12.dp, y = cardY).widthIn(max = (maxW - 24.dp))
            .testTag("tutorial-coach-tip"), shape = RoundedCornerShape(14.dp),
            color = Color(0xFF12241F), shadowElevation = 10.dp) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ARCANA  /  " + lesson.title.uppercase(), color = gold,
                        fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    Text("${index + 1} / ${lesson.steps.size}", color = muted, fontSize = 11.sp)
                }
                Text(step.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(step.body, color = Color(0xFFF0EEE6), fontSize = 13.sp, lineHeight = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onSkip, modifier = Modifier.height(45.dp)) { Text("EXIT") }
                    if (step.actionKind == "next" || !practice) {
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
