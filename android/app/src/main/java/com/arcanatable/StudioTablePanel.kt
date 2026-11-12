package com.arcanatable

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owner notes 16/17: tapping empty table space in Studio picks the table itself. Its panel sets a background picture of
 * the owner's own (Fill or Fit, dimmed so cards stay readable) and can match the table's colours to the picture.
 */
@Composable
internal fun StudioTableInspector(runtime:StudioControlsRuntime,collapsed:Boolean,onCollapse:(Boolean)->Unit,onMessage:(String)->Unit,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val project=runtime.draft
    var shuffle by remember{mutableIntStateOf(0)}
    var busy by remember{mutableStateOf(false)}
    val pick=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {uri->
        if(uri!=null){busy=true;scope.launch {
            runCatching{withContext(Dispatchers.IO){StudioBackdrop.import(context,uri)}}
                .onSuccess{name->runtime.change(runtime.draft.copy(backdrop=name))}
                .onFailure{onMessage(it.message ?: "Couldn't use that picture.")}
            busy=false
        }}
    }
    fun match(next:Int) {
        val name=project.backdrop ?: return
        busy=true
        scope.launch {
            runCatching{withContext(Dispatchers.Default){StudioBackdrop.samplePixels(context,name)?.let{StudioPictureColors.theme(it,next)}}}
                .onSuccess{theme->if(theme!=null){shuffle=next;runtime.change(runtime.draft.copy(theme=theme))} else onMessage("Couldn't read that picture.")}
                .onFailure{onMessage("Couldn't read that picture.")}
            busy=false
        }
    }
    Surface(modifier.fillMaxWidth().testTag("studio-table-inspector"),color=darkTableColors.panel,shape=RoundedCornerShape(16.dp),shadowElevation=8.dp) {
        Column(Modifier.padding(horizontal=8.dp,vertical=4.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start=4.dp)) {
                    Text("Table",color=darkTableColors.text,fontSize=15.sp)
                    Text(if(project.backdrop==null)"Background: table colour" else "Background: your picture",color=darkTableColors.muted,fontSize=11.sp)
                }
                if(busy)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp,color=StudioEditorRequests.chromeGold)
                IconButton(onClick={onCollapse(!collapsed)}){Icon(if(collapsed)Icons.Default.ExpandLess else Icons.Default.ExpandMore,if(collapsed)"Expand" else "Collapse",tint=darkTableColors.text)}
                IconButton(onClick={runtime.tablePicked=false},modifier=Modifier.testTag("studio-table-close")){Icon(Icons.Default.Close,"Deselect table",tint=darkTableColors.text)}
            }
            if(collapsed)return@Column
            Row(Modifier.hintedHorizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
                StudioChip(if(project.backdrop==null)"Choose picture" else "Change picture",!busy,"studio-backdrop-pick") {
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if(project.backdrop!=null)StudioChip("Remove",!busy,"studio-backdrop-remove"){runtime.change(runtime.draft.copy(backdrop=null))}
            }
            if(project.backdrop!=null) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Picture",color=darkTableColors.muted,fontSize=12.sp,modifier=Modifier.padding(end=6.dp))
                    StudioBackdrop.FITS.forEach{f->StudioChip(if(f=="Fill")"Fill screen" else "Whole picture",true,"studio-backdrop-$f",selected=project.backdropFit==f){runtime.change(runtime.draft.copy(backdropFit=f))}}
                }
                StudioStepper("Dim","${(project.backdropDim*100).toInt()}%","studio-backdrop-dim",
                    {runtime.change(runtime.draft.copy(backdropDim=((runtime.draft.backdropDim-0.1f)*10).let{kotlin.math.round(it)/10f}.coerceIn(0f,0.9f)))},
                    {runtime.change(runtime.draft.copy(backdropDim=((runtime.draft.backdropDim+0.1f)*10).let{kotlin.math.round(it)/10f}.coerceIn(0f,0.9f)))})
                Row(Modifier.hintedHorizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
                    StudioChip("Match picture",!busy,"studio-backdrop-match"){match(0)}
                    StudioChip("Shuffle",!busy,"studio-backdrop-shuffle"){match(shuffle+1)}
                    StudioChip("Table default",project.theme.isNotEmpty(),"studio-backdrop-default-colours"){runtime.change(runtime.draft.copy(theme=emptyMap()))}
                }
            }
        }
    }
}
