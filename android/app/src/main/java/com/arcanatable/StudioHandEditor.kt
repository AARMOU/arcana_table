package com.arcanatable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The hand screen inside the same editor. The canvas is [StudioHandCanvas] — the exact renderer the gameplay hand
 * drawer uses — at the device's real width, with the player's real hand cards. Edits go through
 * [StudioControlsRuntime.changeHand], so hand and control edits share one draft, one undo history and one Apply.
 */
internal object StudioHandEditing {
    val bindingNames=linkedMapOf("pass" to "Pass / primary game action","undo" to "Undo","actions" to "Legal actions list","cast" to "Cast selected card",
        "metric0" to "Stack","metric1" to "Library count","metric2" to "Graveyard","metric3" to "Exile")
    const val OWNER_ASSET="studio/owner-hand.json"

    /** Current hand draft, or a responsive starting point sized for this device and profile. */
    fun layout(runtime:StudioControlsRuntime,widthDp:Float,heightDp:Float):StudioLayout =
        runtime.handDraft() ?: StudioLayout.responsive(widthDp,heightDp,runtime.profile.takeIf{it in listOf("Minimal","Balanced","Detailed")} ?: "Balanced")

    fun edit(runtime:StudioControlsRuntime,layout:StudioLayout,id:String,update:(StudioElement)->StudioElement)=
        runtime.changeHand(layout.copy(elements=layout.elements.map{if(it.id==id)update(it) else it}))

    /** Frame of an element in px inside the canvas, given px-per-design-unit. */
    fun frame(e:StudioElement,layout:StudioLayout,unit:Float)=Rect(e.x*unit,(e.y-layout.originY)*unit,(e.x+e.w)*unit,(e.y-layout.originY+e.h)*unit)

    fun hitTest(layout:StudioLayout,point:Offset,unit:Float):StudioElement? =
        layout.elements.filter{it.visible && frame(it,layout,unit).contains(point)}.minByOrNull{it.w*it.h}
}

@Composable
internal fun StudioHandWorkspace(runtime:StudioControlsRuntime,layout:StudioLayout,cards:List<GameCard>,selected:String?,onSelect:(String?)->Unit,
    intercept:Boolean,snap:Boolean,outlines:Boolean,topPad:androidx.compose.ui.unit.Dp,bottomPad:androidx.compose.ui.unit.Dp,scroll:ScrollState=rememberScrollState()) {
    val currentLayout by rememberUpdatedState(layout)
    val currentSnap by rememberUpdatedState(snap)
    val currentSelected by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    var handle by remember{mutableStateOf<StudioHandle?>(null)}
    var drag by remember{mutableStateOf(Offset.Zero)}
    val reach=with(LocalDensity.current){24.dp.toPx()}
    val accent=darkTableColors.accent
    val measurer=rememberTextMeasurer()
    Box(Modifier.fillMaxSize().background(darkTableColors.background).testTag("studio-hand-workspace")) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Spacer(Modifier.height(topPad))
            Text("Hand · ${runtime.orientation} · real hand drawer renderer",color=darkTableColors.muted,fontSize=11.sp,modifier=Modifier.padding(horizontal=12.dp,vertical=4.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val unit=constraints.maxWidth/layout.canvasWidth.coerceIn(240f,2000f)
                val minPx=24f*unit
                // The canvas copy of the hand must not register its cards as live native controls: they would be
                // validated (and selectable) as if they were on the real screen behind the editor.
                CompositionLocalProvider(LocalStudioControls provides null) {
                    StudioHandCanvas(layout,StudioHandState(emptyMap(),emptySet(),cards),{},{},{},editing=true,selectedElement=null,
                        onSelectElement={onSelect(it)})
                }
                val gestures=if(!intercept)Modifier else Modifier.pointerInput(runtime,unit) {
                    awaitEachGesture {
                        val down=awaitFirstDown(requireUnconsumed=false);down.consume()
                        val l=currentLayout
                        val sel=l.elements.firstOrNull{it.id==currentSelected && it.visible}
                        val grabbed=sel?.takeIf{!it.locked}?.let{StudioEditorGeometry.handleAt(StudioHandEditing.frame(it,l,unit),down.position,reach)}
                        var total=Offset.Zero;var dragging=false;var scrolling=false
                        while(true) {
                            val event=awaitPointerEvent()
                            val change=event.changes.firstOrNull{it.id==down.id} ?: break
                            if(!change.pressed){change.consume();break}
                            val step=change.positionChange();total+=step;change.consume()
                            if(!dragging && !scrolling && StudioEditorGeometry.isMeaningfulDrag(total,viewConfiguration.touchSlop)) {
                                if(grabbed!=null)dragging=true else scrolling=true
                            }
                            if(dragging){handle=grabbed;drag=total}
                            // Dragging empty canvas scrolls it, so long hand designs stay reachable in Select mode.
                            if(scrolling)scroll.dispatchRawDelta(-step.y)
                        }
                        if(dragging && grabbed!=null && sel!=null) {
                            val r=StudioEditorGeometry.commitAbsolute(sel.x,sel.y,sel.w,sel.h,grabbed,total.x/unit,total.y/unit,
                                if(currentSnap)l.snap else 1,24f,l.canvasWidth,l.originY)
                            StudioHandEditing.edit(runtime,l,sel.id){it.copy(x=r[0],y=r[1],w=r[2],h=r[3])}
                        } else if(!dragging && !scrolling) select(StudioHandEditing.hitTest(l,down.position,unit)?.id)
                        handle=null;drag=Offset.Zero
                    }
                }
                Canvas(Modifier.matchParentSize().then(gestures).testTag("studio-hand-canvas-overlay").semantics{contentDescription="Hand canvas"}) {
                    if(outlines)layout.elements.filter{it.visible && it.id!=selected}.forEach {e->
                        val f=StudioHandEditing.frame(e,layout,unit)
                        drawRect(accent.copy(alpha=0.35f),f.topLeft,f.size,style=Stroke(1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(6f,6f))))
                    }
                    layout.elements.firstOrNull{it.id==selected && it.visible}?.let {e->
                        val base=StudioHandEditing.frame(e,layout,unit)
                        val f=handle?.let{StudioEditorGeometry.preview(base,it,drag,minPx)} ?: base
                        if(handle!=null)drawRect(accent.copy(alpha=0.12f),f.topLeft,f.size)
                        drawRect(accent,f.topLeft,f.size,style=Stroke(2.dp.toPx()))
                        if(!e.locked)StudioEditorGeometry.handlePoints(f,StudioEditorGeometry.compact(f,reach)).values.forEach {p->
                            drawCircle(Color.White,7.dp.toPx(),p);drawCircle(accent,7.dp.toPx(),p,style=Stroke(2.dp.toPx()))
                        }
                        val text=measurer.measure("${(f.width/unit).toInt()} × ${(f.height/unit).toInt()}"+if(e.locked)" · locked" else "",
                            TextStyle(color=darkTableColors.background,fontSize=11.sp))
                        val pad=4.dp.toPx()
                        val at=Offset(f.left.coerceIn(0f,(size.width-text.size.width-2*pad).coerceAtLeast(0f)),(f.top-text.size.height-2*pad-10.dp.toPx()).coerceAtLeast(0f))
                        drawRoundRect(accent,at,Size(text.size.width+2*pad,text.size.height+2*pad),CornerRadius(pad))
                        drawText(text,topLeft=at+Offset(pad,pad))
                    }
                }
            }
            Spacer(Modifier.height(bottomPad))
        }
    }
}

@Composable
internal fun StudioHandInspector(runtime:StudioControlsRuntime,layout:StudioLayout,e:StudioElement,collapsed:Boolean,onCollapse:(Boolean)->Unit,
    onSelect:(String?)->Unit,modifier:Modifier=Modifier) {
    val card=e.kind=="card"
    val tabs=StudioInspectorTab.entries
    var tab by remember(e.id){mutableStateOf(StudioInspectorTab.Layout)}
    fun change(update:(StudioElement)->StudioElement)=StudioHandEditing.edit(runtime,layout,e.id,update)
    val palette=layout.colors(runtime.project().colors(darkTableColors)).let {c->listOf("Surface" to c.panel,"Raised" to c.raised,"Accent" to c.accent,
        "Text" to c.text,"Muted" to c.muted,"Gold" to c.counter,"Canvas" to c.background)}
    Surface(modifier.fillMaxWidth().testTag("studio-inspector"),color=darkTableColors.panel,shape=RoundedCornerShape(16.dp),shadowElevation=8.dp) {
        Column(Modifier.padding(horizontal=8.dp,vertical=4.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start=4.dp)) {
                    Text(if(card)"Hand card slot ${(e.binding.removePrefix("card").toIntOrNull() ?: 0)+1}" else e.label.ifBlank{e.id},color=darkTableColors.text,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=15.sp)
                    Text("Hand · ${if(card)"Card" else if(e.kind=="metric")"Live info" else "Button"} · ${e.w.toInt()}×${e.h.toInt()}",color=darkTableColors.muted,fontSize=11.sp,maxLines=1)
                }
                IconButton(onClick={onCollapse(!collapsed)},modifier=Modifier.testTag("studio-inspector-collapse")) {
                    Icon(if(collapsed)Icons.Default.ExpandLess else Icons.Default.ExpandMore,if(collapsed)"Expand inspector" else "Collapse inspector",tint=darkTableColors.text)
                }
                IconButton(onClick={onSelect(null)},modifier=Modifier.testTag("studio-inspector-close")){Icon(Icons.Default.Close,"Deselect",tint=darkTableColors.text)}
            }
            if(collapsed)return@Column
            Row(Modifier.fillMaxWidth()) {tabs.forEach {t->
                val on=t==tab
                Box(Modifier.weight(1f).heightIn(min=44.dp).clip(RoundedCornerShape(10.dp)).background(if(on)darkTableColors.raised else Color.Transparent)
                    .clickable{tab=t}.testTag("studio-tab-${t.name}"),contentAlignment=Alignment.Center) {
                    Text(if(t==StudioInspectorTab.Action && card)"Slot" else t.title,color=if(on)darkTableColors.accent else darkTableColors.muted,fontSize=13.sp)
                }
            }}
            Column(Modifier.heightIn(max=260.dp).testTag("studio-inspector-body").verticalScroll(rememberScrollState()).padding(vertical=4.dp)) {
                when(tab) {
                    StudioInspectorTab.Layout->HandLayoutTab(layout,e,::change)
                    StudioInspectorTab.Style->HandStyleTab(e,palette,::change)
                    StudioInspectorTab.Action->HandActionTab(e,::change)
                    StudioInspectorTab.Arrange->HandArrangeTab(runtime,layout,e,onSelect,::change)
                }
            }
        }
    }
}

@Composable
private fun HandLayoutTab(layout:StudioLayout,e:StudioElement,change:((StudioElement)->StudioElement)->Unit) {
    var step by remember{mutableFloatStateOf(layout.snap.toFloat().coerceAtLeast(1f))}
    fun move(dx:Float,dy:Float)=change{it.copy(x=(it.x+dx).coerceIn(0f,(layout.canvasWidth-it.w).coerceAtLeast(0f)),y=(it.y+dy).coerceAtLeast(layout.originY))}
    val pad=@Composable {
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            StudioPadButton(Icons.Default.KeyboardArrowUp,"Move up","studio-nudge-up"){move(0f,-step)}
            Row {
                StudioPadButton(Icons.Default.KeyboardArrowLeft,"Move left","studio-nudge-left"){move(-step,0f)}
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(darkTableColors.raised)
                    .clickable{step=when(step){1f->4f;4f->layout.snap.toFloat().takeIf{it!=4f && it!=1f} ?: 16f;16f->1f;else->16f}}.testTag("studio-nudge-step"),
                    contentAlignment=Alignment.Center){Text("${step.toInt()}",color=darkTableColors.accent,fontSize=12.sp)}
                StudioPadButton(Icons.Default.KeyboardArrowRight,"Move right","studio-nudge-right"){move(step,0f)}
            }
            StudioPadButton(Icons.Default.KeyboardArrowDown,"Move down","studio-nudge-down"){move(0f,step)}
        }
    }
    val sizes=@Composable {
        StudioStepper("Width","${e.w.toInt()}","studio-width",{change{it.copy(w=(it.w-step).coerceAtLeast(24f))}},{change{it.copy(w=(it.w+step).coerceAtMost(layout.canvasWidth-it.x))}})
        StudioStepper("Height","${e.h.toInt()}","studio-height",{change{it.copy(h=(it.h-step).coerceAtLeast(24f))}},{change{it.copy(h=it.h+step)}})
    }
    val position=@Composable {Text("X ${e.x.toInt()} · Y ${e.y.toInt()}",color=darkTableColors.muted,fontSize=11.sp)}
    BoxWithConstraints {
        if(maxWidth<400.dp)Column {
            Row(verticalAlignment=Alignment.CenterVertically){pad();Spacer(Modifier.width(12.dp));position()}
            sizes()
        } else Row(verticalAlignment=Alignment.CenterVertically) {
            pad();Spacer(Modifier.width(8.dp));Column(Modifier.weight(1f)){position();sizes()}
        }
    }
}

@Composable
private fun HandStyleTab(e:StudioElement,palette:List<Pair<String,Color>>,change:((StudioElement)->StudioElement)->Unit) {
    val card=e.kind=="card"
    if(!card && e.binding!="pass") {
        var text by remember(e.id,e.label){mutableStateOf(e.label)}
        OutlinedTextField(text,{t->text=t.take(80);change{it.copy(label=text)}},singleLine=true,
            label={Text(if(e.kind=="metric")"Label (count stays live)" else "Label")},modifier=Modifier.fillMaxWidth().testTag("studio-label"))
    } else Text(if(card)"Card art and name come from the game." else "Pass always shows the live game action.",color=darkTableColors.muted,fontSize=12.sp)
    if(!card) {
        StudioColorField("Fill",e.color,palette,{v->change{it.copy(color=v)}},"studio-fill")
        StudioColorField("Text",e.textColor,palette,{v->change{it.copy(textColor=v)}},"studio-ink")
        StudioStepper("Text size","${e.fs.toInt()}sp","studio-font",{change{it.copy(fs=(it.fs-1f).coerceIn(8f,32f))}},{change{it.copy(fs=(it.fs+1f).coerceIn(8f,32f))}})
    }
    StudioStepper("Corners","${e.r.toInt()}","studio-radius",{change{it.copy(r=(it.r-2f).coerceIn(0f,64f))}},{change{it.copy(r=(it.r+2f).coerceIn(0f,64f))}})
    if(!card) {
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Emphasis",color=darkTableColors.text,modifier=Modifier.weight(1f));Switch(e.strong,{v->change{it.copy(strong=v)}},modifier=Modifier.testTag("studio-strong"))
        }
        Text("Icon",color=darkTableColors.muted,fontSize=12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState())) {StudioLayout.icons.forEach {(name,glyph)->
            StudioChip(if(glyph.isEmpty())name else "$glyph $name",true,"studio-icon-$name",selected=e.icon==name){change{it.copy(icon=name)}}
        }}
    }
}

@Composable
private fun HandActionTab(e:StudioElement,change:((StudioElement)->StudioElement)->Unit) {
    if(e.kind=="card") {
        Text("Shows card ${(e.binding.removePrefix("card").toIntOrNull() ?: 0)+1} of the player's real hand. Tapping it selects that card; holding previews it.",
            color=darkTableColors.muted,fontSize=12.sp)
        return
    }
    Text("When tapped, this runs the real hand action:",color=darkTableColors.muted,fontSize=12.sp)
    StudioHandEditing.bindingNames.forEach {(id,name)->
        StudioListRow(name,e.binding==id,"studio-hand-binding-$id"){change{it.copy(binding=id)}}
    }
    Text("Every action must stay reachable; Apply refuses a layout that hides one.",color=darkTableColors.muted,fontSize=11.sp)
}

@Composable
private fun HandArrangeTab(runtime:StudioControlsRuntime,layout:StudioLayout,e:StudioElement,onSelect:(String?)->Unit,change:((StudioElement)->StudioElement)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
        Text("Visible",color=darkTableColors.text,modifier=Modifier.weight(1f));Switch(e.visible,{v->change{it.copy(visible=v)}},modifier=Modifier.testTag("studio-visible"))
    }
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
        Text("Lock position",color=darkTableColors.text,modifier=Modifier.weight(1f));Switch(e.locked,{v->change{it.copy(locked=v)}},modifier=Modifier.testTag("studio-lock"))
    }
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        StudioChip(if(e.kind=="card")"Add card slot" else "Duplicate",layout.elements.size<64,"studio-duplicate") {
            val next=layout.duplicateElement(e.id);runtime.changeHand(next);onSelect(next.elements.last().id)
        }
        StudioChip("Remove",e.kind!="card" || layout.elements.count{it.kind=="card"}>1,"studio-remove") {
            runtime.changeHand(layout.removeElement(e.id));onSelect(null)
        }
    }
}

/** Hand-wide settings shown on the options sheet while the hand screen is being edited. */
@Composable
internal fun StudioHandOptions(runtime:StudioControlsRuntime,layout:StudioLayout,widthDp:Float,heightDp:Float,onApplied:()->Unit,onMessage:(String)->Unit,
    confirm:(String,()->Unit)->Unit) {
    val context=LocalContext.current
    val store=StudioStore(runtime.prefs,runtime.orientation,runtime.profile)
    var enabled by remember(runtime.profile,runtime.revision){mutableStateOf(store.enabled())}
    Text("Start from",color=darkTableColors.accent,fontSize=12.sp,modifier=Modifier.padding(top=10.dp))
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        StudioChip("Owner design",true,"studio-hand-owner") {
            runCatching{context.assets.open(StudioHandEditing.OWNER_ASSET).bufferedReader().use{StudioLayout.parse(it.readText())}}
                .onSuccess{runtime.changeHand(it);onMessage("Owner hand design loaded into the draft.")}.onFailure{onMessage(it.message ?: "Could not load the owner design.")}
        }
        StudioChip("Reference",true,"studio-hand-reference"){runtime.changeHand(StudioLayout.reference())}
        listOf("Minimal","Balanced","Detailed").forEach {d->StudioChip(d,true,"studio-hand-$d"){runtime.changeHand(StudioLayout.responsive(widthDp,heightDp,d))}}
    }
    // Moving the hand origin carries every element with it, so the design keeps its shape.
    fun shiftOrigin(by:Float){if(layout.originY+by>=0f)runtime.changeHand(layout.copy(originY=layout.originY+by,elements=layout.elements.map{it.copy(y=it.y+by)}))}
    StudioStepper("Hand top (origin Y)","${layout.originY.toInt()}","studio-hand-origin",{shiftOrigin(-layout.snap.toFloat())},{shiftOrigin(layout.snap.toFloat())})
    StudioStepper("Snap grid","${layout.snap}","studio-hand-snap",{runtime.changeHand(layout.copy(snap=(layout.snap/2).coerceAtLeast(1)))},{runtime.changeHand(layout.copy(snap=(layout.snap*2).coerceAtMost(32)))})
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Use saved hand layout",color=darkTableColors.text)
            Text("Takes effect immediately; off shows the native hand drawer.",color=darkTableColors.muted,fontSize=11.sp)
        }
        Switch(enabled,{v->if(v && store.load()==null)onMessage("Apply a valid hand layout before turning it on.") else {store.enable(v);enabled=v;onApplied()}},
            modifier=Modifier.testTag("studio-hand-enabled"))
    }
    Text("Hand palette (saved with the hand)",color=darkTableColors.accent,fontSize=12.sp,modifier=Modifier.padding(top=10.dp))
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        StudioChip("Inherit table",layout.theme.isNotEmpty(),"studio-hand-palette-inherit"){runtime.changeHand(layout.copy(theme=emptyMap()))}
        tablePalettePresets.forEach {preset->StudioChip(preset.name,true,"studio-hand-palette-${preset.name}"){
            runtime.changeHand(layout.copy(theme=StudioLayout.themeRoles.associateWith{role->tableColorHex(preset.colors.colorRole(if(role=="gold")"counter" else role))}))
        }}
    }
    val base=layout.colors(darkTableColors)
    val swatches=listOf("Surface" to base.panel,"Raised" to base.raised,"Accent" to base.accent,"Text" to base.text,"Gold" to base.counter)
    StudioLayout.themeRoles.forEach {role->
        StudioColorField(role.replaceFirstChar{it.uppercase()},layout.theme[role],swatches,{v->
            runtime.changeHand(layout.copy(theme=if(v==null)layout.theme-role else layout.theme+(role to v)))},"studio-hand-theme-$role")
    }
    StudioChip("Restore native hand",store.load()!=null || store.enabled(),"studio-hand-restore") {
        confirm("Remove the saved hand layout for ${runtime.profile} and return to the native hand drawer? This is saved immediately and closes Studio.") {
            // Clears the saved hand and this session's hand draft; the editor closes so every screen reloads cleanly.
            store.reset();enabled=false;runtime.cancel();onApplied()
        }
    }
}

@Composable
internal fun StudioHandLayersPanel(runtime:StudioControlsRuntime,layout:StudioLayout,selected:String?,onSelect:(String?)->Unit,onDismiss:()->Unit,modifier:Modifier=Modifier) {
    Surface(modifier.fillMaxHeight().widthIn(max=340.dp).fillMaxWidth(0.86f).testTag("studio-layers-panel"),color=darkTableColors.panel,
        shape=RoundedCornerShape(topStart=16.dp,bottomStart=16.dp),shadowElevation=10.dp) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Hand layers · ${layout.elements.size}",color=darkTableColors.text,modifier=Modifier.weight(1f).padding(start=6.dp))
                IconButton(onClick=onDismiss,modifier=Modifier.testTag("studio-layers-close")){Icon(Icons.Default.Close,"Close layers",tint=darkTableColors.text)}
            }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                layout.elements.sortedWith(compareBy({it.y},{it.x})).forEach {e->
                    StudioListRow((if(e.kind=="card")"▭ " else if(e.kind=="metric")"▤ " else "▣ ")+(if(e.kind=="card")"Card slot ${(e.binding.removePrefix("card").toIntOrNull() ?: 0)+1}" else e.label.ifBlank{e.id}),
                        selected==e.id,"studio-layer-row",trailing={
                        IconButton(onClick={StudioHandEditing.edit(runtime,layout,e.id){it.copy(visible=!it.visible)}},modifier=Modifier.size(44.dp).testTag("studio-layer-eye")) {
                            Icon(if(e.visible)Icons.Default.Visibility else Icons.Default.VisibilityOff,if(e.visible)"Hide" else "Show",tint=if(e.visible)darkTableColors.text else darkTableColors.muted)
                        }
                    }){onSelect(e.id)}
                }
            }
        }
    }
}

/** Imports either a full Studio project or a bare hand design (the owner's hand JSON) into the draft, unchanged. */
internal fun studioImport(runtime:StudioControlsRuntime,text:String) {
    val root=org.json.JSONObject(text)
    if(root.optString("screen")=="hand" && root.has("layouts"))runtime.changeHand(StudioLayout.parse(text)) else runtime.importProject(text)
}
