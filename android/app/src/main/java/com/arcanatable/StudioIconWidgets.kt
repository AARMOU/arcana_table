package com.arcanatable

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
internal fun StudioIcon(site:String,screen:String,imageVector:ImageVector,contentDescription:String?,modifier:Modifier=Modifier,tint:Color=LocalContentColor.current) {
    val parent=LocalStudioControlParent.current
    val w=if(parent==null)studioWidget(site,screen,"icon",contentDescription ?: "Icon") else null
    val style=parent?.style ?: w?.style
    val key="$site/$currentCompositeKeyHash"
    if(parent!=null && contentDescription!=null)SideEffect{parent.report(key,contentDescription)}
    if(style?.visible==false)return
    Icon(imageVector,parent?.boundCaption ?: style?.label ?: contentDescription,modifier.studioWidget(w),style?.ink?.let(::parseTableColorHex) ?: tint)
}
@Composable
internal fun StudioIcon(site:String,screen:String,painter:Painter,contentDescription:String?,modifier:Modifier=Modifier,tint:Color=LocalContentColor.current) {
    val parent=LocalStudioControlParent.current
    val w=if(parent==null)studioWidget(site,screen,"icon",contentDescription ?: "Icon") else null
    val style=parent?.style ?: w?.style
    val key="$site/$currentCompositeKeyHash"
    if(parent!=null && contentDescription!=null)SideEffect{parent.report(key,contentDescription)}
    if(style?.visible==false)return
    Icon(painter,parent?.boundCaption ?: style?.label ?: contentDescription,modifier.studioWidget(w),style?.ink?.let(::parseTableColorHex) ?: tint)
}
@Composable
internal fun StudioIcon(site:String,screen:String,bitmap:ImageBitmap,contentDescription:String?,modifier:Modifier=Modifier,tint:Color=LocalContentColor.current) {
    val parent=LocalStudioControlParent.current
    val w=if(parent==null)studioWidget(site,screen,"icon",contentDescription ?: "Icon") else null
    val style=parent?.style ?: w?.style
    if(style?.visible==false)return
    Icon(bitmap,style?.label ?: contentDescription,modifier.studioWidget(w),style?.ink?.let(::parseTableColorHex) ?: tint)
}
