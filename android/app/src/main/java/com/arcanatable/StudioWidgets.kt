package com.arcanatable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import androidx.compose.foundation.text.InlineTextContent

internal data class StudioWidget(val runtime:StudioControlsRuntime,val id:String,val style:StudioControlStyle,
    val enabled:Boolean,val title:MutableState<String>,val titleSource:MutableState<String?>) {
    fun click(){if(runtime.editing)runtime.select(id) else if(enabled)runtime.invokeFrom(id,style.binding ?: id)}
    fun parent(proxy:Boolean=false)=StudioControlParent(id,style,{source,text->
        if(titleSource.value==null)titleSource.value=source
        if(titleSource.value==source)title.value=text
    },proxy,if(style.binding!=null)runtime.controls[style.binding]?.title?.let{native->style.label?.let{"$it · $native"} ?: native} ?: "Unavailable action" else null)
}

@Composable
internal fun studioWidget(site:String,screen:String,kind:String,title:String,enabled:Boolean=true,protectedLabel:Boolean=false,
    onClick:(()->Unit)?=null):StudioWidget? {
    val runtime=LocalStudioControls.current ?: return null
    if(!runtime.active)return null
    if(kind.endsWith("action") && LocalStudioControlParent.current?.proxy==true)return null
    val base="native:$site/${currentCompositeKeyHash}"
    val token=remember{Any()}
    val id=runtime.claim(base,token)
    val caption=remember(id){mutableStateOf(title)}
    val source=remember(id){mutableStateOf<String?>(null)}
    val currentCaption=if(title in listOf("Action","Icon action","Menu action","Text input","Filter","Action chip","Control","Tab"))caption.value else title
    val captionEditable=kind!="container-action" || source.value!=null
    val action by rememberUpdatedState(onClick)
    val window=System.identityHashCode(LocalView.current.rootView)
    DisposableEffect(runtime,id){onDispose{runtime.remove(id);runtime.release(base,token)}}
    val actualScreen=LocalStudioScreen.current ?: screen
    SideEffect {runtime.record(StudioLiveControl(id,actualScreen,kind,currentCaption,enabled,window=window,protectedLabel=protectedLabel,captionEditable=captionEditable),
        if(onClick==null)null else ({action?.invoke()}))}
    val style=runtime.style(id,actualScreen,kind)
    val available=enabled && (style.binding==null || if(kind.endsWith("action"))runtime.canInvoke(id,style.binding) else runtime.canDisplay(id,style.binding))
    return StudioWidget(runtime,id,style,available,caption,source)
}

@Composable
internal fun Modifier.studioWidget(widget:StudioWidget?):Modifier {
    if(widget==null)return this
    val s=widget.style
    val density=LocalDensity.current
    val root=LocalView.current.rootView
    var drag by remember(widget.id){mutableStateOf(Offset.Zero)}
    var resizing by remember(widget.id){mutableStateOf(false)}
    val current by rememberUpdatedState(widget)
    if(!s.visible)return this.layout {measurable,constraints->
        measurable.measure(constraints)
        layout(0,0){} // Compose captions and guards, but never place, draw or hit-test hidden controls.
    }
    // Native anchors and parent-data (weights) remain intact. Draft and applied use this same modifier.
    var result=this.offset(s.dx.takeIf{it.isFinite()}?.coerceIn(-2000f,2000f)?.dp ?: 0.dp,
        s.dy.takeIf{it.isFinite()}?.coerceIn(-4000f,4000f)?.dp ?: 0.dp)
    s.width?.takeIf{it.isFinite()}?.let{result=result.requiredWidth(it.coerceIn(1f,2000f).dp)}
    s.height?.takeIf{it.isFinite()}?.let{result=result.requiredHeight(it.coerceIn(1f,4000f).dp)}
    s.radius?.takeIf{it.isFinite()}?.let{result=result.clip(RoundedCornerShape(it.coerceIn(0f,100f).dp))}
    s.fill?.let(::parseTableColorHex)?.let {result=result.background(it)}
    if(widget.runtime.editing) result=result.offset {IntOffset(drag.x.toInt(),drag.y.toInt())}
        .pointerInput(widget.id,true) {
            detectDragGesturesAfterLongPress(onDragStart={point->
                current.runtime.selected=current.id
                resizing=point.x>=size.width-with(density){24.dp.toPx()} && point.y>=size.height-with(density){24.dp.toPx()}
                drag=Offset.Zero
            },onDrag={change,amount->change.consume();drag+=amount},onDragCancel={drag=Offset.Zero},onDragEnd={
                val dx=with(density){drag.x.toDp().value};val dy=with(density){drag.y.toDp().value}
                val style=current.runtime.style(current.id)
                val bounds=current.runtime.controls[current.id]?.bounds
                current.runtime.edit(current.id,if(resizing)style.copy(
                    width=((style.width ?: with(density){(bounds?.width ?: 88f).toDp().value})+dx).coerceAtLeast(44f),
                    height=((style.height ?: with(density){(bounds?.height ?: 88f).toDp().value})+dy).coerceAtLeast(44f)
                ) else style.copy(dx=style.dx+dx,dy=style.dy+dy));drag=Offset.Zero
            })
        }.drawWithContent {
            drawContent()
            if(widget.runtime.selected==widget.id) {
                drawRect(androidx.compose.ui.graphics.Color(0xFF89E3B0),style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                drawRect(androidx.compose.ui.graphics.Color(0xFF89E3B0),topLeft=Offset(size.width-16.dp.toPx(),size.height-16.dp.toPx()),size=androidx.compose.ui.geometry.Size(16.dp.toPx(),16.dp.toPx()))
            }
        }
    return result.zIndex(s.layer.takeIf{it.isFinite()} ?: 0f).onGloballyPositioned {coordinates->
        val rect=coordinates.boundsInRoot()
        widget.runtime.bounds(widget.id,rect,System.identityHashCode(root),root.width.toFloat(),root.height.toFloat())
        if(s!=StudioControlStyle() && !widget.runtime.editing && widget.runtime.controls[widget.id]?.isAction==true) {
            val minimum=with(density){44.dp.toPx()}
            if(s.width!=null && rect.width<minimum-1 || s.height!=null && rect.height<minimum-1 || rect.left< -1 || rect.top< -1 ||
                rect.right>root.width+1 || rect.bottom>root.height+1)widget.runtime.recover(widget.id,"A control did not fit this viewport; its native layout was restored.")
        }
    }
}

@Composable
internal fun StudioText(site:String,screen:String,protectedLabel:Boolean,text:AnnotatedString,modifier:Modifier=Modifier,color:Color=Color.Unspecified,
    fontSize:TextUnit=TextUnit.Unspecified,fontStyle:FontStyle?=null,fontWeight:FontWeight?=null,fontFamily:FontFamily?=null,
    letterSpacing:TextUnit=TextUnit.Unspecified,textDecoration:TextDecoration?=null,textAlign:TextAlign?=null,lineHeight:TextUnit=TextUnit.Unspecified,
    overflow:TextOverflow=TextOverflow.Clip,softWrap:Boolean=true,maxLines:Int=Int.MAX_VALUE,minLines:Int=1,
    inlineContent:Map<String,InlineTextContent> = emptyMap(),onTextLayout:((TextLayoutResult)->Unit)?=null,style:TextStyle=LocalTextStyle.current) {
    val parent=LocalStudioControlParent.current ?: modifier.studioMarker()?.parent()
    val w=if(parent==null)studioWidget(site,screen,"data",text.text.take(100),protectedLabel=true) else null
    val s=parent?.style ?: w?.style
    val reportKey="$site/$currentCompositeKeyHash"
    if(parent!=null)SideEffect{parent.report(reportKey,text.text.take(100))}
    if(s?.visible==false)return
    androidx.compose.material3.Text(parent?.boundCaption?.let(::AnnotatedString) ?: text,modifier=modifier.studioWidget(w).then(if(w?.runtime?.editing==true)Modifier.clickable {w.runtime.select(w.id)} else Modifier),
        color=s?.ink?.let(::parseTableColorHex) ?: color,fontSize=s?.font?.coerceIn(8f,64f)?.sp ?: fontSize,fontStyle=fontStyle,fontWeight=fontWeight,
        fontFamily=fontFamily,letterSpacing=letterSpacing,textDecoration=textDecoration,textAlign=textAlign,lineHeight=s?.font?.coerceIn(8f,64f)?.let{(it*1.2f).sp} ?: lineHeight,
        overflow=overflow,softWrap=softWrap,maxLines=maxLines,minLines=minLines,inlineContent=inlineContent,onTextLayout=onTextLayout ?: {},style=style)
}

@Composable
private fun RowScope.StudioButtonContent(widget:StudioWidget?,content:@Composable RowScope.()->Unit) {
    CompositionLocalProvider(LocalStudioControlParent provides (widget?.parent() ?: LocalStudioControlParent.current)) {
        widget?.style?.icon?.let {StudioLayout.icons[it]}?.takeIf{it.isNotEmpty()}?.let {androidx.compose.material3.Text(it)}
        content()
    }
}

@Composable
internal fun StudioButton(site:String,screen:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    shape:Shape=ButtonDefaults.shape,colors:ButtonColors=ButtonDefaults.buttonColors(),elevation:ButtonElevation?=ButtonDefaults.buttonElevation(),
    border:BorderStroke?=null,contentPadding:PaddingValues=ButtonDefaults.ContentPadding,interactionSource:MutableInteractionSource?=null,
    content:@Composable RowScope.()->Unit) {
    val w=studioWidget(site,screen,"action","Action",enabled,onClick=onClick)
    val c=if(w==null)colors else colors.copy(containerColor=w.style.fill?.let(::parseTableColorHex) ?: colors.containerColor,
        contentColor=w.style.ink?.let(::parseTableColorHex) ?: colors.contentColor)
    androidx.compose.material3.Button(onClick={w?.click() ?: onClick()},modifier=modifier.studioWidget(w),enabled=w?.let{it.runtime.editing || it.enabled} ?: enabled,
        shape=w?.style?.radius?.let {RoundedCornerShape(it.coerceIn(0f,100f).dp)} ?: shape,colors=c,elevation=elevation,border=border,
        contentPadding=contentPadding,interactionSource=interactionSource) {StudioButtonContent(w,content)}
    StudioNativeCopies(w)
}

@Composable
internal fun StudioTextButton(site:String,screen:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    shape:Shape=ButtonDefaults.shape,colors:ButtonColors=ButtonDefaults.textButtonColors(),elevation:ButtonElevation?=null,
    border:BorderStroke?=null,contentPadding:PaddingValues=ButtonDefaults.TextButtonContentPadding,interactionSource:MutableInteractionSource?=null,
    content:@Composable RowScope.()->Unit) {
    val w=studioWidget(site,screen,"action","Action",enabled,onClick=onClick)
    val c=if(w==null)colors else colors.copy(containerColor=w.style.fill?.let(::parseTableColorHex) ?: colors.containerColor,
        contentColor=w.style.ink?.let(::parseTableColorHex) ?: colors.contentColor)
    androidx.compose.material3.TextButton(onClick={w?.click() ?: onClick()},modifier=modifier.studioWidget(w),enabled=w?.let{it.runtime.editing || it.enabled} ?: enabled,
        shape=w?.style?.radius?.let {RoundedCornerShape(it.coerceIn(0f,100f).dp)} ?: shape,colors=c,elevation=elevation,border=border,
        contentPadding=contentPadding,interactionSource=interactionSource) {StudioButtonContent(w,content)}
    StudioNativeCopies(w)
}

@Composable
internal fun StudioOutlinedButton(site:String,screen:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    shape:Shape=ButtonDefaults.shape,colors:ButtonColors=ButtonDefaults.outlinedButtonColors(),elevation:ButtonElevation?=null,
    border:BorderStroke?=ButtonDefaults.outlinedButtonBorder(enabled),contentPadding:PaddingValues=ButtonDefaults.ContentPadding,interactionSource:MutableInteractionSource?=null,
    content:@Composable RowScope.()->Unit) {
    val w=studioWidget(site,screen,"action","Action",enabled,onClick=onClick)
    val c=if(w==null)colors else colors.copy(containerColor=w.style.fill?.let(::parseTableColorHex) ?: colors.containerColor,
        contentColor=w.style.ink?.let(::parseTableColorHex) ?: colors.contentColor)
    androidx.compose.material3.OutlinedButton(onClick={w?.click() ?: onClick()},modifier=modifier.studioWidget(w),enabled=w?.let{it.runtime.editing || it.enabled} ?: enabled,
        shape=w?.style?.radius?.let {RoundedCornerShape(it.coerceIn(0f,100f).dp)} ?: shape,colors=c,elevation=elevation,border=border,
        contentPadding=contentPadding,interactionSource=interactionSource) {StudioButtonContent(w,content)}
    StudioNativeCopies(w)
}

@Composable
internal fun StudioIconButton(site:String,screen:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    colors:IconButtonColors=IconButtonDefaults.iconButtonColors(),interactionSource:MutableInteractionSource?=null,content:@Composable ()->Unit) {
    val w=studioWidget(site,screen,"icon-action","Icon action",enabled,onClick=onClick)
    androidx.compose.material3.IconButton(onClick={w?.click() ?: onClick()},modifier=modifier.studioWidget(w),enabled=w?.let{it.runtime.editing || it.enabled} ?: enabled,
        colors=if(w==null)colors else colors.copy(containerColor=w.style.fill?.let(::parseTableColorHex) ?: colors.containerColor,
            contentColor=w.style.ink?.let(::parseTableColorHex) ?: colors.contentColor),interactionSource=interactionSource) {
        val icon=w?.style?.icon?.let {StudioLayout.icons[it]}.orEmpty()
        CompositionLocalProvider(LocalStudioControlParent provides (w?.parent() ?: LocalStudioControlParent.current)) {
            if(icon.isNotEmpty())androidx.compose.material3.Text(icon) else content()
        }
    }
    StudioNativeCopies(w)
}

@Composable
internal fun StudioDropdownMenuItem(site:String,screen:String,text:@Composable ()->Unit,onClick:()->Unit,modifier:Modifier=Modifier,
    leadingIcon:(@Composable ()->Unit)?=null,trailingIcon:(@Composable ()->Unit)?=null,enabled:Boolean=true,
    colors:MenuItemColors=MenuDefaults.itemColors(),contentPadding:PaddingValues=MenuDefaults.DropdownMenuItemContentPadding,
    interactionSource:MutableInteractionSource?=null) {
    val w=studioWidget(site,screen,"action","Menu action",enabled,onClick=onClick)
    androidx.compose.material3.DropdownMenuItem(text={CompositionLocalProvider(LocalStudioControlParent provides (w?.parent() ?: LocalStudioControlParent.current)){text()}},
        onClick={w?.click() ?: onClick()},modifier=modifier.studioWidget(w),
        leadingIcon=w?.style?.icon?.let{StudioLayout.icons[it]}?.takeIf{it.isNotEmpty()}?.let {glyph->({androidx.compose.material3.Text(glyph)})} ?: leadingIcon,trailingIcon=trailingIcon,
        enabled=w?.let{it.runtime.editing || it.enabled} ?: enabled,colors=colors,contentPadding=contentPadding,interactionSource=interactionSource)
    StudioNativeCopies(w)
}

@Composable
internal fun StudioText(site:String,screen:String,protectedLabel:Boolean,text:String,modifier:Modifier=Modifier,color:Color=Color.Unspecified,
    fontSize:TextUnit=TextUnit.Unspecified,fontStyle:FontStyle?=null,fontWeight:FontWeight?=null,fontFamily:FontFamily?=null,
    letterSpacing:TextUnit=TextUnit.Unspecified,textDecoration:TextDecoration?=null,textAlign:TextAlign?=null,lineHeight:TextUnit=TextUnit.Unspecified,
    overflow:TextOverflow=TextOverflow.Clip,softWrap:Boolean=true,maxLines:Int=Int.MAX_VALUE,minLines:Int=1,
    onTextLayout:((TextLayoutResult)->Unit)?=null,style:TextStyle=LocalTextStyle.current) {
    val parent=LocalStudioControlParent.current ?: modifier.studioMarker()?.parent()
    val w=if(parent==null)studioWidget(site,screen,if(protectedLabel)"data" else "label",text.take(100),protectedLabel=protectedLabel) else null
    if(w!=null)SideEffect{w.runtime.text(w.id,text)}
    StudioNativeCopies(w,action=false)
    val s=parent?.style ?: w?.style
    val reportKey="$site/$currentCompositeKeyHash"
    if(parent!=null)SideEffect {parent.report(reportKey,text.take(100))}
    if(s?.visible==false)return
    val caption=parent?.boundCaption ?: if(s?.binding!=null && w!=null) {if(w.runtime.canDisplay(w.id,s.binding))w.runtime.text(s.binding) else "Information unavailable"} else s?.label?.let{if(protectedLabel && parent!=null)"$it · $text" else if(!protectedLabel)it else text} ?: text
    androidx.compose.material3.Text(caption,modifier=modifier.studioWidget(w).then(if(w?.runtime?.editing==true)
        Modifier.clickable {w.runtime.select(w.id)} else Modifier),
        color=s?.ink?.let(::parseTableColorHex) ?: color,fontSize=s?.font?.coerceIn(8f,64f)?.sp ?: fontSize,fontStyle=fontStyle,fontWeight=fontWeight,
        fontFamily=fontFamily,letterSpacing=letterSpacing,textDecoration=textDecoration,textAlign=textAlign,lineHeight=s?.font?.coerceIn(8f,64f)?.let{(it*1.2f).sp} ?: lineHeight,
        overflow=overflow,softWrap=softWrap,maxLines=maxLines,minLines=minLines,onTextLayout=onTextLayout,style=style)
}
