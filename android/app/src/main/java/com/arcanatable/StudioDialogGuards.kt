package com.arcanatable

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Back/outside taps inspect the live window rather than canceling a game decision. */
@Composable
internal fun StudioDialog(screen:String="Dialog",onDismissRequest:()->Unit,properties:DialogProperties=DialogProperties(),content:@Composable ()->Unit) {
    val runtime=LocalStudioControls.current
    Dialog(onDismissRequest={if(runtime?.editing==true){runtime.panelOpen=true;StudioEditorRequests.dismiss(onDismissRequest)} else onDismissRequest()},properties=properties) {StudioScreen(screen) {
        if(runtime?.active==true)Box {
            Box(Modifier.padding(bottom=48.dp)){content()}
            StudioWindowEscape(runtime,Modifier.align(Alignment.BottomEnd))
        } else content()
    }}
}

@Composable
internal fun StudioAlertDialog(screen:String="Dialog",onDismissRequest:()->Unit,confirmButton:@Composable ()->Unit,modifier:Modifier=Modifier,
    dismissButton:(@Composable ()->Unit)?=null,icon:(@Composable ()->Unit)?=null,title:(@Composable ()->Unit)?=null,
    text:(@Composable ()->Unit)?=null,shape:Shape=AlertDialogDefaults.shape,containerColor:Color=AlertDialogDefaults.containerColor,
    iconContentColor:Color=AlertDialogDefaults.iconContentColor,titleContentColor:Color=AlertDialogDefaults.titleContentColor,
    textContentColor:Color=AlertDialogDefaults.textContentColor,tonalElevation:Dp=AlertDialogDefaults.TonalElevation,
    properties:DialogProperties=DialogProperties()) {
    val runtime=LocalStudioControls.current
    StudioScreen(screen) {
        AlertDialog(onDismissRequest={if(runtime?.editing==true){runtime.panelOpen=true;StudioEditorRequests.dismiss(onDismissRequest)} else onDismissRequest()},confirmButton={
            if(runtime?.active==true)Column(horizontalAlignment=Alignment.End){confirmButton();StudioWindowEscape(runtime)}
            else confirmButton()
        },
            modifier=modifier,dismissButton=dismissButton,icon=icon,title=title,text=text,shape=shape,containerColor=containerColor,
            iconContentColor=iconContentColor,titleContentColor=titleContentColor,textContentColor=textContentColor,tonalElevation=tonalElevation,properties=properties)
    }
}

/** This recovery control is outside the editable project and cannot be hidden or rebound. */
@Composable
private fun StudioWindowEscape(runtime:StudioControlsRuntime,modifier:Modifier=Modifier) {
    androidx.compose.material3.TextButton(onClick={if(runtime.editing){runtime.panelOpen=true;StudioEditorRequests.openMenu()} else runtime.begin()},
        colors=androidx.compose.material3.ButtonDefaults.textButtonColors(containerColor=darkTableColors.panel,contentColor=darkTableColors.text),
        modifier=modifier.height(48.dp).testTag("studio-window-escape")) {
        androidx.compose.material3.Text("Arcana Studio",color=darkTableColors.text)
    }
}
