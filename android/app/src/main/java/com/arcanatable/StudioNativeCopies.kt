package com.arcanatable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.compose.foundation.shape.RoundedCornerShape

/** Duplicates stay in the source control's real Compose window, including native dialogs. */
@Composable
internal fun StudioNativeCopies(source:StudioWidget?,action:Boolean=true) {
    if(source==null)return
    val copies=source.runtime.project().copies.filter{it.source==source.id && it.style.visible && !source.runtime.isRecovered(it.id)}
    if(copies.isEmpty())return
    val density=LocalDensity.current
    copies.forEach {copy->
        val runtime=source.runtime
        val binding=copy.style.binding ?: copy.source
        val target=runtime.controls[binding]
        val title=if(action)copy.style.label?.let {"$it · ${target?.title.orEmpty()}"} ?: target?.title ?: source.title.value
            else runtime.text(binding)
        val available=if(action)runtime.canInvoke(source.id,binding) else runtime.canDisplay(source.id,binding)
        var anchor by remember(copy.id){mutableStateOf(Offset.Zero)}
        val callback by rememberUpdatedState<()->Unit>({runtime.invokeFrom(source.id,binding)})
        SideEffect {runtime.record(StudioLiveControl(copy.id,target?.screen.orEmpty(),if(action)"action" else "data",title.take(100),available,protectedLabel=!action),if(action)({callback()}) else null)}
        DisposableEffect(copy.id){onDispose{runtime.remove(copy.id)}}
        val widget=StudioWidget(runtime,copy.id,copy.style.copy(dx=0f,dy=0f),available,
            remember(copy.id){mutableStateOf(title)},remember(copy.id){mutableStateOf(null)})
        Layout(modifier=Modifier.onGloballyPositioned {anchor=it.positionInRoot()},content={
            if(!action) Text(title,modifier=Modifier.testTag("studio-copy-${copy.id}")
                .requiredSize((copy.style.width ?: 96f).coerceIn(44f,2000f).dp,(copy.style.height ?: 48f).coerceIn(44f,4000f).dp)
                .studioWidget(widget).clickable(enabled=runtime.editing){runtime.select(copy.id)},
                color=copy.style.ink?.let(::parseTableColorHex) ?: SoftWhite,fontSize=(copy.style.font ?: 11f).coerceIn(8f,64f).sp)
            else Button(onClick={if(runtime.editing)runtime.select(copy.id) else runtime.invokeFrom(source.id,binding)},
                enabled=runtime.editing || runtime.canInvoke(source.id,binding),
                modifier=Modifier.testTag("studio-copy-${copy.id}").requiredSize((copy.style.width ?: 96f).coerceIn(44f,2000f).dp,(copy.style.height ?: 48f).coerceIn(44f,4000f).dp).studioWidget(widget),
                shape=RoundedCornerShape((copy.style.radius ?: 10f).coerceIn(0f,100f).dp),
                colors=ButtonDefaults.buttonColors(containerColor=copy.style.fill?.let(::parseTableColorHex) ?: PanelLight,
                    contentColor=copy.style.ink?.let(::parseTableColorHex) ?: SoftWhite)) {
                Text(listOfNotNull(StudioLayout.icons[copy.style.icon]?.takeIf{it.isNotEmpty()},title).joinToString(" "),fontSize=(copy.style.font ?: 11f).coerceIn(8f,64f).sp)
            }
        }){measurables,_->
            val placeable=measurables.single().measure(Constraints())
            layout(0,0){placeable.place((with(density){copy.style.dx.dp.toPx()}-anchor.x).toInt(),
                (with(density){copy.style.dy.dp.toPx()}-anchor.y).toInt())}
        }
    }
}
