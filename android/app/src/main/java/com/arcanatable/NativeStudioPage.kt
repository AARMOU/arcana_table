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
import kotlin.math.roundToInt

/** Development milestone: only the functional hand screen is offered. The classic editor remains. */
@Composable
internal fun NativeStudioPage(store: StudioStore, cards: List<GameCard>, onApplied: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val history = remember(store) { StudioHistory(store.draft() ?: store.load() ?: StudioLayout.reference()) }
    var revision by remember { mutableIntStateOf(0) }
    val layout = remember(revision) { history.current }
    var selected by remember { mutableStateOf("pass") }
    var message by remember { mutableStateOf<String?>(null) }
    var jsonOpen by remember { mutableStateOf(false) }
    var jsonText by remember { mutableStateOf("") }
    var enabled by remember(store) { mutableStateOf(store.enabled()) }
    fun change(next: StudioLayout) { history.change(next);revision++;store.saveDraft(next) }
    fun edit(transform: (StudioElement)->StudioElement) { change(layout.copy(elements=layout.elements.map { if(it.id==selected) transform(it) else it })) }
    fun snap(value:Float) = (value/layout.snap).roundToInt()*layout.snap.toFloat()
    val element=layout.elements.firstOrNull { it.id==selected }
    MaterialTheme(colorScheme=darkColorScheme(primary=Mint,secondary=Mint,surface=Panel,background=Ink,onSurface=SoftWhite,onBackground=SoftWhite)) {
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize(),color=Panel) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().verticalScroll(rememberScrollState()).padding(12.dp)) {
                Text("Arcana Studio",style=MaterialTheme.typography.headlineSmall,color=SoftWhite)
                Text("Hand · development milestone · other screens not implemented",color=Muted)
                Text("Hold a control to drag. Select it and drag its corner to resize.",color=Muted)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick=onDismiss) { Text("Close") }
                    TextButton(enabled=history.canUndo,onClick={history.undo();revision++;store.saveDraft(history.current)}) { Text("Undo") }
                    TextButton(enabled=history.canRedo,onClick={history.redo();revision++;store.saveDraft(history.current)}) { Text("Redo") }
                    Button(onClick={ runCatching {store.apply(layout)}.onSuccess {enabled=true;onApplied();message="Applied to the real hand drawer."}.onFailure {message=it.message} },modifier=Modifier.testTag("studio-apply")) {Text("Apply")}
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
                    TextButton(onClick={
                        runCatching { context.assets.open("studio/owner-hand.json").bufferedReader().use { StudioLayout.parse(it.readText()) } }
                            .onSuccess {change(it);message="Owner hand design loaded into draft."}.onFailure {message=it.message}
                    }) {Text("Owner design")}
                }
                message?.let {Text(it,color=CounterGold)}
                val errors=layout.problems()
                if(errors.isNotEmpty()) Text(errors.distinct().take(6).joinToString("\n"),color=MaterialTheme.colorScheme.error)
                StudioHandCanvas(layout,StudioHandState(emptyMap(),emptySet(),cards),{},{},{},
                    editing=true,selectedElement=selected,onSelectElement={selected=it},
                    onMove={id,dx,dy->change(layout.copy(elements=layout.elements.map {if(it.id==id)it.copy(x=snap(it.x+dx),y=snap(it.y+dy)) else it}))},
                    onResize={id,dw,dh->change(layout.copy(elements=layout.elements.map {if(it.id==id)it.copy(w=snap(it.w+dw),h=snap(it.h+dh)) else it}))})
                Text("Layers",color=SoftWhite)
                layout.elements.chunked(4).forEach { group -> Row {group.forEach { e->TextButton(onClick={selected=e.id}) {Text(e.id)} } } }
                element?.let { e ->
                    Text("Selected: ${e.id}",color=Mint)
                    OutlinedTextField(e.label,{v->edit {it.copy(label=v)}},enabled=e.binding!="pass" && e.kind!="card",
                        label={Text(if(e.binding=="pass" || e.kind=="card") "Live game label (not editable)" else "Label (counts stay live)")},modifier=Modifier.fillMaxWidth())
                    listOf("X" to e.x,"Y" to e.y,"Width" to e.w,"Height" to e.h,"Font" to e.fs,"Radius" to e.r).forEach { (name,value) ->
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
                    var color by remember(e.id,e.color){mutableStateOf(e.color.orEmpty())}
                    OutlinedTextField(color,{v->color=v;edit{it.copy(color=v.takeIf(String::isNotBlank))}},label={Text("Fill color (#RRGGBB, blank uses theme)")},modifier=Modifier.fillMaxWidth())
                    Row {
                        TextButton(onClick={val id="copy${System.nanoTime()}";change(layout.copy(elements=layout.elements+e.copy(id=id,y=e.y+e.h+8)));selected=id}) {Text("Duplicate")}
                        TextButton(onClick={change(layout.copy(elements=layout.elements.filterNot{it.id==e.id}));selected="pass"}) {Text("Remove")}
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
