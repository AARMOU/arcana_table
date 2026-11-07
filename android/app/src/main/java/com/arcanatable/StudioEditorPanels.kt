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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Compact single-row toolbar: fits 344dp (Fold cover) with 44dp+ targets. */
@Composable
internal fun StudioTopBar(runtime:StudioControlsRuntime,screen:String,onClose:()->Unit,onScreens:()->Unit,onLayers:()->Unit,
    layersOpen:Boolean,onMore:()->Unit,onApply:()->Unit,scrollMode:Boolean?=null,onToggleScroll:()->Unit={}) {
    // Fold cover (~344dp): Apply becomes a 44dp check so the screen name keeps room to be read.
    val narrow=androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp<380
    Surface(color=darkTableColors.panel,shape=RoundedCornerShape(bottomStart=16.dp,bottomEnd=16.dp),shadowElevation=6.dp,modifier=Modifier.fillMaxWidth().testTag("studio-top-bar")) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal=if(narrow)0.dp else 2.dp),verticalAlignment=Alignment.CenterVertically) {
            StudioBarIcon(Icons.Default.Close,"Close Studio","studio-close",onClick=onClose)
            // A plain label: screens are chosen from the always-visible screen bar below.
            Column(Modifier.weight(1f).heightIn(min=44.dp).padding(horizontal=if(narrow)2.dp else 6.dp).testTag("studio-screen-title"),verticalArrangement=Arrangement.Center) {
                Text(screen,color=darkTableColors.text,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=15.sp)
                Text(runtime.profile,color=darkTableColors.muted,fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            StudioBarIcon(Icons.AutoMirrored.Filled.Undo,"Undo","studio-undo",enabled=runtime.canUndo){runtime.undo()}
            StudioBarIcon(Icons.AutoMirrored.Filled.Redo,"Redo","studio-redo",enabled=runtime.canRedo){runtime.redo()}
            if(scrollMode!=null)StudioBarIcon(Icons.Default.PanTool,if(scrollMode)"Scroll mode: tap to edit" else "Scroll the screen",
                if(scrollMode)"studio-tool-Select" else "studio-tool-Scroll",active=scrollMode,onClick=onToggleScroll)
            StudioBarIcon(Icons.Default.Layers,"Layers","studio-layers",active=layersOpen,onClick=onLayers)
            StudioBarIcon(Icons.Default.MoreVert,"More options","studio-more",onClick=onMore)
            // Owner note 35: Apply is a squared Arcana Action button in the palette's accent, not a mint circle.
            if(narrow)FilledIconButton(onClick=onApply,modifier=Modifier.size(44.dp).testTag("studio-apply"),shape=RoundedCornerShape(10.dp),
                colors=IconButtonDefaults.filledIconButtonColors(containerColor=StudioEditorRequests.chromeAction,contentColor=darkTableColors.background)) {Icon(Icons.Default.Check,"Apply")}
            else Button(onClick=onApply,contentPadding=PaddingValues(horizontal=12.dp),modifier=Modifier.heightIn(min=44.dp).testTag("studio-apply"),shape=RoundedCornerShape(10.dp),
                colors=ButtonDefaults.buttonColors(containerColor=StudioEditorRequests.chromeAction,contentColor=darkTableColors.background)) {Text("APPLY",fontWeight=FontWeight.Bold,letterSpacing=0.6.sp)}
        }
    }
}

@Composable
private fun StudioBarIcon(icon:androidx.compose.ui.graphics.vector.ImageVector,description:String,tag:String,enabled:Boolean=true,active:Boolean=false,onClick:()->Unit) {
    IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(44.dp).testTag(tag)) {
        Icon(icon,description,tint=when{!enabled->darkTableColors.muted.copy(alpha=0.35f);active->StudioEditorRequests.chromeGold;else->darkTableColors.text})
    }
}

/** Screen chooser. Disabled entries say why (e.g. table screens need an active game) rather than silently failing. */
@Composable
internal fun StudioScreenMenu(destinations:Map<String,Pair<Boolean,()->Unit>>,current:String,onPick:(String)->Unit,onHand:(()->Unit)?,onDismiss:()->Unit) {
    StudioSheet("Edit screen",onDismiss,"studio-screen-menu") {
        destinations.forEach {(name,entry)->
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clip(RoundedCornerShape(10.dp)).clickable(enabled=entry.first){onPick(name)}
                .background(if(name==current)StudioEditorRequests.chromeGold.copy(alpha=0.18f) else Color.Transparent).padding(horizontal=12.dp).testTag("studio-screen-$name"),
                verticalAlignment=Alignment.CenterVertically) {
                Text(name,color=if(!entry.first)darkTableColors.muted.copy(alpha=0.5f) else if(name==current)StudioEditorRequests.chromeGold else darkTableColors.text,modifier=Modifier.weight(1f))
                if(!entry.first)Text(when(name){"Setup","Lobby"->"Outside a game";"Combat"->"During combat";else->"Needs a live game"},color=darkTableColors.muted,fontSize=11.sp)
            }
        }
        onHand?.let {hand->StudioListRow("Hand",current=="Hand","studio-screen-Hand"){hand()}}
    }
}

/** Collapsible layer list for the live screen: tap to select, eye to hide/show, search to filter. */
@Composable
internal fun StudioLayersPanel(runtime:StudioControlsRuntime,window:Int,onDismiss:()->Unit,modifier:Modifier=Modifier,allowedScreens:Set<String>?=null) {
    var query by remember{mutableStateOf("")}
    var allWindows by remember{mutableStateOf(false)}
    // window 0 = "whatever window is on top": used when a game dialog is open, so its controls are listed first.
    val rows=runtime.controls.values.filter {(allWindows || window==0 || it.window==window) && (allowedScreens==null || it.screen in allowedScreens) && it.title.contains(query,true)}
        .sortedWith(compareBy<StudioLiveControl>({it.screen},{-runtime.style(it.id).layer},{it.bounds.top},{it.bounds.left}))
    Surface(modifier.fillMaxHeight().widthIn(max=340.dp).fillMaxWidth(0.86f).testTag("studio-layers-panel"),color=darkTableColors.panel,
        shape=RoundedCornerShape(topStart=16.dp,bottomStart=16.dp),shadowElevation=10.dp) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Layers · ${rows.size}",color=darkTableColors.text,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f).padding(start=6.dp))
                IconButton(onClick=onDismiss,modifier=Modifier.testTag("studio-layers-close")){Icon(Icons.Default.Close,"Close layers",tint=darkTableColors.text)}
            }
            OutlinedTextField(query,{query=it},singleLine=true,label={Text("Find element")},leadingIcon={Icon(Icons.Default.Search,null)},
                modifier=Modifier.fillMaxWidth().testTag("studio-layers-search"))
            Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.heightIn(min=44.dp)) {
                Checkbox(allWindows,{allWindows=it},modifier=Modifier.testTag("studio-layers-all"))
                Text("Include open dialogs",color=darkTableColors.muted,fontSize=12.sp)
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows,key={it.id}) {c->
                    val style=runtime.style(c.id)
                    StudioListRow((if(c.isAction)"▣ " else "▤ ")+c.title.ifBlank{"Untitled"},runtime.selected==c.id,"studio-layer-row",trailing={
                        IconButton(onClick={runtime.edit(c.id,style.copy(visible=!style.visible))},modifier=Modifier.size(44.dp).testTag("studio-layer-eye")) {
                            Icon(if(style.visible)Icons.Default.Visibility else Icons.Default.VisibilityOff,if(style.visible)"Hide ${c.title}" else "Show ${c.title}",
                                tint=if(style.visible)darkTableColors.text else darkTableColors.muted)
                        }
                    }){runtime.selected=c.id}
                }
            }
        }
    }
}

/** Project-level settings that do not belong to one element. Opens on demand, so it never crowds the canvas. */
@Composable
internal fun StudioMorePanel(runtime:StudioControlsRuntime,screen:String,snap:Boolean,onSnap:(Boolean)->Unit,outlines:Boolean,onOutlines:(Boolean)->Unit,
    onApplied:()->Unit,onClassic:(()->Unit)?,onHand:(()->Unit)?,onMessage:(String)->Unit,onDismiss:()->Unit,
    hand:(@Composable ((String,()->Unit)->Unit)->Unit)?=null) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val saveFile=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) {uri->
        if(uri!=null)runCatching{context.contentResolver.openOutputStream(uri)?.use{it.write(runtime.exportProject().toByteArray(Charsets.UTF_8))} ?: error("Could not write the file.")}
            .onSuccess{onMessage("Project saved to file.")}.onFailure{onMessage(it.message ?: "Could not save.")}
    }
    val openFile=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) {uri->
        if(uri!=null)runCatching{
            val text=context.contentResolver.openInputStream(uri)?.bufferedReader()?.use{r->
                val buffer=CharArray(5_000_001);var n=0
                while(n<buffer.size){val k=r.read(buffer,n,buffer.size-n);if(k<0)break;n+=k}
                require(n<=5_000_000){"Project is too large."};String(buffer,0,n)} ?: error("Could not open the file.")
            studioImport(runtime,text)
        }.onSuccess{onMessage("Imported into the draft. Review, then Apply.")}.onFailure{onMessage(it.message ?: "Import failed.")}
    }
    val clipboard=LocalClipboardManager.current
    var profileName by remember{mutableStateOf("")}
    var json by remember{mutableStateOf<String?>(null)}
    var confirm by remember{mutableStateOf<Pair<String,()->Unit>?>(null)}
    var page by remember{mutableStateOf(if(hand!=null)"Hand" else "Layout")}
    val pages=listOfNotNull("Hand".takeIf{hand!=null},"Layout","Palette","Project")
    StudioSheet("Studio options",onDismiss,"studio-more-panel") {
        // Three short pages instead of one long form.
        Row(Modifier.fillMaxWidth()) {pages.forEach {p->
            Box(Modifier.weight(1f).heightIn(min=44.dp).clip(RoundedCornerShape(10.dp)).background(if(p==page)darkTableColors.raised else Color.Transparent)
                .clickable{page=p}.testTag("studio-more-tab-$p"),contentAlignment=Alignment.Center) {
                Text(p,color=if(p==page)StudioEditorRequests.chromeGold else darkTableColors.muted,fontSize=13.sp)
            }
        }}
        if(page=="Hand")hand?.invoke{q,a->confirm=q to a}
        if(page=="Layout") {
        StudioSection("Layout profile")
        Row(Modifier.hintedHorizontalScroll()) {
            StudioProfiles(runtime.prefs,runtime.orientation).names().forEach {name->
                StudioChip(name,true,"studio-profile-$name",selected=name==runtime.profile){runCatching{runtime.chooseProfile(name)}.onFailure{onMessage(it.message ?: "Cannot open profile.")}}
            }
        }
        Row(verticalAlignment=Alignment.CenterVertically) {
            OutlinedTextField(profileName,{profileName=it.take(32)},singleLine=true,label={Text("Save as new profile")},modifier=Modifier.weight(1f).testTag("studio-profile-name"))
            TextButton(onClick={runCatching{runtime.copyProfile(profileName.trim())}.onSuccess{profileName=""}.onFailure{onMessage(it.message ?: "Cannot copy profile.")}},
                enabled=profileName.isNotBlank(),modifier=Modifier.heightIn(min=48.dp).testTag("studio-profile-copy")){Text("Save")}
        }
        StudioSection("Show on screen")
        SCREEN_PIECES.forEach {(key,name)->
            val shown=key !in runtime.draft.hiddenPieces
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(name,color=darkTableColors.text,fontSize=13.sp,modifier=Modifier.weight(1f))
                Switch(shown,{on->runtime.change(runtime.draft.copy(hiddenPieces=if(on)runtime.draft.hiddenPieces-key else runtime.draft.hiddenPieces+key))},
                    modifier=Modifier.testTag("studio-piece-$key"))
            }
        }
        StudioSection("Turn label")
        Row(Modifier.hintedHorizontalScroll()) {
            listOf("both" to "R2 · T3","round" to "Round 2","turn" to "Turn 3","hidden" to "Hidden").forEach {(mode,text)->
                StudioChip(text,true,"studio-turn-label-$mode",selected=runtime.draft.turnLabel==mode){runtime.change(runtime.draft.copy(turnLabel=mode))}
            }
        }
        StudioSection("Information on $screen")
        val current=runtime.draft.screenDensity[screen] ?: runtime.draft.density
        Row {listOf("Minimal","Balanced","Detailed").forEach {v->StudioChip(v,true,"studio-density-$v",selected=v==current){runtime.density(screen,v)}}}
        Row {listOf("Minimal","Balanced","Detailed").forEach {v->StudioChip("All: $v",true,"studio-density-all-$v",selected=v==runtime.draft.density && runtime.draft.screenDensity.isEmpty()){runtime.density(null,v)}}}
        StudioSection("Editing")
        StudioToggleRow("Snap to 4dp grid",snap,"studio-snap",onSnap)
        StudioToggleRow("Outline editable elements",outlines,"studio-outlines",onOutlines)
        }
        if(page=="Palette") {
        StudioSection("Palette")
        Row(Modifier.hintedHorizontalScroll()) {
            StudioChip("Table default",runtime.draft.theme.isNotEmpty(),"studio-palette-default",selected=runtime.draft.theme.isEmpty()){runtime.change(runtime.draft.copy(theme=emptyMap()))}
            tablePalettePresets.forEach {preset->
                val presetTheme=StudioLayout.themeRoles.associateWith{role->tableColorHex(preset.colors.colorRole(if(role=="gold")"counter" else role))}
                StudioChip(preset.name,true,"studio-palette-${preset.name}",selected=runtime.draft.theme==presetTheme){
                runtime.change(runtime.draft.copy(theme=StudioLayout.themeRoles.associateWith{role->tableColorHex(preset.colors.colorRole(if(role=="gold")"counter" else role))}))
            }}
        }
        val base=runtime.draft.colors(darkTableColors)
        val swatches=listOf("Surface" to base.panel,"Raised" to base.raised,"Accent" to base.accent,"Text" to base.text,"Gold" to base.counter)
        StudioLayout.themeRoles.forEach {role->
            StudioColorField(role.replaceFirstChar{it.uppercase()},runtime.draft.theme[role],swatches,{v->
                runtime.change(runtime.draft.copy(theme=if(v==null)runtime.draft.theme-role else runtime.draft.theme+(role to v)))},"studio-theme-$role")
        }
        }
        if(page=="Project") {
        StudioSection("Project")
        Row(Modifier.hintedHorizontalScroll()) {
            StudioChip("Copy / paste",true,"studio-export"){json=runtime.exportProject()}
            StudioChip("Save file",true,"studio-save-file"){saveFile.launch("Arcana-Studio-${runtime.orientation}-${runtime.profile}.json")}
            StudioChip("Open file",true,"studio-open-file"){openFile.launch(arrayOf("application/json","text/plain"))}
            onHand?.let{StudioChip("Edit hand",true,"studio-more-hand"){it()}}
            onClassic?.let{StudioChip("Classic panel",true,"studio-classic",onClick=it)}
        }
        // Owner, Nov 7: Studio's own log, separate from Arcana's debug logs.
        StudioSection("Studio log")
        Row(Modifier.hintedHorizontalScroll()) {
            StudioChip("Share Studio log",true,"studio-log-share"){
                val text=StudioLog.text().ifBlank{"The Studio log is empty."}
                runCatching{context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply{
                    type="text/plain";putExtra(android.content.Intent.EXTRA_SUBJECT,"Arcana Studio log");putExtra(android.content.Intent.EXTRA_TEXT,text)},"Share Studio log"))}
                    .onFailure{onMessage("Couldn't open sharing on this phone.")}
            }
            StudioChip("Clear Studio log",true,"studio-log-clear"){confirm="Clear the Studio log?" to {StudioLog.clear()}}
        }
        StudioSection("Reset")
        Row(Modifier.hintedHorizontalScroll()) {
            StudioChip("Reset $screen",true,"studio-reset-screen"){confirm="Reset every change on $screen?" to {runtime.resetScreen(screen)}}
            StudioChip("Reset profile",true,"studio-reset-profile"){confirm="Reset the ${runtime.profile} profile to native layouts? This is saved immediately." to {runtime.resetProfile();onApplied()}}
            StudioChip("Reset all Studio",true,"studio-reset-all"){confirm="Remove every Studio profile and layout? This is saved immediately." to {
                StudioProfiles(runtime.prefs,runtime.orientation).resetAll();runtime.cancel();onApplied()}}
        }
        }
    }
    // Projects can be large (hand layout + every control), so transfer goes through the clipboard instead of an
    // on-screen text field that would have to lay out the whole document.
    json?.let {project->
        var status by remember(project){mutableStateOf("Project for ${runtime.profile} · ${runtime.orientation} · ${project.length/1024+1} KB")}
        AlertDialog(onDismissRequest={json=null},title={Text("Export / import")},text={Column {
            Text(status,modifier=Modifier.testTag("studio-json-status"))
            Text("Import replaces the current draft; nothing reaches the game until you Apply.",color=darkTableColors.muted,fontSize=12.sp)
        }},confirmButton={TextButton(onClick={
            val text=clipboard.getText()?.text.orEmpty()
            runCatching{studioImport(runtime,text)}.onSuccess{json=null;onMessage("Imported into the draft. Review, then Apply.")}
                .onFailure{status=it.message ?: "The clipboard does not hold an Arcana Studio project."}
        },modifier=Modifier.heightIn(min=48.dp).testTag("studio-json-import")){Text("Import from clipboard")}},
            dismissButton={TextButton(onClick={clipboard.setText(AnnotatedString(project));status="Copied ${project.length/1024+1} KB to the clipboard."},
                modifier=Modifier.heightIn(min=48.dp).testTag("studio-json-copy")){Text("Copy project")}})
    }
    confirm?.let {(question,action)->
        AlertDialog(onDismissRequest={confirm=null},text={Text(question)},
            confirmButton={TextButton(onClick={confirm=null;action()},modifier=Modifier.testTag("studio-confirm")){Text("Reset")}},
            dismissButton={TextButton(onClick={confirm=null}){Text("Keep")}})
    }
}

@Composable
private fun StudioSection(title:String){Text(title,color=StudioEditorRequests.chromeGold,fontSize=12.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=10.dp,bottom=2.dp))}

@Composable
private fun StudioToggleRow(label:String,value:Boolean,tag:String,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(label,color=darkTableColors.text,modifier=Modifier.weight(1f));Switch(value,onChange,modifier=Modifier.testTag(tag))
    }
}

/** Bottom sheet surface used for the screen menu and options; tapping the scrim closes it. */
@Composable
internal fun StudioSheet(title:String,onDismiss:()->Unit,tag:String,content:@Composable ColumnScope.()->Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.35f)).clickable(interactionSource=null,indication=null,onClick=onDismiss)) {
        Surface(Modifier.align(Alignment.BottomCenter).widthIn(max=640.dp).fillMaxWidth().fillMaxHeight(0.72f).clickable(interactionSource=null,indication=null){}.testTag(tag),
            color=darkTableColors.panel,shape=RoundedCornerShape(topStart=20.dp,topEnd=20.dp),shadowElevation=12.dp) {
            Column(Modifier.navigationBarsPadding().padding(horizontal=12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(title,color=darkTableColors.text,fontWeight=FontWeight.SemiBold,fontSize=17.sp,modifier=Modifier.weight(1f))
                    IconButton(onClick=onDismiss,modifier=Modifier.testTag("$tag-close")){Icon(Icons.Default.Close,"Close",tint=darkTableColors.text)}
                }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom=12.dp),content=content)
            }
        }
    }
}
