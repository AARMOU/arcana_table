package com.arcanatable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

internal class StudioControlMarker(val widget:StudioWidget):Modifier.Element
internal fun Modifier.studioMarker():StudioWidget?=foldIn<StudioWidget?>(null){found,element->if(element is StudioControlMarker)element.widget else found}

@Composable
internal fun Modifier.studioClickable(site:String,screen:String,enabled:Boolean=true,onClickLabel:String?=null,role:Role?=null,onClick:()->Unit):Modifier {
    val w=studioWidget(site,screen,"container-action",onClickLabel ?: "Control",enabled,onClick=onClick)
    StudioNativeCopies(w)
    return studioWidget(w).clickable(enabled=w?.let{it.runtime.editing || it.enabled} ?: enabled,
        onClickLabel=w?.style?.label ?: onClickLabel,role=role,onClick={w?.click() ?: onClick()})
        .then(w?.let{StudioControlMarker(it)} ?: Modifier)
}

@Composable
internal fun Modifier.studioClickable(site:String,screen:String,interactionSource:MutableInteractionSource?,indication:Indication?,
    enabled:Boolean=true,onClickLabel:String?=null,role:Role?=null,onClick:()->Unit):Modifier {
    val w=studioWidget(site,screen,"container-action",onClickLabel ?: "Control",enabled,onClick=onClick)
    StudioNativeCopies(w)
    return studioWidget(w).clickable(interactionSource=interactionSource,indication=indication,
        enabled=w?.let{it.runtime.editing || it.enabled} ?: enabled,onClickLabel=w?.style?.label ?: onClickLabel,role=role,
        onClick={w?.click() ?: onClick()}).then(w?.let{StudioControlMarker(it)} ?: Modifier)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.studioCombinedClickable(site:String,screen:String,enabled:Boolean=true,onClickLabel:String?=null,role:Role?=null,
    onLongClickLabel:String?=null,onLongClick:(()->Unit)?=null,onDoubleClick:(()->Unit)?=null,hapticFeedbackEnabled:Boolean=true,onClick:()->Unit):Modifier {
    val w=studioWidget(site,screen,"container-action",onClickLabel ?: "Control",enabled,onClick=onClick)
    StudioNativeCopies(w)
    return studioWidget(w).combinedClickable(enabled=w?.let{it.runtime.editing || it.enabled} ?: enabled,
        onClickLabel=w?.style?.label ?: onClickLabel,role=role,onLongClickLabel=onLongClickLabel,
        onLongClick=if(w?.runtime?.editing==true)null else onLongClick,
        onDoubleClick=if(w?.runtime?.editing==true)null else onDoubleClick,hapticFeedbackEnabled=hapticFeedbackEnabled,
        onClick={w?.click() ?: onClick()}).then(w?.let{StudioControlMarker(it)} ?: Modifier)
}
