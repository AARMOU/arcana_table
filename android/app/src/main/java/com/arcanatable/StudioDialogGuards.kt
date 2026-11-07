package com.arcanatable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Game pop-ups. In normal play they are real dialog windows, unchanged. While the Studio editor is open they are drawn
 * inside the main screen instead (owner plan item 17), so the editor's canvas, outlines and handles work on them exactly
 * as on full screens, and the toolbar's "‹ Back" closes them through their own [onDismissRequest].
 */
@Composable
internal fun StudioDialog(screen:String="Dialog",onDismissRequest:()->Unit,properties:DialogProperties=DialogProperties(),content:@Composable ()->Unit) {
    if(studioInline()) {
        StudioInlinePopup(screen,onDismissRequest,fullWidth=!properties.usePlatformDefaultWidth) {content()}
        return
    }
    Dialog(onDismissRequest=onDismissRequest,properties=properties) {StudioScreen(screen){content()}}
}

@Composable
internal fun StudioAlertDialog(screen:String="Dialog",onDismissRequest:()->Unit,confirmButton:@Composable ()->Unit,modifier:Modifier=Modifier,
    dismissButton:(@Composable ()->Unit)?=null,icon:(@Composable ()->Unit)?=null,title:(@Composable ()->Unit)?=null,
    text:(@Composable ()->Unit)?=null,shape:Shape=AlertDialogDefaults.shape,containerColor:Color=AlertDialogDefaults.containerColor,
    iconContentColor:Color=AlertDialogDefaults.iconContentColor,titleContentColor:Color=AlertDialogDefaults.titleContentColor,
    textContentColor:Color=AlertDialogDefaults.textContentColor,tonalElevation:Dp=AlertDialogDefaults.TonalElevation,
    properties:DialogProperties=DialogProperties()) {
    if(studioInline()) {
        // Same structure and spacing as Material 3's AlertDialog content, drawn in place.
        StudioInlinePopup(screen,onDismissRequest,fullWidth=false) {
            Surface(modifier.widthIn(min=280.dp,max=560.dp),shape=shape,color=containerColor,tonalElevation=tonalElevation) {
                Column(Modifier.padding(24.dp)) {
                    icon?.let{CompositionLocalProvider(LocalContentColor provides iconContentColor){Box(Modifier.padding(bottom=16.dp).align(Alignment.CenterHorizontally)){it()}}}
                    title?.let{CompositionLocalProvider(LocalContentColor provides titleContentColor){ProvideTextStyle(MaterialTheme.typography.headlineSmall){
                        Box(Modifier.padding(bottom=16.dp).align(if(icon==null)Alignment.Start else Alignment.CenterHorizontally)){it()}}}}
                    text?.let{CompositionLocalProvider(LocalContentColor provides textContentColor){ProvideTextStyle(MaterialTheme.typography.bodyMedium){
                        Box(Modifier.weight(1f,fill=false).padding(bottom=24.dp).align(Alignment.Start)){it()}}}}
                    Row(Modifier.align(Alignment.End),horizontalArrangement=Arrangement.spacedBy(8.dp)){dismissButton?.invoke();confirmButton()}
                }
            }
        }
        return
    }
    StudioScreen(screen) {
        AlertDialog(onDismissRequest=onDismissRequest,confirmButton=confirmButton,
            modifier=modifier,dismissButton=dismissButton,icon=icon,title=title,text=text,shape=shape,containerColor=containerColor,
            iconContentColor=iconContentColor,titleContentColor=titleContentColor,textContentColor=textContentColor,tonalElevation=tonalElevation,properties=properties)
    }
}

/** Pop-ups are drawn in place only while the editor shell is open, and never on screens Studio doesn't edit. */
@Composable
private fun studioInline():Boolean {
    val runtime=LocalStudioControls.current
    return runtime?.editing==true && StudioEditorRequests.shellActive && !LocalStudioExcluded.current
}

/**
 * Registers the pop-up with the editor (for "‹ Back" and so only its own controls are selectable) and hands its content to
 * the editor's pop-up layer at the root of the main screen, under the editing canvas. A dimmed scrim and the same centring
 * as a dialog window keep it looking like it will in play.
 */
@Composable
private fun StudioInlinePopup(screen:String,onDismissRequest:()->Unit,fullWidth:Boolean,content:@Composable ()->Unit) {
    val key=remember{Any()}
    val dismiss by rememberUpdatedState(onDismissRequest)
    val body by rememberUpdatedState(content)
    val entry=remember(key){StudioPopupEntry(key,screen,{dismiss()}) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.6f))
            .clickable(interactionSource=remember{MutableInteractionSource()},indication=null){}.testTag("studio-inline-popup-$screen"),
            contentAlignment=Alignment.Center) {
            Box(if(fullWidth)Modifier.fillMaxSize() else Modifier.padding(horizontal=24.dp,vertical=48.dp)) {StudioScreen(screen){body()}}
        }
    }}
    DisposableEffect(entry){StudioEditorRequests.popups.add(entry);onDispose{StudioEditorRequests.popups.remove(entry)}}
}

internal class StudioPopupEntry(val key:Any,val screen:String,val dismiss:()->Unit,val content:@Composable ()->Unit)
