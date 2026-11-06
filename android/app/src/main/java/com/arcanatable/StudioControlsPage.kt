package com.arcanatable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Edits the actual native controls; there is no separate screen mockup. */
@Composable
internal fun StudioControlsPage(runtime:StudioControlsRuntime,onApplied:()->Unit,onHand:()->Unit,
    destinations:Map<String,Pair<Boolean,()->Unit>>) {
    val clipboard=LocalClipboardManager.current
    val density=LocalDensity.current.density
    var query by remember {mutableStateOf("")}
    var screen by remember {mutableStateOf<String?>(null)}
    var message by remember {mutableStateOf<String?>(null)}
    var json by remember {mutableStateOf<String?>(null)}
    var profileName by remember{mutableStateOf("")}
    val selected=runtime.selected?.let {runtime.controls[it]}
    val style=selected?.let {runtime.style(it.id)}
    fun change(update:(StudioControlStyle)->StudioControlStyle) {
        selected?.let {runtime.edit(it.id,update(runtime.style(it.id)))}
    }
    CompositionLocalProvider(LocalTableColors provides darkTableColors) {
    MaterialTheme(colorScheme=darkColorScheme(primary=Mint,surface=Panel,onSurface=SoftWhite,background=Ink)) {
    Dialog(onDismissRequest={runtime.cancel()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize(),color=Panel) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().verticalScroll(rememberScrollState()).padding(12.dp)) {
                Text("Arcana Studio",style=MaterialTheme.typography.headlineSmall)
                Text("Native screens · ${runtime.orientation} · ${runtime.profile}")
                Text("Inspect the real screen. Tap a control to edit; hold to move, or hold its lower-right corner to resize. Taps select controls; native game decisions remain authoritative.")
                Row {
                    Button(onClick={runCatching{runtime.apply()}.onSuccess{onApplied()}.onFailure{message=it.message}}){Text("Apply")}
                    TextButton(onClick={runtime.cancel()}){Text("Cancel")}
                    TextButton(onClick={runtime.inspectNativeScreen()}){Text("Inspect screen")}
                }
                Row {
                    TextButton(enabled=runtime.canUndo,onClick={runtime.undo()}){Text("Undo")}
                    TextButton(enabled=runtime.canRedo,onClick={runtime.redo()}){Text("Redo")}
                    TextButton(onClick=onHand){Text("Hand canvas")}
                }
                StudioProfiles(runtime.prefs,runtime.orientation).names().chunked(3).forEach {group->Row {group.forEach {name->
                    TextButton(onClick={runtime.chooseProfile(name)}){Text(if(name==runtime.profile)"[$name]" else name)}
                }} }
                OutlinedTextField(profileName,{profileName=it},label={Text("New profile name")},modifier=Modifier.fillMaxWidth())
                TextButton(onClick={runCatching {
                    runtime.copyProfile(profileName)
                }.onFailure{message=it.message}}){Text("Copy profile")}
                Text("Open a native screen",color=Mint)
                destinations.entries.toList().chunked(2).forEach {group->Row {group.forEach {(name,destination)->
                    TextButton(enabled=destination.first,onClick={destination.second();runtime.inspectNativeScreen()}){Text(name)}
                }} }
                Text("Game-only screens become available during an active game. Choices and combat remain the engine's current legal state.",color=Muted)
                Row {
                    TextButton(onClick={selected?.screen?.let {runtime.resetScreen(it)}} ,enabled=selected!=null){Text("Reset screen")}
                    TextButton(onClick={runtime.resetProfile();onApplied()}){Text("Reset profile")}
                    TextButton(onClick={StudioProfiles(runtime.prefs,runtime.orientation).resetAll();runtime.cancel();onApplied()}){Text("Reset all Studio")}
                }
                Text("Information density: ${screen ?: "all screens"}")
                TextButton(onClick={json=runtime.exportProject()}){Text("Import / export")}
                listOf("Minimal","Balanced","Detailed").chunked(2).forEach {group->Row {group.forEach {value->
                    TextButton(onClick={runtime.density(screen,value)}){Text(value)}
                }} }
                message?.let {Text(it,color=CounterGold)}
                runtime.recoveryReasons().forEach {Text(it,color=CounterGold)}
                runtime.problems().distinct().take(8).forEach {Text(it,color=MaterialTheme.colorScheme.error)}
                Text("Project palette",color=Mint)
                tablePalettePresets.chunked(2).forEach {group->Row{group.forEach {preset->TextButton(onClick={
                    runtime.change(runtime.draft.copy(theme=StudioLayout.themeRoles.associateWith{role->tableColorHex(preset.colors.colorRole(if(role=="gold")"counter" else role))}))
                }){Text(preset.name)}}}}
                StudioLayout.themeRoles.forEach {role->
                    OutlinedTextField(runtime.draft.theme[role].orEmpty(),{value->runtime.change(runtime.draft.copy(theme=if(value.isBlank())runtime.draft.theme-role else runtime.draft.theme+(role to value)))},
                        label={Text("$role #RRGGBB; blank inherits table")},modifier=Modifier.fillMaxWidth())
                }
                selected?.let {control->
                    Text("Selected: ${control.title}",color=Mint)
                    Text(control.screen+" · "+control.kind,color=Muted)
                    val current=style ?: StudioControlStyle()
                    OutlinedTextField(current.label.orEmpty(),{value->change{it.copy(label=value.takeIf(String::isNotBlank))}},
                        enabled=!control.protectedLabel && control.captionEditable && control.kind in listOf("action","container-action","icon-action","label","input","icon"),label={Text(if(control.protectedLabel)"Live data label is protected" else if(!control.captionEditable)"Edit this control's text layer" else if(control.kind in listOf("range","toggle"))"State comes from the native setting" else "Label / action caption")},modifier=Modifier.fillMaxWidth())
                    (listOf("X offset" to current.dx,"Y offset" to current.dy,"Width" to current.width,"Height" to current.height)+
                        (if(control.kind in listOf("range","toggle","icon","icon-action","card-action") || !control.captionEditable)emptyList() else listOf("Font" to current.font))+
                        (if(control.kind=="container-action")emptyList() else listOf("Radius" to current.radius))+listOf("Layer" to current.layer)).forEach {(name,value)->
                        var field by remember(control.id,name,value){mutableStateOf(value?.toString().orEmpty())}
                        OutlinedTextField(field,{v->field=v;val number=v.toFloatOrNull();if(v.isBlank() || number?.isFinite()==true)change {when(name){
                            "X offset"->it.copy(dx=number ?: 0f);"Y offset"->it.copy(dy=number ?: 0f);"Width"->it.copy(width=number);"Height"->it.copy(height=number);
                            "Font"->it.copy(font=number);"Radius"->it.copy(radius=number);else->it.copy(layer=number ?: 0f)
                        }}},label={Text("$name (blank = native)")},modifier=Modifier.fillMaxWidth())
                    }
                    Row {Checkbox(current.visible,{v->change{it.copy(visible=v)}});Text("Visible")}
                    OutlinedTextField(current.fill.orEmpty(),{v->change{it.copy(fill=v.takeIf(String::isNotBlank))}},label={Text("Fill #RRGGBB")},modifier=Modifier.fillMaxWidth())
                    if(control.kind!="card-action")OutlinedTextField(current.ink.orEmpty(),{v->change{it.copy(ink=v.takeIf(String::isNotBlank))}},label={Text("Text / icon #RRGGBB")},modifier=Modifier.fillMaxWidth())
                    if(control.kind in listOf("data","label")) {
                        TextButton(onClick={runCatching{runtime.duplicate(control.id,density)}.onFailure{message=it.message}}){Text("Add live information module")}
                        if(control.id.startsWith("copy:"))TextButton(onClick={runtime.removeCopy(control.id)}){Text("Remove module")}
                        Text("Native information binding",color=Mint)
                        TextButton(onClick={change{it.copy(binding=null)}}){Text("Original information")}
                        runtime.controls.values.filter{it.id.startsWith("native:") && it.kind in listOf("data","label") && it.screen==control.screen && it.window==control.window}.forEach{target->
                            TextButton(onClick={change{it.copy(binding=target.id,label=null)}}){Text(target.title)}
                        }
                    }
                    if(control.isAction) {
                        Row {
                            TextButton(onClick={runCatching{runtime.duplicate(control.id,density)}.onFailure{message=it.message}}){Text("Duplicate button")}
                            if(control.id.startsWith("copy:"))TextButton(onClick={runtime.removeCopy(control.id)}){Text("Remove duplicate")}
                        }
                        if(control.kind in listOf("action","icon-action"))StudioLayout.icons.keys.toList().chunked(4).forEach {group->Row{group.forEach {icon->TextButton(onClick={change{it.copy(icon=icon)}}){Text(if(current.icon==icon)"[$icon]" else icon)}}} }
                        if(control.kind!="card-action" && (control.kind!="container-action" || control.captionEditable)) {
                        Text("Native action binding",color=Mint)
                        TextButton(onClick={change{it.copy(binding=null)}}){Text("Original action")}
                        runtime.controls.values.filter {it.id.startsWith("native:") && it.isAction && it.enabled && it.screen==control.screen && it.window==control.window}.distinctBy{it.title}.take(60).forEach {target->
                            TextButton(onClick={change{it.copy(binding=target.id)}}){Text(target.title)}
                        }
                        } else Text("Duplicate this compound control as a button to change its action; its text layers can be styled separately.",color=Muted)
                    }
                }
                OutlinedTextField(query,{query=it},label={Text("Find visible elements")},modifier=Modifier.fillMaxWidth())
                val areas=runtime.controls.values.map{it.screen}.distinct().sorted()
                areas.chunked(2).forEach {group->Row{group.forEach {area->TextButton(onClick={screen=if(screen==area)null else area}){Text(if(screen==area)"[$area]" else area)}}} }
                runtime.controls.values.filter {(screen==null || it.screen==screen) && it.title.contains(query,true)}.sortedBy{it.title}.take(300).forEach {control->
                    TextButton(onClick={runtime.selected=control.id}){Text(control.title.take(100)+" · "+control.kind)}
                }
            }
        }
    }
    json?.let {project->AlertDialog(onDismissRequest={json=null},title={Text("Native controls JSON")},text={Column {
        OutlinedTextField(project,{json=it},modifier=Modifier.fillMaxWidth().heightIn(max=300.dp))
        TextButton(onClick={clipboard.setText(AnnotatedString(project))}){Text("Copy JSON")}
        message?.let {Text(it)}
    }},confirmButton={TextButton(onClick={runCatching{runtime.importProject(project)}.onSuccess{json=null}.onFailure{message=it.message}}){Text("Import draft")}},dismissButton={TextButton(onClick={json=null}){Text("Close")}})}
    }
    }
}
