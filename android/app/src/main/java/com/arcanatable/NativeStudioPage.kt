package com.arcanatable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.math.roundToInt

/** Development milestone: only the functional hand screen is offered. The classic editor remains. */
@Composable
internal fun NativeStudioPage(store: StudioStore, cards: List<GameCard>, onApplied: () -> Unit, onDismiss: () -> Unit,
    initialLayout: StudioLayout = StudioLayout.reference(), profileNames: List<String> = emptyList(),
    onProfile: (String)->Unit = {}, onOrientation: ((String)->Unit)? = null,
    onCopyProfile: ((String,StudioLayout)->Unit)? = null, onResetAll: (() -> Unit)? = null,
    onViewGame: (() -> Unit)? = null,onOtherScreens:(()->Unit)?=null) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val history = remember(store) { StudioHistory(store.draft() ?: store.load() ?: initialLayout) }
    var revision by remember(store) { mutableIntStateOf(0) }
    val layout = remember(history,revision) { history.current }
    var selected by remember { mutableStateOf("pass") }
    var message by remember { mutableStateOf<String?>(null) }
    var jsonOpen by remember { mutableStateOf(false) }
    var jsonText by remember { mutableStateOf("") }
    var enabled by remember(store) { mutableStateOf(store.enabled()) }
    var copyName by remember {mutableStateOf("")}
    fun change(next: StudioLayout) { history.change(next);revision++;store.saveDraft(next) }
    fun edit(transform: (StudioElement)->StudioElement) { change(layout.copy(elements=layout.elements.map { if(it.id==selected) transform(it) else it })) }
    fun snap(value:Float) = (value/layout.snap).roundToInt()*layout.snap.toFloat()
    val element=layout.elements.firstOrNull { it.id==selected }
    val importFile=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri ->
        if(uri!=null) runCatching {
            val text=context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val buffer=CharArray(250001)
                var count=0
                while(count<buffer.size) {val n=reader.read(buffer,count,buffer.size-count);if(n<0)break;count+=n}
                require(count<=250000){"Project is too large."};String(buffer,0,count)
            } ?: error("Could not open project.")
            StudioLayout.parse(text)
        }.onSuccess {change(it);message="Imported project into draft."}.onFailure {message=it.message}
    }
    val exportFile=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {uri ->
        if(uri!=null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use {it.write(layout.json().toByteArray(Charsets.UTF_8))}
                ?: error("Could not write project.")
        }.onSuccess {message="Project exported."}.onFailure {message=it.message}
    }
    val previewBaseColors=LocalTableColors.current
    CompositionLocalProvider(LocalTableColors provides darkTableColors) {
    MaterialTheme(colorScheme=darkColorScheme(primary=Mint,secondary=Mint,surface=Panel,background=Ink,onSurface=SoftWhite,onBackground=SoftWhite)) {
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize(),color=Panel) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().verticalScroll(rememberScrollState()).padding(12.dp)) {
                Text("Arcana Studio",style=MaterialTheme.typography.headlineSmall,color=SoftWhite)
                onOtherScreens?.let {TextButton(onClick=it){Text("All screens and controls")}}
                Text("Hand · ${store.orientation} · ${store.profile}",color=Muted)
                Text("Native Argentum hand layout. Classic controls remain available.",color=Muted)
                onOrientation?.let {selectOrientation -> Row {
                    listOf("portrait","landscape").forEach {name ->TextButton(onClick={selectOrientation(name)}) {Text(if(name==store.orientation)"[$name]" else name)}}
                } }
                profileNames.chunked(3).forEach {group->Row {group.forEach {name->TextButton(onClick={onProfile(name)}) {Text(if(name==store.profile)"[$name]" else name)}}} }
                Row {listOf("Minimal","Balanced","Detailed").forEach {density->
                    TextButton(onClick={change(StudioLayout.responsive(layout.canvasWidth,layout.canvasHeight,density))}){Text("$density draft")}
                }}
                Text("Hold a control to drag. Select it and drag its corner to resize.",color=Muted)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick=onDismiss) { Text("Close") }
                    TextButton(enabled=history.canUndo,onClick={history.undo();revision++;store.saveDraft(history.current)}) { Text("Undo") }
                    TextButton(enabled=history.canRedo,onClick={history.redo();revision++;store.saveDraft(history.current)}) { Text("Redo") }
                    Button(onClick={ runCatching {store.apply(layout)}.onSuccess {enabled=true;onApplied();message="Saved for the native hand drawer.";onViewGame?.invoke()}.onFailure {message=it.message} },modifier=Modifier.testTag("studio-apply")) {Text("Apply")}
                }
                Row {
                    Switch(checked=enabled,onCheckedChange={ value ->
                        if(value && store.load()==null) message="Apply a valid draft before enabling it."
                        else {enabled=value;store.enable(value);onApplied()}
                    })
                    Text("Use saved hand layout",color=SoftWhite,modifier=Modifier.padding(12.dp))
                }
                Row {
                    TextButton(onClick={change(StudioLayout.reference())}) {Text("Reset draft")}
                    TextButton(onClick={store.reset();enabled=false;change(StudioLayout.reference());onApplied();message="Native hand restored."}) {Text("Restore native hand")}
                }
                Row {
                    TextButton(onClick={jsonText=layout.json();jsonOpen=true}) {Text("Import / export JSON")}
                    TextButton(onClick={importFile.launch(arrayOf("application/json","text/plain"))}) {Text("Open file")}
                    TextButton(onClick={exportFile.launch("Arcana-hand-${store.orientation}-${store.profile}.json")}) {Text("Save file")}
                }
                Row {
                    TextButton(onClick={
                        runCatching { context.assets.open("studio/owner-hand.json").bufferedReader().use { StudioLayout.parse(it.readText()) } }
                            .onSuccess {change(it);message="Owner hand design loaded into draft."}.onFailure {message=it.message}
                    }) {Text("Owner design")}
                }
                onCopyProfile?.let {copy ->
                    OutlinedTextField(copyName,{copyName=it},label={Text("New profile name")},modifier=Modifier.fillMaxWidth())
                    TextButton(onClick={runCatching{copy(copyName,layout)}.onFailure{message=it.message}}){Text("Copy to new profile")}
                }
                onResetAll?.let {resetAll->TextButton(onClick={resetAll();message="All Studio profiles reset; other app data preserved."}) {Text("Reset all Studio profiles")}}
                listOf("Canvas width" to layout.canvasWidth,"Canvas height" to layout.canvasHeight,"Hand origin Y" to layout.originY).forEach { (name,value)->
                    var field by remember(store,name,value){mutableStateOf(value.toString())}
                    OutlinedTextField(field,{v->field=v;v.toFloatOrNull()?.takeIf {it.isFinite()}?.let {n->change(when(name){"Canvas width"->layout.copy(canvasWidth=n);"Canvas height"->layout.copy(canvasHeight=n);else->layout.copy(originY=n)})}},
                        label={Text(name)},modifier=Modifier.fillMaxWidth())
                }
                message?.let {Text(it,color=CounterGold)}
                val errors=layout.problems()
                if(errors.isNotEmpty()) Text(errors.distinct().take(6).joinToString("\n"),color=MaterialTheme.colorScheme.error)
                CompositionLocalProvider(LocalTableColors provides previewBaseColors) {
                StudioHandCanvas(layout,StudioHandState(emptyMap(),emptySet(),cards),{},{},{},
                    editing=true,selectedElement=selected,onSelectElement={selected=it},
                    onMove={id,dx,dy->change(layout.copy(elements=layout.elements.map {if(it.id==id)it.copy(x=snap(it.x+dx),y=snap(it.y+dy)) else it}))},
                    onResize={id,dw,dh->change(layout.copy(elements=layout.elements.map {if(it.id==id)it.copy(w=snap(it.w+dw),h=snap(it.h+dh)) else it}))})
                }
                Text("Layers",color=SoftWhite)
                layout.elements.chunked(4).forEach { group -> Row {group.forEach { e->TextButton(onClick={selected=e.id}) {Text(e.id)} } } }
                element?.let { e ->
                    Text("Selected: ${e.id}",color=Mint)
                    OutlinedTextField(e.label,{v->edit {it.copy(label=v)}},enabled=e.binding!="pass" && e.kind!="card",
                        label={Text(if(e.binding=="pass" || e.kind=="card") "Live game label (not editable)" else "Label (counts stay live)")},modifier=Modifier.fillMaxWidth())
                    (listOf("X" to e.x,"Y" to e.y,"Width" to e.w,"Height" to e.h)+
                        (if(e.kind=="card")emptyList() else listOf("Font" to e.fs))+listOf("Radius" to e.r)).forEach { (name,value) ->
                        var field by remember(e.id,name,value) {mutableStateOf(value.toString())}
                        OutlinedTextField(field, onValueChange = { v ->
                            field = v
                            v.toFloatOrNull()?.takeIf { it.isFinite() }?.let { number ->
                                edit { item -> when (name) {
                                    "X" -> item.copy(x=number)
                                    "Y" -> item.copy(y=number)
                                    "Width" -> item.copy(w=number)
                                    "Height" -> item.copy(h=number)
                                    "Font" -> item.copy(fs=number)
                                    else -> item.copy(r=number)
                                } }
                            }
                        }, label={Text(name)},singleLine=true,modifier=Modifier.fillMaxWidth())
                    }
                    Row { Checkbox(e.locked,{v->edit{it.copy(locked=v)}});Text("Lock dragging",color=SoftWhite) }
                    Row { Checkbox(e.visible,{v->edit{it.copy(visible=v)}});Text("Visible (required actions must remain reachable)",color=SoftWhite) }
                    if(e.kind!="card") {
                        Text("Real action binding",color=Mint)
                        StudioLayout.bindings.chunked(4).forEach { group->Row {group.forEach {b->TextButton(onClick={edit{it.copy(binding=b)}}){Text(if(e.binding==b)"[$b]" else b)}}} }
                    }
                    if(e.kind!="card") {
                    var color by remember(e.id,e.color){mutableStateOf(e.color.orEmpty())}
                    OutlinedTextField(color,{v->color=v;edit{it.copy(color=v.takeIf(String::isNotBlank))}},label={Text("Fill color (#RRGGBB, blank uses theme)")},modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(e.textColor.orEmpty(),{v->edit{it.copy(textColor=v.takeIf(String::isNotBlank))}},label={Text("Text color (#RRGGBB, blank uses theme)")},modifier=Modifier.fillMaxWidth())
                    StudioLayout.icons.keys.toList().chunked(4).forEach {group->Row{group.forEach {icon->TextButton(onClick={edit{it.copy(icon=icon)}}){Text(if(e.icon==icon)"[$icon]" else icon)}}} }
                    } else Text("Live hand slot ${e.binding.removePrefix("card").toIntOrNull()?.plus(1)} · artwork and card names come from the game.",color=Muted)
                    Row {
                        TextButton(onClick={val next=layout.duplicateElement(e.id);change(next);selected=next.elements.last().id}) {Text("Duplicate")}
                        TextButton(enabled=e.kind!="card" || layout.elements.count{it.kind=="card"}>1,
                            onClick={change(layout.removeElement(e.id));selected="pass"}) {Text("Remove")}
                    }
                }
                Text("Hand palette (saved with this layout)",color=Mint)
                tablePalettePresets.chunked(2).forEach { group -> Row {group.forEach { preset ->
                    TextButton(onClick={change(layout.copy(theme=StudioLayout.themeRoles.associateWith { role ->
                        tableColorHex(preset.colors.colorRole(if(role=="gold")"counter" else role))
                    }))}) {Text(preset.name)}
                }} }
                StudioLayout.themeRoles.forEach { role ->
                    val value=layout.theme[role].orEmpty()
                    var field by remember(role,value){mutableStateOf(value)}
                    OutlinedTextField(field,{v->field=v;change(layout.copy(theme=if(v.isBlank())layout.theme-role else layout.theme+(role to v)))},
                        label={Text("$role (#RRGGBB; blank inherits table)")},modifier=Modifier.fillMaxWidth())
                }
            }
        }
    }
    if(jsonOpen) AlertDialog(onDismissRequest={jsonOpen=false},title={Text("Hand project JSON")},text={
        Column {OutlinedTextField(jsonText,{jsonText=it},modifier=Modifier.fillMaxWidth().heightIn(max=300.dp));message?.let {Text(it)};TextButton(onClick={clipboard.setText(AnnotatedString(jsonText));message="JSON copied."}){Text("Copy JSON")}}
    },confirmButton={TextButton(onClick={runCatching{StudioLayout.parse(jsonText)}.onSuccess{change(it);jsonOpen=false;message="Imported into draft; Apply to use."}.onFailure{message=it.message}}){Text("Import draft")}},dismissButton={TextButton(onClick={jsonOpen=false}){Text("Close")}})
    }
    }
}
