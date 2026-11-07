package com.arcanatable

import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/** Select: touches edit the canvas. Scroll: touches reach the live screen (Studio controls still only select while editing). */
internal enum class StudioEditorTool {Select,Scroll}

/**
 * Arcana Studio editor shell. The real native screen stays visible underneath as the canvas; this draws only a compact
 * toolbar, a selection frame with handles, and a contextual inspector. All state lives in [StudioControlsRuntime], so
 * Apply/Undo/persistence/validation are exactly the runtime's and the gameplay UI renders the same styles it edits.
 */
@Composable
internal fun StudioEditorShell(runtime:StudioControlsRuntime,destinations:Map<String,Pair<Boolean,()->Unit>>,onApplied:()->Unit,
    onClassic:(()->Unit)?,handCards:List<GameCard> = emptyList(),onPractice:(()->Unit)?=null,onLeaveScreen:()->Unit={}) {
    val window=System.identityHashCode(LocalView.current.rootView)
    val density=LocalDensity.current.density
    var tool by StudioEditorRequests::tool
    var layersOpen by remember{mutableStateOf(false)}
    var moreOpen by remember{mutableStateOf(false)}
    var collapsed by remember{mutableStateOf(false)}
    var confirmClose by remember{mutableStateOf(false)}
    // Editor preferences persist between sessions (owner plan item 22).
    val prefs=runtime.prefs
    var snap by remember{mutableStateOf(prefs.getBoolean("studio_editor_snap",true))}
    var outlines by remember{mutableStateOf(prefs.getBoolean("studio_editor_outlines",true))}
    SideEffect{StudioEditorRequests.snap=snap;StudioEditorRequests.outlines=outlines}
    val setSnap:(Boolean)->Unit={snap=it;prefs.edit().putBoolean("studio_editor_snap",it).apply()}
    val setOutlines:(Boolean)->Unit={outlines=it;prefs.edit().putBoolean("studio_editor_outlines",it).apply()}
    var message by remember{mutableStateOf<String?>(null)}
    var origin by remember{mutableStateOf(Offset.Zero)}
    var height by remember{mutableFloatStateOf(0f)}
    var barBottom by remember{mutableFloatStateOf(0f)}
    // The screen being edited is the tab the player picked (not guessed from what happens to be behind the editor).
    val tabs=destinations.keys.toList().let{k->k.take(2)+"Hand"+k.drop(2)}
    var tab by remember{mutableStateOf("Table")}  // outside a game, Table shows the practice card
    val handMode=tab=="Hand"
    var handSelected by remember{mutableStateOf<String?>(null)}
    val configuration=androidx.compose.ui.platform.LocalConfiguration.current
    val handLayout=if(handMode)StudioHandEditing.layout(runtime,configuration.screenWidthDp.toFloat(),configuration.screenHeightDp.toFloat()) else null
    val handElement=handLayout?.elements?.firstOrNull{it.id==handSelected}
    val popups=StudioEditorRequests.popups
    val popupScreens=popups.map{it.screen}.toSet().takeIf{it.isNotEmpty()}
    val noGame=destinations.values.none{it.first}
    val selected=if(handMode)null else runtime.selected?.let{runtime.controls[it]}
    val screen=if(handMode)"Hand" else popupScreens?.let{p->selected?.screen?.takeIf{it in p} ?: popups.last().screen} ?: selected?.screen ?: tab
    val foreign=!handMode && runtime.controls.values.any{it.window!=0 && it.window!=window && it.bounds.width>0f}
    val apply:()->Unit={runCatching{runtime.apply()}.onSuccess{onApplied()}.onFailure{message=it.message ?: "Fix the listed problems before applying."}}
    val close:()->Unit={if(runtime.canUndo || runtime.hasDraft())confirmClose=true else runtime.cancel()}
    val pick:(String)->Unit={name->
        if(name=="Hand"){onLeaveScreen();tab="Hand";runtime.selected=null;handSelected=null}
        else destinations[name]?.let{(available,open)->
            if(available){tab=name;handSelected=null;runtime.selected=null;open()}
            else message=if(name=="Combat")"Combat can be edited during combat in a practice game." else "Start a practice game to edit $name."
        }
    }
    // Back closes the open pop-up the way the game would; with none open, it offers to leave the editor.
    androidx.activity.compose.BackHandler(enabled=true) {if(popups.isNotEmpty())popups.last().dismiss() else close()}

    val bar=@Composable {
        // Owner note 5: the Select/Scroll switch lives in the toolbar, never floating over cards or buttons.
        StudioTopBar(runtime,screen,close,{},{layersOpen=!layersOpen},layersOpen,{moreOpen=true},apply,
            scrollMode=if(noGame)null else tool==StudioEditorTool.Scroll,
            onToggleScroll={tool=if(tool==StudioEditorTool.Scroll)StudioEditorTool.Select else StudioEditorTool.Scroll})
        StudioScreenBar(tabs,tab,destinations,pick,popups.lastOrNull()?.let{p->({p.dismiss()})},if(handMode)null else tab)
        // Owner note 14: no standing warning bar. Problems are red marking on the items themselves; this only shows a
        // message the owner just triggered (e.g. why Apply couldn't save a lock-out), and a tap dismisses it.
        val notice=message
        if(notice!=null)Surface(color=darkTableColors.raised,shape=RoundedCornerShape(12.dp),modifier=Modifier.padding(6.dp).fillMaxWidth()
            .clickable{message=null}.testTag("studio-notice")) {
            Text(notice,color=darkTableColors.counter,fontSize=12.sp,modifier=Modifier.padding(10.dp))
        }
    }
    val inspector:@Composable (Modifier)->Unit={mod->
        if(handLayout!=null)handElement?.let {StudioHandInspector(runtime,handLayout,it,collapsed,{v->collapsed=v},{id->handSelected=id},mod)}
        else selected?.let {StudioInspector(runtime,it,density,collapsed,{v->collapsed=v},{m->message=m},mod)}
    }
    val sheets=@Composable {
        Box(Modifier.fillMaxSize()) {
            val layersModifier=Modifier.align(Alignment.CenterEnd).statusBarsPadding().padding(top=56.dp)
            if(layersOpen)if(handLayout!=null)StudioHandLayersPanel(runtime,handLayout,handSelected,{handSelected=it},{layersOpen=false},layersModifier)
                else StudioLayersPanel(runtime,if(foreign)0 else window,{layersOpen=false},layersModifier,popupScreens)
            if(moreOpen)StudioMorePanel(runtime,screen,snap,setSnap,outlines,setOutlines,onApplied,onClassic,
                if(handMode)null else ({moreOpen=false;pick("Hand")}),{message=it},{moreOpen=false},
                hand=handLayout?.let{l->{confirm->StudioHandOptions(runtime,l,configuration.screenWidthDp.toFloat(),configuration.screenHeightDp.toFloat(),onApplied,{message=it},confirm)}})
        }
    }
    SideEffect{StudioEditorRequests.mainWindow=window}
    DisposableEffect(runtime){StudioEditorRequests.clear();StudioEditorRequests.shellActive=true;StudioEditorRequests.tool=StudioEditorTool.Select
        onDispose{StudioEditorRequests.clear();StudioEditorRequests.shellActive=false}}
    CompositionLocalProvider(LocalTableColors provides darkTableColors) {
    MaterialTheme(colorScheme=darkColorScheme(primary=darkTableColors.accent,surface=darkTableColors.panel,onSurface=darkTableColors.text,background=darkTableColors.background)) {
        Box(Modifier.fillMaxSize().onGloballyPositioned{origin=it.boundsInRoot().topLeft;height=it.size.height.toFloat()}.testTag("studio-editor")) {
            // Game pop-ups drawn in place, in the game's own colours, under the editing canvas.
            if(!handMode)CompositionLocalProvider(LocalTableColors provides (runtime.project().colors(LocalStudioBaseColors.current ?: darkTableColors))) {
                // Pop-ups sit below the toolbar and screen bar so their top row and "‹ Back" never overlap.
                val top=with(LocalDensity.current){(barBottom-origin.y).coerceAtLeast(0f).toDp()}
                Box(Modifier.fillMaxSize().padding(top=top)) {popups.forEach{p->key(p.key){p.content()}}}
            }
            when {
                handLayout!=null->StudioHandWorkspace(runtime,handLayout,handCards,handSelected,{handSelected=it},tool==StudioEditorTool.Select,snap,outlines,
                    bottomPad=if(handElement!=null && !collapsed)360.dp else 96.dp)
                noGame->StudioPracticeCard(onPractice,{pick("Hand")},Modifier.align(Alignment.Center))
                else->StudioSelectionCanvas(runtime,window,origin,tool==StudioEditorTool.Select,snap,outlines,density,popupScreens)
            }
            Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().onGloballyPositioned{barBottom=it.boundsInRoot().bottom}) {bar()}
            // Dock the inspector away from the selection so the element being edited is never hidden.
            val dockTop=!handMode && selected!=null && selected.window==window && (selected.bounds.center.y-origin.y)>height*0.55f
            Column(Modifier.align(if(dockTop)Alignment.TopCenter else Alignment.BottomCenter).fillMaxWidth()
                .then(if(dockTop)Modifier.statusBarsPadding().padding(top=112.dp) else Modifier.navigationBarsPadding()).padding(horizontal=6.dp,vertical=4.dp)) {
                if(!foreign || selected?.window==window)inspector(Modifier.widthIn(max=640.dp).align(Alignment.CenterHorizontally))
            }
            if(!foreign)sheets()
        }
        // Windows Studio can't draw in place yet (e.g. bottom sheets) keep a floating toolbar and inspector above them.
        if(foreign) {
            StudioFloatingWindow(top=true,tag="studio-floating-bar") {bar()}
            if(selected!=null && selected.window!=window && !layersOpen && !moreOpen)
                StudioFloatingWindow(top=false,tag="studio-floating-inspector") {inspector(Modifier.padding(6.dp))}
            if(layersOpen || moreOpen)StudioOverlayWindow {sheets()}
        }
        if(confirmClose)AlertDialog(onDismissRequest={confirmClose=false},title={Text("Leave Studio?")},
            text={Column {
                Text("Your draft is kept and reopens next time. Nothing changes in the game until you Apply.")
                TextButton(onClick={confirmClose=false;runtime.discard()},modifier=Modifier.testTag("studio-close-discard")){
                    Text("Discard draft",color=darkTableColors.muted)}
            }},
            confirmButton={TextButton(onClick={confirmClose=false;runtime.cancel()},modifier=Modifier.testTag("studio-close-confirm")){Text("Leave")}},
            dismissButton={TextButton(onClick={confirmClose=false;apply()},modifier=Modifier.testTag("studio-close-apply")){Text("Apply and leave")}})
    }
    }
}

/** Always-visible screen tabs (owner: make choosing hand/table/zones prominent), plus "‹ Back" while a pop-up is open. */
@Composable
internal fun StudioScreenBar(tabs:List<String>,current:String,destinations:Map<String,Pair<Boolean,()->Unit>>,onPick:(String)->Unit,
    onBack:(()->Unit)?,backTo:String?) {
    Surface(color=darkTableColors.panel.copy(alpha=0.97f),shape=RoundedCornerShape(14.dp),modifier=Modifier.padding(horizontal=6.dp,vertical=4.dp).fillMaxWidth().testTag("studio-screen-bar")) {
        // "‹ Back" stays pinned at the start; only the screen tabs scroll, so Back is never scrolled out of view.
        Row(Modifier.padding(4.dp),verticalAlignment=Alignment.CenterVertically) {
            if(onBack!=null) {
                Row(Modifier.heightIn(min=44.dp).clip(RoundedCornerShape(12.dp)).background(darkTableColors.counter.copy(alpha=0.22f))
                    .clickable(onClick=onBack).padding(horizontal=12.dp).testTag("studio-popup-back"),verticalAlignment=Alignment.CenterVertically) {
                    Text("‹ Back to ${backTo ?: "screen"}",color=darkTableColors.counter,fontSize=13.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)
                }
                Spacer(Modifier.width(6.dp))
            }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
            tabs.forEach {name->
                val on=name==current
                val available=name=="Hand" || destinations[name]?.first==true
                Column(Modifier.widthIn(min=56.dp).heightIn(min=48.dp).clip(RoundedCornerShape(12.dp))
                    .background(if(on)darkTableColors.accent else Color.Transparent).clickable{onPick(name)}.padding(horizontal=10.dp,vertical=4.dp)
                    .semantics{contentDescription=if(available)"Edit $name" else "$name: start a practice game first"}.testTag("studio-tab-screen-$name"),
                    horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    Icon(studioScreenIcon(name),null,tint=if(on)darkTableColors.background else if(available)darkTableColors.text else darkTableColors.muted.copy(alpha=0.6f),modifier=Modifier.size(20.dp))
                    Text(name,fontSize=11.sp,color=if(on)darkTableColors.background else if(available)darkTableColors.text else darkTableColors.muted.copy(alpha=0.6f),maxLines=1)
                }
            }
            }
        }
    }
}

private fun studioScreenIcon(name:String)=when(name){"Table"->Icons.Default.TableRestaurant;"Player"->Icons.Default.Person;"Hand"->Icons.Default.ViewCarousel
    "Stack"->Icons.Default.Layers;"Zones"->Icons.Default.Inventory2;"Log"->Icons.AutoMirrored.Filled.List;"Combat"->Icons.Default.Bolt;else->Icons.Default.Dashboard}

/** Studio edits game screens; outside a game it offers a paused offline practice game (or the hand design). */
@Composable
private fun StudioPracticeCard(onPractice:(()->Unit)?,onHand:()->Unit,modifier:Modifier) {
    Surface(modifier.padding(24.dp).widthIn(max=420.dp).testTag("studio-practice-card"),color=darkTableColors.panel,shape=RoundedCornerShape(20.dp),shadowElevation=8.dp) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Edit your game screens",color=darkTableColors.text,fontSize=18.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text("Studio changes the table, player, stack, zones and log screens. Start an offline practice game against the AI to see and edit them. Your layout then applies to every game.",
                color=darkTableColors.muted,fontSize=13.sp)
            if(onPractice!=null)Button(onClick=onPractice,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("studio-start-practice"),
                colors=ButtonDefaults.buttonColors(containerColor=darkTableColors.accent,contentColor=darkTableColors.background)){Text("Start practice game")}
            OutlinedButton(onClick=onHand,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("studio-practice-hand")){Text("Edit the hand",color=darkTableColors.text)}
        }
    }
}

/** The screen being edited: the selection's screen, else the most common screen among visible controls in this window. */
internal fun studioCurrentScreen(runtime:StudioControlsRuntime,window:Int,selected:StudioLiveControl?):String =
    selected?.screen ?: runtime.controls.values.filter{it.window==window && it.bounds.width>0f}.groupingBy{it.screen}.eachCount()
        .maxByOrNull{it.value}?.key ?: "Table"

@Composable
private fun StudioToolSwitch(tool:StudioEditorTool,onTool:(StudioEditorTool)->Unit) {
    val narrow=androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp<380
    Row(Modifier.padding(bottom=4.dp).clip(RoundedCornerShape(22.dp)).background(darkTableColors.panel.copy(alpha=0.94f))) {
        listOf(StudioEditorTool.Select to Icons.Default.TouchApp,StudioEditorTool.Scroll to Icons.Default.PanTool).forEach {(t,icon)->
            val on=t==tool
            Row(Modifier.heightIn(min=44.dp).clip(RoundedCornerShape(22.dp)).background(if(on)darkTableColors.accent.copy(alpha=0.25f) else Color.Transparent)
                .clickable{onTool(t)}.padding(horizontal=14.dp).testTag("studio-tool-${t.name}"),verticalAlignment=Alignment.CenterVertically) {
                Icon(icon,if(narrow)"${t.name} mode" else null,tint=if(on)darkTableColors.accent else darkTableColors.muted,modifier=Modifier.size(18.dp))
                if(!narrow)Text(" ${t.name}",color=if(on)darkTableColors.accent else darkTableColors.muted,fontSize=13.sp)
            }
        }
    }
}

/**
 * Transparent canvas over the live screen. In Select mode it owns touches: tap selects the deepest control, dragging the
 * selection moves it and the eight handles resize it. One gesture commits one undo step.
 */
@Composable
internal fun StudioSelectionCanvas(runtime:StudioControlsRuntime,window:Int,origin:Offset,intercept:Boolean,snap:Boolean,outlines:Boolean,density:Float,
    allowedScreens:Set<String>?=null) {
    // While a pop-up is open only its controls can be picked or outlined; the screen behind it is dimmed.
    val pickable:(StudioLiveControl)->Boolean={c->allowedScreens==null || c.screen in allowedScreens}
    val currentPickable by rememberUpdatedState(pickable)
    var handle by remember{mutableStateOf<StudioHandle?>(null)}
    var drag by remember{mutableStateOf(Offset.Zero)}
    val currentOrigin by rememberUpdatedState(origin)
    val currentSnap by rememberUpdatedState(snap)
    val reach=with(LocalDensity.current){24.dp.toPx()}
    val minPx=StudioEditorGeometry.MIN_DP*density
    val gestures=if(!intercept)Modifier else Modifier.pointerInput(runtime,window) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false)
            down.consume()
            val point=down.position+currentOrigin
            val selection=runtime.selected?.let{runtime.controls[it]}?.takeIf{it.window==window && it.bounds.width>0f}
            val grabbed=selection?.let{StudioEditorGeometry.handleAt(it.bounds,point,reach)}
            var total=Offset.Zero;var dragging=false
            while(true) {
                val event=awaitPointerEvent()
                val change=event.changes.firstOrNull{it.id==down.id} ?: break
                if(!change.pressed){change.consume();break}
                total+=change.positionChange();change.consume()
                if(!dragging && grabbed!=null && StudioEditorGeometry.isMeaningfulDrag(total,viewConfiguration.touchSlop))dragging=true
                if(dragging){handle=grabbed;drag=total}
            }
            if(dragging && grabbed!=null && selection!=null) {
                val style=runtime.style(selection.id)
                runtime.edit(selection.id,StudioEditorGeometry.commit(style,grabbed,selection.bounds.width/density,selection.bounds.height/density,
                    Offset(total.x/density,total.y/density),currentSnap))
            } else if(!StudioEditorGeometry.isMeaningfulDrag(total,viewConfiguration.touchSlop)) {
                runtime.selected=StudioEditorGeometry.hitTest(runtime.controls.values.filter(currentPickable),point,window){runtime.style(it).visible}?.id
            }
            handle=null;drag=Offset.Zero
        }
    }
    val selection=runtime.selected?.let{runtime.controls[it]}?.takeIf{it.window==window && it.bounds.width>0f}
    val accent=darkTableColors.accent
    val measurer=rememberTextMeasurer()
    val ink=darkTableColors.background
    Canvas(Modifier.fillMaxSize().then(gestures).testTag("studio-canvas").semantics{contentDescription="Studio canvas"}) {
        // Outline only what can actually be picked: table controls hidden behind a full-screen page are skipped.
        val covered=outlines && runtime.controls.values.any{it.window==window && it.bounds.width>0f && it.screen in StudioEditorGeometry.FULL_SCREENS}
        if(outlines)runtime.controls.values.filter(pickable).forEach {c->
            if(c.window==window && c.bounds.width>0f && c.id!=selection?.id && !(covered && c.screen==StudioEditorGeometry.BASE_SCREEN)) {
                val r=c.bounds.translate(-origin)
                drawRect(accent.copy(alpha=if(c.isAction)0.35f else 0.18f),r.topLeft,r.size,
                    style=Stroke(1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(6f,6f))))
            }
        }
        // Owner notes 14 + 31: anything with advice is outlined red with a small warning flag (not colour alone), and
        // turns red live while a drag would put it in a problem spot. Nothing blocks; tapping it shows why.
        val advice=runtime.advice()
        val red=Color(0xFFE5534B)
        runtime.controls.values.filter{it.id in advice && it.window==window && it.bounds.width>0f && pickable(it)}.forEach {c->
            val r=c.bounds.translate(-origin)
            drawRect(red,r.topLeft,r.size,style=Stroke(2.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(10f,5f))))
            val f=8.dp.toPx();val p=androidx.compose.ui.graphics.Path().apply{moveTo(r.right-f,r.top);lineTo(r.right,r.top);lineTo(r.right,r.top+f);close()}
            drawPath(p,red)
        }
        selection?.let {s->
            val base=s.bounds.translate(-origin)
            val frame=handle?.let{StudioEditorGeometry.preview(base,it,drag,StudioEditorGeometry.FLOOR_DP*density)} ?: base
            val trouble=handle!=null && StudioEditorGeometry.dragTrouble(frame,s,runtime.controls.values.filter{
                it.id!=s.id && it.window==window && it.isAction && it.bounds.width>0f && it.screen==s.screen && runtime.style(it.id).visible}.map{it.bounds.translate(-origin)},
                Size(size.width,size.height),minPx)
            val edge=if(trouble || (handle==null && s.id in advice))red else accent
            if(handle!=null)drawRect(edge.copy(alpha=0.12f),frame.topLeft,frame.size)
            drawRect(edge,frame.topLeft,frame.size,style=Stroke(2.dp.toPx()))
            StudioEditorGeometry.handlePoints(frame,StudioEditorGeometry.compact(frame,reach)).values.forEach {p->
                drawCircle(Color.White,7.dp.toPx(),p);drawCircle(accent,7.dp.toPx(),p,style=Stroke(2.dp.toPx()))
            }
            // Size readout rides on the frame, drawn (not laid out) so it can never feed back into layout.
            val text=measurer.measure("${(frame.width/density).toInt()} × ${(frame.height/density).toInt()}",TextStyle(color=ink,fontSize=11.sp))
            val pad=4.dp.toPx()
            val at=Offset(frame.left.coerceIn(0f,(size.width-text.size.width-2*pad).coerceAtLeast(0f)),
                (frame.top-text.size.height-2*pad-10.dp.toPx()).coerceAtLeast(0f))
            drawRoundRect(accent,at,Size(text.size.width+2*pad,text.size.height+2*pad),CornerRadius(pad))
            drawText(text,topLeft=at+Offset(pad,pad))
        }
    }
}

/**
 * Direct manipulation inside a game dialog window: a non-focusable popup pinned to the dialog window's origin carries
 * the same selection canvas (tap/drag/handles), so dialogs are edited exactly like full screens. Shown only in Select
 * mode; in Scroll mode the dialog scrolls normally and its Studio controls still only select.
 */
@Composable
internal fun StudioWindowCanvasHost(runtime:StudioControlsRuntime,window:Int) {
    if(!runtime.editing || !StudioEditorRequests.shellActive || window==StudioEditorRequests.mainWindow)return
    val token=remember{Any()}
    DisposableEffect(window,token){onDispose{if(StudioEditorRequests.canvasOwners[window]===token)StudioEditorRequests.canvasOwners.remove(window)}}
    SideEffect{if(StudioEditorRequests.canvasOwners[window]==null)StudioEditorRequests.canvasOwners[window]=token}
    if(StudioEditorRequests.canvasOwners[window]===token)StudioWindowCanvas(runtime)
}

@Composable
internal fun StudioWindowCanvas(runtime:StudioControlsRuntime) {
    if(!runtime.editing || !StudioEditorRequests.shellActive || StudioEditorRequests.tool!=StudioEditorTool.Select)return
    val window=System.identityHashCode(LocalView.current.rootView)
    val density=LocalDensity.current.density
    androidx.compose.ui.window.Popup(popupPositionProvider=object:androidx.compose.ui.window.PopupPositionProvider {
        override fun calculatePosition(anchorBounds:androidx.compose.ui.unit.IntRect,windowSize:androidx.compose.ui.unit.IntSize,
            layoutDirection:androidx.compose.ui.unit.LayoutDirection,popupContentSize:androidx.compose.ui.unit.IntSize)=androidx.compose.ui.unit.IntOffset.Zero
    },properties=androidx.compose.ui.window.PopupProperties(focusable=false,clippingEnabled=false)) {
        CompositionLocalProvider(LocalTableColors provides darkTableColors) {
            Box(Modifier.fillMaxSize().testTag("studio-window-canvas")) {
                StudioSelectionCanvas(runtime,window,Offset.Zero,true,StudioEditorRequests.snap,StudioEditorRequests.outlines,density)
            }
        }
    }
}

/** Full-screen, undimmed window for Studio sheets when a game dialog is open (sheets must sit above it). */
@Composable
internal fun StudioOverlayWindow(content:@Composable ()->Unit) {
    Dialog(onDismissRequest={},properties=DialogProperties(dismissOnBackPress=false,dismissOnClickOutside=false,usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val window=(LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }}
        Box(Modifier.fillMaxSize().testTag("studio-overlay-window")) {content()}
    }
}

/** A small, non-modal, undimmed window pinned to the top or bottom edge, stacked above any open game dialog. */
@Composable
internal fun StudioFloatingWindow(top:Boolean,tag:String,content:@Composable ()->Unit) {
    Dialog(onDismissRequest={},properties=DialogProperties(dismissOnBackPress=false,dismissOnClickOutside=false,usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val window=(LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {window?.apply {
            setGravity(if(top)Gravity.TOP else Gravity.BOTTOM)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.WRAP_CONTENT)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        }}
        Column(Modifier.fillMaxWidth().then(if(top)Modifier.statusBarsPadding() else Modifier.navigationBarsPadding()).testTag(tag)) {content()}
    }
}
