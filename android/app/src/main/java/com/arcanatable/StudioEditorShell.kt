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
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.TouchApp
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
    onClassic:()->Unit,onHand:(()->Unit)?) {
    val window=System.identityHashCode(LocalView.current.rootView)
    val density=LocalDensity.current.density
    var tool by remember{mutableStateOf(StudioEditorTool.Select)}
    var layersOpen by remember{mutableStateOf(false)}
    var moreOpen by remember{mutableStateOf(false)}
    var screensOpen by remember{mutableStateOf(false)}
    var collapsed by remember{mutableStateOf(false)}
    var confirmClose by remember{mutableStateOf(false)}
    var snap by remember{mutableStateOf(true)}
    var outlines by remember{mutableStateOf(true)}
    var message by remember{mutableStateOf<String?>(null)}
    var origin by remember{mutableStateOf(Offset.Zero)}
    var height by remember{mutableFloatStateOf(0f)}
    val selected=runtime.selected?.let{runtime.controls[it]}
    val screen=studioCurrentScreen(runtime,window,selected)
    val foreign=runtime.controls.values.any{it.window!=0 && it.window!=window && it.bounds.width>0f}
    val apply:()->Unit={runCatching{runtime.apply()}.onSuccess{onApplied()}.onFailure{message=it.message ?: "Fix the listed problems before applying."}}
    val close:()->Unit={if(runtime.canUndo)confirmClose=true else runtime.cancel()}
    val problems=runtime.problems().distinct()

    val bar=@Composable {
        StudioTopBar(runtime,screen,close,{screensOpen=true},{layersOpen=!layersOpen},layersOpen,{moreOpen=true},apply)
        val notice=message ?: problems.firstOrNull()
        if(notice!=null)Surface(color=darkTableColors.raised,shape=RoundedCornerShape(12.dp),modifier=Modifier.padding(6.dp).fillMaxWidth()
            .clickable{message=null}.testTag("studio-notice")) {
            Text(notice+(if(message==null && problems.size>1)"  (+${problems.size-1} more)" else ""),color=darkTableColors.counter,fontSize=12.sp,modifier=Modifier.padding(10.dp))
        }
    }
    val inspector:@Composable (Modifier)->Unit={mod->
        selected?.let {StudioInspector(runtime,it,density,collapsed,{v->collapsed=v},{m->message=m},mod)}
    }
    val sheets=@Composable {
        Box(Modifier.fillMaxSize()) {
            if(layersOpen)StudioLayersPanel(runtime,if(foreign)0 else window,{layersOpen=false},Modifier.align(Alignment.CenterEnd).statusBarsPadding().padding(top=56.dp))
            if(screensOpen)StudioScreenMenu(destinations,screen,{name->screensOpen=false;runtime.selected=null;destinations[name]?.second?.invoke()},
                onHand?.let{h->({screensOpen=false;h()})},{screensOpen=false})
            if(moreOpen)StudioMorePanel(runtime,screen,snap,{snap=it},outlines,{outlines=it},onApplied,onClassic,onHand,{message=it},{moreOpen=false})
        }
    }
    // Requests from inside game dialogs (their Studio button, or a back/outside dismissal held while editing).
    val firstMenuRequest=remember{StudioEditorRequests.menuRequests}
    LaunchedEffect(StudioEditorRequests.menuRequests){if(StudioEditorRequests.menuRequests!=firstMenuRequest)screensOpen=true}
    DisposableEffect(runtime){StudioEditorRequests.clear();onDispose{StudioEditorRequests.clear()}}
    CompositionLocalProvider(LocalTableColors provides darkTableColors) {
    MaterialTheme(colorScheme=darkColorScheme(primary=darkTableColors.accent,surface=darkTableColors.panel,onSurface=darkTableColors.text,background=darkTableColors.background)) {
        Box(Modifier.fillMaxSize().onGloballyPositioned{origin=it.boundsInRoot().topLeft;height=it.size.height.toFloat()}.testTag("studio-editor")) {
            StudioSelectionCanvas(runtime,window,origin,tool==StudioEditorTool.Select,snap,outlines,density)
            Column(Modifier.align(Alignment.TopCenter).statusBarsPadding()) {bar()}
            // Dock the inspector away from the selection so the element being edited is never hidden.
            val dockTop=selected!=null && selected.window==window && (selected.bounds.center.y-origin.y)>height*0.55f
            Column(Modifier.align(if(dockTop)Alignment.TopCenter else Alignment.BottomCenter).fillMaxWidth()
                .then(if(dockTop)Modifier.statusBarsPadding().padding(top=60.dp) else Modifier.navigationBarsPadding()).padding(horizontal=6.dp,vertical=4.dp)) {
                if(!dockTop)StudioToolSwitch(tool){tool=it}
                if(!foreign || selected?.window==window)inspector(Modifier.widthIn(max=640.dp).align(Alignment.CenterHorizontally))
                if(dockTop)StudioToolSwitch(tool){tool=it}
            }
            if(!foreign)sheets()
        }
        // A game dialog is its own window above this one. Mirror the toolbar and inspector into small, non-modal windows
        // above it, so dialogs are edited in place instead of through a separate form.
        if(foreign) {
            StudioFloatingWindow(top=true,tag="studio-floating-bar") {bar()}
            if(selected!=null && selected.window!=window && !layersOpen && !screensOpen && !moreOpen)
                StudioFloatingWindow(top=false,tag="studio-floating-inspector") {inspector(Modifier.padding(6.dp))}
            if(layersOpen || screensOpen || moreOpen)StudioOverlayWindow {sheets()}
        }
        StudioEditorRequests.pendingDismiss?.let {
            AlertDialog(onDismissRequest={StudioEditorRequests.clear()},title={Text("Close this window?")},
                text={Text("Studio held a back/outside tap so a decision isn't cancelled by accident. Close the window as the game normally would?")},
                confirmButton={TextButton(onClick={StudioEditorRequests.consumeDismiss()?.invoke()},modifier=Modifier.testTag("studio-dismiss-window")){Text("Close window")}},
                dismissButton={TextButton(onClick={StudioEditorRequests.clear()},modifier=Modifier.testTag("studio-keep-window")){Text("Keep editing")}})
        }
        if(confirmClose)AlertDialog(onDismissRequest={confirmClose=false},title={Text("Leave Studio?")},
            text={Text("Your draft is kept and reopens next time. Nothing changes in the game until you Apply.")},
            confirmButton={TextButton(onClick={confirmClose=false;runtime.cancel()},modifier=Modifier.testTag("studio-close-confirm")){Text("Leave")}},
            dismissButton={TextButton(onClick={confirmClose=false;apply()},modifier=Modifier.testTag("studio-close-apply")){Text("Apply and leave")}})
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
internal fun StudioSelectionCanvas(runtime:StudioControlsRuntime,window:Int,origin:Offset,intercept:Boolean,snap:Boolean,outlines:Boolean,density:Float) {
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
                runtime.selected=StudioEditorGeometry.hitTest(runtime.controls.values,point,window){runtime.style(it).visible}?.id
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
        if(outlines)runtime.controls.values.forEach {c->
            if(c.window==window && c.bounds.width>0f && c.id!=selection?.id && !(covered && c.screen==StudioEditorGeometry.BASE_SCREEN)) {
                val r=c.bounds.translate(-origin)
                drawRect(accent.copy(alpha=if(c.isAction)0.35f else 0.18f),r.topLeft,r.size,
                    style=Stroke(1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(6f,6f))))
            }
        }
        selection?.let {s->
            val base=s.bounds.translate(-origin)
            val frame=handle?.let{StudioEditorGeometry.preview(base,it,drag,minPx)} ?: base
            if(handle!=null)drawRect(accent.copy(alpha=0.12f),frame.topLeft,frame.size)
            drawRect(accent,frame.topLeft,frame.size,style=Stroke(2.dp.toPx()))
            StudioEditorGeometry.handlePoints(frame).values.forEach {p->
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
