package com.arcanatable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

@Composable
internal fun StudioFilterChip(site:String,screen:String,selected:Boolean,onClick:()->Unit,label:@Composable ()->Unit,
    modifier:Modifier=Modifier,enabled:Boolean=true,leadingIcon:(@Composable ()->Unit)?=null,trailingIcon:(@Composable ()->Unit)?=null,
    shape:Shape=FilterChipDefaults.shape,colors:SelectableChipColors=FilterChipDefaults.filterChipColors(),
    elevation:SelectableChipElevation?=FilterChipDefaults.filterChipElevation(),border:BorderStroke?=FilterChipDefaults.filterChipBorder(enabled,selected),
    interactionSource:MutableInteractionSource?=null) {
    val w=studioWidget(site,screen,"action","Filter",enabled,onClick=onClick)
    if(w?.style?.visible==false){StudioNativeCopies(w);return}
    CompositionLocalProvider(LocalStudioControlParent provides (w?.parent() ?: LocalStudioControlParent.current)) {
        val c=colors.let {original->w?.style?.fill?.let(::parseTableColorHex)?.let{original.copy(containerColor=it,selectedContainerColor=it)} ?: original}
            .let {original->w?.style?.ink?.let(::parseTableColorHex)?.let{original.copy(labelColor=it,selectedLabelColor=it)} ?: original}
        FilterChip(selected,{w?.click() ?: onClick()},label,modifier.studioWidget(w),w?.let{it.runtime.editing || it.enabled} ?: enabled,
            w?.style?.icon?.let{StudioLayout.icons[it]}?.takeIf{it.isNotEmpty()}?.let{glyph->({Text(glyph)})} ?: leadingIcon,
            trailingIcon,w?.style?.radius?.let{RoundedCornerShape(it.coerceIn(0f,100f).dp)} ?: shape,c,elevation,border,interactionSource)
    }
    StudioNativeCopies(w)
}

@Composable
internal fun StudioAssistChip(site:String,screen:String,onClick:()->Unit,label:@Composable ()->Unit,modifier:Modifier=Modifier,
    enabled:Boolean=true,leadingIcon:(@Composable ()->Unit)?=null,trailingIcon:(@Composable ()->Unit)?=null,
    shape:Shape=AssistChipDefaults.shape,colors:ChipColors=AssistChipDefaults.assistChipColors(),elevation:ChipElevation?=AssistChipDefaults.assistChipElevation(),
    border:BorderStroke?=AssistChipDefaults.assistChipBorder(enabled),interactionSource:MutableInteractionSource?=null) {
    val w=studioWidget(site,screen,"action","Action chip",enabled,onClick=onClick)
    if(w?.style?.visible==false){StudioNativeCopies(w);return}
    CompositionLocalProvider(LocalStudioControlParent provides (w?.parent() ?: LocalStudioControlParent.current)) {
        val c=if(w==null)colors else colors.copy(containerColor=w.style.fill?.let(::parseTableColorHex) ?: colors.containerColor,
            labelColor=w.style.ink?.let(::parseTableColorHex) ?: colors.labelColor)
        AssistChip({w?.click() ?: onClick()},label,modifier.studioWidget(w),w?.let{it.runtime.editing || it.enabled} ?: enabled,
            w?.style?.icon?.let{StudioLayout.icons[it]}?.takeIf{it.isNotEmpty()}?.let{glyph->({Text(glyph)})} ?: leadingIcon,
            trailingIcon,w?.style?.radius?.let{RoundedCornerShape(it.coerceIn(0f,100f).dp)} ?: shape,c,elevation,border,interactionSource)
    }
    StudioNativeCopies(w)
}

@Composable
internal fun StudioTab(site:String,screen:String,selected:Boolean,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    text:(@Composable ()->Unit)?=null,icon:(@Composable ()->Unit)?=null,selectedContentColor:Color=LocalContentColor.current,
    unselectedContentColor:Color=selectedContentColor,interactionSource:MutableInteractionSource?=null) {
    val w=studioWidget(site,screen,"action","Tab",enabled,onClick=onClick)
    CompositionLocalProvider(LocalStudioControlParent provides (w?.parent() ?: LocalStudioControlParent.current)) {
        Tab(selected,{w?.click() ?: onClick()},modifier.studioWidget(w),w?.let{it.runtime.editing || it.enabled} ?: enabled,
            text,icon,w?.style?.ink?.let(::parseTableColorHex) ?: selectedContentColor,
            w?.style?.ink?.let(::parseTableColorHex) ?: unselectedContentColor,interactionSource)
    }
    StudioNativeCopies(w)
}
