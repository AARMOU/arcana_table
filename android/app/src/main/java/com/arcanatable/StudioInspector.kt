package com.arcanatable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class StudioInspectorTab(val title:String) {Layout("Layout"),Style("Style"),Action("Action"),Arrange("Arrange")}

/** What the selected native control allows, mirroring the validation in [StudioControlsRuntime.problems]. */
internal data class StudioCapabilities(val label:Boolean,val font:Boolean,val radius:Boolean,val ink:Boolean,val icon:Boolean,
    val rebind:Boolean,val information:Boolean,val duplicate:Boolean) {
    companion object {
        fun of(c:StudioLiveControl)=StudioCapabilities(
            label=!c.protectedLabel && c.captionEditable && c.kind in listOf("action","container-action","icon-action","label","input","icon"),
            font=!(c.kind in listOf("range","toggle","icon","icon-action","card-action") || !c.captionEditable),
            radius=c.kind!="container-action",ink=c.kind!="card-action",icon=c.kind in listOf("action","icon-action"),
            rebind=c.isAction && c.kind!="card-action" && (c.kind!="container-action" || c.captionEditable),
            information=c.kind in listOf("data","label"),duplicate=c.isAction || c.kind in listOf("data","label"))
    }
}

/**
 * Contextual editor for the one selected control. It never covers the whole screen: it docks to the edge away from the
 * selection and can be collapsed to a single 56dp row.
 */
@Composable
internal fun StudioInspector(runtime:StudioControlsRuntime,control:StudioLiveControl,density:Float,collapsed:Boolean,
    onCollapse:(Boolean)->Unit,onMessage:(String)->Unit,modifier:Modifier=Modifier) {
    val style=runtime.style(control.id)
    val caps=StudioCapabilities.of(control)
    val tabs=StudioInspectorTab.entries.filter{it!=StudioInspectorTab.Action || caps.rebind || caps.information}
    var tab by remember(control.id){mutableStateOf(StudioInspectorTab.Layout)}
    if(tab !in tabs)tab=StudioInspectorTab.Layout
    val widthDp=control.bounds.width/density;val heightDp=control.bounds.height/density
    fun change(update:(StudioControlStyle)->StudioControlStyle)=runtime.edit(control.id,update(runtime.style(control.id)))
    val palette=runtime.project().colors(darkTableColors).let {c->listOf("Surface" to c.panel,"Raised" to c.raised,"Accent" to c.accent,
        "Text" to c.text,"Muted" to c.muted,"Gold" to c.counter,"Canvas" to c.background)}
    Surface(modifier.fillMaxWidth().testTag("studio-inspector"),color=darkTableColors.panel,shape=RoundedCornerShape(16.dp),shadowElevation=8.dp) {
        Column(Modifier.padding(horizontal=8.dp,vertical=4.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start=4.dp)) {
                    Text(control.title.ifBlank{"Untitled"},color=darkTableColors.text,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=15.sp)
                    Text("${control.screen} · ${kindName(control.kind)} · ${kotlin.math.round(widthDp).toInt()}×${kotlin.math.round(heightDp).toInt()}",color=darkTableColors.muted,fontSize=11.sp,maxLines=1)
                }
                StudioParentButton(runtime,control)
                IconButton(onClick={onCollapse(!collapsed)},modifier=Modifier.testTag("studio-inspector-collapse")) {
                    Icon(if(collapsed)Icons.Default.ExpandLess else Icons.Default.ExpandMore,if(collapsed)"Expand inspector" else "Collapse inspector",tint=darkTableColors.text)
                }
                IconButton(onClick={runtime.selected=null},modifier=Modifier.testTag("studio-inspector-close")) {
                    Icon(Icons.Default.Close,"Deselect",tint=darkTableColors.text)
                }
            }
            if(collapsed)return@Column
            Row(Modifier.fillMaxWidth()) {tabs.forEach {t->
                val on=t==tab
                Box(Modifier.weight(1f).heightIn(min=44.dp).clip(RoundedCornerShape(10.dp)).background(if(on)darkTableColors.raised else Color.Transparent)
                    .clickable{tab=t}.testTag("studio-tab-${t.name}"),contentAlignment=Alignment.Center) {
                    Text(if(t==StudioInspectorTab.Action && !caps.rebind)"Data" else t.title,color=if(on)darkTableColors.accent else darkTableColors.muted,fontSize=13.sp)
                }
            }}
            Column(Modifier.heightIn(max=260.dp).testTag("studio-inspector-body").verticalScroll(rememberScrollState()).padding(vertical=4.dp)) {
                when(tab) {
                    StudioInspectorTab.Layout->LayoutTab(style,widthDp,heightDp,::change)
                    StudioInspectorTab.Style->StyleTab(style,caps,palette,::change)
                    StudioInspectorTab.Action->ActionTab(runtime,control,style,caps,::change)
                    StudioInspectorTab.Arrange->ArrangeTab(runtime,control,style,caps,density,::change,onMessage)
                }
            }
        }
    }
}

private fun kindName(kind:String)=when(kind){"action"->"Button";"icon-action"->"Icon button";"container-action"->"Group";"card-action"->"Card";
    "data"->"Live info";"label"->"Label";"input"->"Input";"toggle"->"Toggle";"range"->"Slider";"icon"->"Icon";else->kind}

@Composable
private fun StudioParentButton(runtime:StudioControlsRuntime,control:StudioLiveControl) {
    val parent=StudioEditorGeometry.parentOf(runtime.controls.values,control)
    IconButton(onClick={parent?.let{runtime.selected=it.id}},enabled=parent!=null,modifier=Modifier.testTag("studio-select-parent")) {
        Icon(Icons.Default.VerticalAlignTop,"Select enclosing control",tint=if(parent!=null)darkTableColors.text else darkTableColors.muted.copy(alpha=0.4f))
    }
}

@Composable
private fun LayoutTab(style:StudioControlStyle,widthDp:Float,heightDp:Float,change:((StudioControlStyle)->StudioControlStyle)->Unit) {
    var step by remember{mutableFloatStateOf(4f)}
    val pad=@Composable {
        // Directional pad: one tap moves by the chosen step; the centre chip cycles 4 → 16 → 1 dp.
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            StudioPadButton(Icons.Default.KeyboardArrowUp,"Move up","studio-nudge-up"){change{StudioEditorGeometry.nudge(it,0f,-step)}}
            Row {
                StudioPadButton(Icons.Default.KeyboardArrowLeft,"Move left","studio-nudge-left"){change{StudioEditorGeometry.nudge(it,-step,0f)}}
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(darkTableColors.raised).clickable{step=if(step==4f)16f else if(step==16f)1f else 4f}.testTag("studio-nudge-step"),
                    contentAlignment=Alignment.Center){Text("${step.toInt()}dp",color=darkTableColors.accent,fontSize=12.sp)}
                StudioPadButton(Icons.Default.KeyboardArrowRight,"Move right","studio-nudge-right"){change{StudioEditorGeometry.nudge(it,step,0f)}}
            }
            StudioPadButton(Icons.Default.KeyboardArrowDown,"Move down","studio-nudge-down"){change{StudioEditorGeometry.nudge(it,0f,step)}}
        }
    }
    val sizes=@Composable {
        StudioStepper("Width","${(style.width ?: widthDp).toInt()}","studio-width",{change{StudioEditorGeometry.resize(it,widthDp,heightDp,-step,0f)}},{change{StudioEditorGeometry.resize(it,widthDp,heightDp,step,0f)}})
        StudioStepper("Height","${(style.height ?: heightDp).toInt()}","studio-height",{change{StudioEditorGeometry.resize(it,widthDp,heightDp,0f,-step)}},{change{StudioEditorGeometry.resize(it,widthDp,heightDp,0f,step)}})
    }
    val resets=@Composable {
        Text("Offset ${style.dx.toInt()}, ${style.dy.toInt()} dp",color=darkTableColors.muted,fontSize=11.sp)
        StudioChip("Native size",style.width!=null || style.height!=null,"studio-native-size"){change{it.copy(width=null,height=null)}}
        StudioChip("Native spot",style.dx!=0f || style.dy!=0f,"studio-native-position"){change{it.copy(dx=0f,dy=0f)}}
    }
    BoxWithConstraints {
        if(maxWidth<400.dp) Column {
            // Narrow (Fold cover): pad and resets side by side, size steppers full width underneath.
            Row(verticalAlignment=Alignment.CenterVertically){pad();Spacer(Modifier.width(8.dp));Column{resets()}}
            sizes()
        } else Row(verticalAlignment=Alignment.CenterVertically) {
            pad();Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)){sizes();Row(verticalAlignment=Alignment.CenterVertically){resets()}}
        }
    }
}

@Composable
private fun StyleTab(style:StudioControlStyle,caps:StudioCapabilities,palette:List<Pair<String,Color>>,change:((StudioControlStyle)->StudioControlStyle)->Unit) {
    if(caps.label) {
        var text by remember(style.label){mutableStateOf(style.label.orEmpty())}
        OutlinedTextField(text,{t->text=t.take(120);change{it.copy(label=text.takeIf(String::isNotBlank))}},singleLine=true,
            label={Text("Label (blank = native)")},modifier=Modifier.fillMaxWidth().testTag("studio-label"))
    }
    StudioColorField("Fill",style.fill,palette,{v->change{it.copy(fill=v)}},"studio-fill")
    if(caps.ink)StudioColorField("Text",style.ink,palette,{v->change{it.copy(ink=v)}},"studio-ink")
    if(caps.font)StudioStepper("Text size",style.font?.let{"${it.toInt()}sp"} ?: "Auto","studio-font",
        {change{it.copy(font=((it.font ?: 14f)-1f).coerceIn(8f,64f))}},{change{it.copy(font=((it.font ?: 14f)+1f).coerceIn(8f,64f))}})
    if(caps.radius)StudioStepper("Corners",style.radius?.let{"${it.toInt()}dp"} ?: "Auto","studio-radius",
        {change{it.copy(radius=((it.radius ?: 12f)-2f).coerceIn(0f,100f))}},{change{it.copy(radius=((it.radius ?: 12f)+2f).coerceIn(0f,100f))}})
    if(caps.icon) {
        Text("Icon",color=darkTableColors.muted,fontSize=12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState())) {StudioLayout.icons.forEach {(name,glyph)->
            StudioChip(if(glyph.isEmpty())name else "$glyph $name",true,"studio-icon-$name",selected=style.icon==name){change{it.copy(icon=name)}}
        }}
    }
}

@Composable
private fun ActionTab(runtime:StudioControlsRuntime,control:StudioLiveControl,style:StudioControlStyle,caps:StudioCapabilities,
    change:((StudioControlStyle)->StudioControlStyle)->Unit) {
    var query by remember(control.id){mutableStateOf("")}
    // Only callbacks the live screen currently offers are listed: the engine's legal actions are never invented here.
    val targets=runtime.controls.values.filter {t->t.id!=control.id && t.id.startsWith("native:") && t.screen==control.screen && t.window==control.window &&
        (if(caps.rebind)t.isAction && t.enabled && runtime.canInvoke(control.id,t.id) else runtime.canDisplay(control.id,t.id)) &&
        t.title.contains(query,true)}.distinctBy{it.title}.sortedBy{it.title}
    Text(if(caps.rebind)"When tapped, this button runs:" else "This module shows:",color=darkTableColors.muted,fontSize=12.sp)
    OutlinedTextField(query,{query=it},singleLine=true,label={Text(if(caps.rebind)"Search actions on this screen" else "Search information")},
        leadingIcon={Icon(Icons.Default.Search,null)},modifier=Modifier.fillMaxWidth().testTag("studio-action-search"))
    StudioListRow(if(caps.rebind)"Its own action" else "Its own information",style.binding==null,"studio-binding-original"){change{it.copy(binding=null)}}
    LazyColumn(Modifier.heightIn(max=180.dp)) {
        items(targets,key={it.id}) {t->
            StudioListRow(t.title,style.binding==t.id,"studio-binding-option"){change{s->s.copy(binding=t.id,label=if(caps.rebind)s.label else null)}}
        }
    }
    if(targets.isEmpty())Text("No other ${if(caps.rebind)"available actions" else "information"} on this screen right now.",color=darkTableColors.muted,fontSize=12.sp)
}

@Composable
private fun ArrangeTab(runtime:StudioControlsRuntime,control:StudioLiveControl,style:StudioControlStyle,caps:StudioCapabilities,density:Float,
    change:((StudioControlStyle)->StudioControlStyle)->Unit,onMessage:(String)->Unit) {
    Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
        Text("Visible",color=darkTableColors.text,modifier=Modifier.weight(1f))
        Switch(style.visible,{v->change{it.copy(visible=v)}},modifier=Modifier.testTag("studio-visible"))
    }
    StudioStepper("Layer",style.layer.toInt().toString(),"studio-layer",{change{it.copy(layer=(it.layer-1f).coerceIn(-100f,100f))}},
        {change{it.copy(layer=(it.layer+1f).coerceIn(-100f,100f))}})
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        if(caps.duplicate)StudioChip(if(control.isAction)"Duplicate button" else "Add info module",true,"studio-duplicate") {
            runCatching{runtime.duplicate(control.id,density)}.onFailure{onMessage(it.message ?: "Cannot duplicate this control.")}
        }
        if(control.id.startsWith("copy:"))StudioChip("Remove",true,"studio-remove"){runtime.removeCopy(control.id)}
        else StudioChip("Reset element",control.id in runtime.draft.controls,"studio-reset-element") {
            runtime.change(runtime.draft.copy(controls=runtime.draft.controls-control.id))
        }
    }
    if(control.requiredNativeAction)Text("Required game action: hiding or rebinding it needs a visible replacement.",color=darkTableColors.counter,fontSize=11.sp)
}

@Composable
internal fun StudioPadButton(icon:androidx.compose.ui.graphics.vector.ImageVector,description:String,tag:String,onClick:()->Unit) {
    IconButton(onClick=onClick,modifier=Modifier.size(48.dp).testTag(tag)){Icon(icon,description,tint=darkTableColors.text)}
}

@Composable
internal fun StudioStepper(label:String,value:String,tag:String,onMinus:()->Unit,onPlus:()->Unit) {
    Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()) {
        Text(label,color=darkTableColors.muted,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
        IconButton(onClick=onMinus,modifier=Modifier.size(48.dp).testTag("$tag-minus")){Icon(Icons.Default.Remove,"Decrease $label",tint=darkTableColors.text)}
        Text(value,color=darkTableColors.text,fontSize=13.sp,modifier=Modifier.widthIn(min=44.dp).testTag("$tag-value"),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick=onPlus,modifier=Modifier.size(48.dp).testTag("$tag-plus")){Icon(Icons.Default.Add,"Increase $label",tint=darkTableColors.text)}
    }
}

@Composable
internal fun StudioChip(text:String,enabled:Boolean,tag:String,selected:Boolean=false,onClick:()->Unit) {
    Box(Modifier.padding(end=6.dp,top=2.dp,bottom=2.dp).heightIn(min=44.dp).clip(RoundedCornerShape(22.dp))
        .background(if(selected)darkTableColors.accent.copy(alpha=0.22f) else darkTableColors.raised)
        .clickable(enabled=enabled,onClick=onClick).padding(horizontal=14.dp).testTag(tag),contentAlignment=Alignment.Center) {
        Text(text,color=if(!enabled)darkTableColors.muted.copy(alpha=0.5f) else if(selected)darkTableColors.accent else darkTableColors.text,fontSize=13.sp,maxLines=1)
    }
}

@Composable
internal fun StudioListRow(text:String,selected:Boolean,tag:String,trailing:(@Composable ()->Unit)?=null,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clip(RoundedCornerShape(10.dp)).background(if(selected)darkTableColors.accent.copy(alpha=0.18f) else Color.Transparent)
        .clickable(onClick=onClick).padding(horizontal=12.dp).testTag(tag),verticalAlignment=Alignment.CenterVertically) {
        if(selected)Icon(Icons.Default.Check,null,tint=darkTableColors.accent,modifier=Modifier.size(18.dp).padding(end=4.dp))
        Text(text,color=if(selected)darkTableColors.accent else darkTableColors.text,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f),fontSize=14.sp)
        trailing?.invoke()
    }
}
