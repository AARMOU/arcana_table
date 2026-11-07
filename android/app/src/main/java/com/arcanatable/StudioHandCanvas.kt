package com.arcanatable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import kotlin.math.roundToInt

internal data class StudioHandState(
    val labels: Map<String,String>, val enabled: Set<String>,
    val cards: List<GameCard>, val playable: Set<Int> = emptySet(), val selected: Int? = null,
)

internal fun studioLiveLabel(element: StudioElement, labels: Map<String,String>): String {
    val live=labels[element.binding] ?: return element.label
    return when(element.binding) {
        "pass" -> live // Actual primary action must never be disguised by a saved phase label.
        "actions" -> element.label.substringBefore('·').trim()+" · "+live.substringAfterLast('·').trim()
        "metric0","metric1","metric2","metric3" -> element.label.substringBefore('|').substringBefore('\n')+"\n"+live.substringAfterLast('\n')
        else -> element.label
    }
}

/** One coordinate renderer for the editing canvas and the actual native hand drawer. */
@Composable
internal fun StudioHandCanvas(layout: StudioLayout, state: StudioHandState,
    onAction: (String) -> Unit, onCard: (GameCard) -> Unit, onPreview: (GameCard) -> Unit,
    modifier: Modifier = Modifier, editing: Boolean = false, selectedElement: String? = null,
    onSelectElement: (String) -> Unit = {}, onMove: (String,Float,Float) -> Unit = { _,_,_ -> },
    onResize: (String,Float,Float) -> Unit = { _,_,_ -> }) {
    CompositionLocalProvider(LocalTableColors provides layout.colors(LocalTableColors.current)) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val move by rememberUpdatedState(onMove)
        val resize by rememberUpdatedState(onResize)
        val select by rememberUpdatedState(onSelectElement)
        val scale = maxWidth.value / layout.canvasWidth.takeIf {it.isFinite()}?.coerceIn(240f,2000f).let {it ?: 412f}
        val origin=layout.originY.takeIf {it.isFinite()} ?: 344f
        val density = LocalDensity.current
        val visible = layout.elements.filter { it.visible }
        val advice = if (editing) layout.advice() else emptyMap()
        val slots = visible.filter { it.kind == "card" }.sortedBy { it.binding.removePrefix("card").toIntOrNull() ?: 0 }
        // Preserve exported slots. Extra real cards continue below the entire design, in reachable scrolling rows.
        val lastIndex = slots.maxOfOrNull { it.binding.removePrefix("card").toIntOrNull() ?: 0 } ?: -1
        val bottom = visible.filter {it.y.isFinite() && it.h.isFinite()}.maxOfOrNull { it.y + it.h } ?: origin
        val template = slots.firstOrNull()
        val overflowColumns = if(template==null)1 else ((layout.canvasWidth-32f+8f)/(template.w+8f)).toInt().coerceAtLeast(1)
        val extra = if (!editing && template != null && state.cards.size > lastIndex+1)
            (lastIndex+1 until state.cards.size).map { i -> template.copy(id="overflow$i",binding="card$i",
                x=16f+((i-lastIndex-1)%overflowColumns)*(template.w+8f), y=bottom+8f+((i-lastIndex-1)/overflowColumns)*(template.h+8f)) }
            else emptyList()
        // An invalid in-progress draft must remain editable without crashing the native preview.
        // Apply rejects it; the actual game continues using its last validated layout.
        val renderElements = (visible + extra).filter { e ->
            listOf(e.x,e.y,e.w,e.h,e.fs,e.r).all {it.isFinite()} && e.w>0 && e.h>0 &&
                e.w<=4000 && e.h<=4000 && e.y<=4000 && e.fs in 1f..100f && e.r>=0
        }
        val extent = ((renderElements.maxOfOrNull { it.y+it.h } ?: origin)-origin).coerceIn(44f,4000f)
        // The hand design draws on the drawer's own surface colour, so the drawer is one colour top to bottom.
        // Owner note 3: no paint of its own. The drawer behind it owns the colour, so the drawer is one colour from its top
        // edge down (the canvas used to paint a different palette's panel colour under the design).
        Box(Modifier.fillMaxWidth().height((extent*scale).dp).testTag("studio-hand-canvas")) {
            renderElements.forEach { e ->
                val shape = RoundedCornerShape((e.r*scale).dp)
                var delta by remember(e.id) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                val gesture = if (editing) Modifier.pointerInput(e.id,e.locked,scale) {
                    detectDragGestures(onDragStart={ select(e.id); delta=androidx.compose.ui.geometry.Offset.Zero },
                        onDrag={ change,amount -> change.consume(); delta+=amount },
                        onDragCancel={delta=androidx.compose.ui.geometry.Offset.Zero},
                        onDragEnd={ if (!e.locked) move(e.id, with(density){delta.x.toDp().value}/scale, with(density){delta.y.toDp().value}/scale); delta=androidx.compose.ui.geometry.Offset.Zero })
                } else Modifier
                // Owner note 8: in play, a button drawn smaller than a fingertip grows around its centre to 44dp
                // (the editor shows it at its true size, marked red). Cards keep their exact size.
                val grow = !editing && e.kind != "card"
                val drawW = if (grow) maxOf(e.w*scale,44f) else e.w*scale
                val drawH = if (grow) maxOf(e.h*scale,44f) else e.h*scale
                Box(Modifier.offset((e.x*scale-(drawW-e.w*scale)/2).dp,((e.y-origin)*scale-(drawH-e.h*scale)/2).dp)
                    .offset { androidx.compose.ui.unit.IntOffset(delta.x.roundToInt(),delta.y.roundToInt()) }
                    .size(drawW.dp,drawH.dp)
                    .testTag("studio-bounds-${e.id}").then(gesture)
                    .border(if (editing && (selectedElement==e.id || e.id in advice)) 2.dp else 0.dp,
                        if (e.id in advice) androidx.compose.ui.graphics.Color(0xFFE5534B) else Mint,shape)) {
                    val cardIndex = e.binding.removePrefix("card").toIntOrNull()
                    val card = cardIndex?.let { state.cards.getOrNull(it) }
                    if (e.kind=="card" && card!=null) CardFace(card,Modifier.fillMaxSize()
                        .then(if (state.selected==card.id) Modifier.border(2.dp,Mint,shape) else Modifier),
                        onClick={ if (editing) onSelectElement(e.id) else onCard(card) },
                        onLongClick={ if (!editing) onPreview(card) },showCounters=false,glow=card.id in state.playable,tutorialZone="hand",cornerRadius=e.r*scale)
                    else if (e.kind!="card" || editing) {
                        val enabled = editing || e.binding in state.enabled
                        val color = e.color?.let(::parseTableColorHex) ?: if (e.strong) Mint else PanelLight
                        Box(Modifier.fillMaxSize().testTag("studio-element-${e.id}").clip(shape).background(color,shape)
                            .clickable(enabled=enabled) { if (editing) onSelectElement(e.id) else onAction(e.binding) }
                            .padding(2.dp),contentAlignment=Alignment.Center) {
                            Text(listOfNotNull(StudioLayout.icons[e.icon]?.takeIf {it.isNotEmpty()},if (editing) e.label else studioLiveLabel(e,state.labels)).joinToString(" "),
                                fontSize=(e.fs*scale).sp, lineHeight=(e.fs*scale*1.2f).sp,
                                color=if (!enabled) Muted else e.textColor?.let(::parseTableColorHex) ?: if(e.strong) Panel else SoftWhite)
                        }
                    }
                    if (editing && selectedElement==e.id && !e.locked) {
                        var resizeDelta by remember(e.id) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                        Box(Modifier.align(Alignment.BottomEnd).size(24.dp).background(Mint)
                            .testTag("studio-resize-${e.id}").pointerInput(e.id,scale) {
                                detectDragGestures(onDragStart={resizeDelta=androidx.compose.ui.geometry.Offset.Zero},
                                    onDrag={c,a->c.consume();resizeDelta+=a},
                                    onDragEnd={resize(e.id,with(density){resizeDelta.x.toDp().value}/scale,with(density){resizeDelta.y.toDp().value}/scale)})
                            })
                    }
                }
            }
        }
    }
    }
}
