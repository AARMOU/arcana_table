package com.arcanatable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Convert between #RRGGBB and HSV so the custom picker and hex entry stay in sync. */
internal object StudioColorMath {
    fun hsv(hex:String?):FloatArray {
        val c=hex?.let(::parseTableColorHex) ?: return floatArrayOf(150f,0.4f,0.9f)
        val out=FloatArray(3);android.graphics.Color.colorToHSV(c.toArgb(),out);return out
    }
    fun hex(h:Float,s:Float,v:Float):String=tableColorHex(Color(android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0f,360f),s.coerceIn(0f,1f),v.coerceIn(0f,1f)))))
}

/**
 * One-row colour field: "Auto" (inherit native colour), the project's palette roles, recent colours and a custom picker.
 * Swatches are 40dp inside 48dp targets so they are usable on the Fold cover screen.
 */
@Composable
internal fun StudioColorField(label:String,value:String?,palette:List<Pair<String,Color>>,onChange:(String?)->Unit,tag:String) {
    var custom by remember(tag){mutableStateOf(false)}
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(label,color=darkTableColors.muted,fontSize=12.sp,modifier=Modifier.width(56.dp))
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
                StudioSwatch(null,value==null,"Auto $label",Modifier.testTag("$tag-auto")){onChange(null)}
                palette.forEach {(name,color)->val hex=tableColorHex(color)
                    StudioSwatch(color,value.equals(hex,true),"$name $label",Modifier.testTag("$tag-$name")){onChange(hex)}}
                Box(Modifier.size(48.dp).clickable{custom=!custom}.testTag("$tag-custom").semantics{contentDescription="Custom $label"},contentAlignment=Alignment.Center) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Brush.sweepGradient(listOf(Color.Red,Color.Yellow,Color.Green,Color.Cyan,Color.Blue,Color.Magenta,Color.Red)))
                        .border(2.dp,if(custom)darkTableColors.accent else Color.Transparent,CircleShape))
                }
            }
        }
        if(custom)StudioCustomColor(value,onChange,tag)
    }
}

@Composable
private fun StudioSwatch(color:Color?,selected:Boolean,description:String,modifier:Modifier,onClick:()->Unit) {
    Box(modifier.size(48.dp).clickable(onClick=onClick).semantics{contentDescription=description},contentAlignment=Alignment.Center) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(color ?: darkTableColors.raised)
            .border(if(selected)3.dp else 1.dp,if(selected)darkTableColors.accent else darkTableColors.muted.copy(alpha=0.5f),CircleShape),
            contentAlignment=Alignment.Center) {
            if(color==null)Text("A",color=darkTableColors.text,fontSize=13.sp)
        }
    }
}

@Composable
private fun StudioCustomColor(value:String?,onChange:(String)->Unit,tag:String) {
    val start=remember(value){StudioColorMath.hsv(value)}
    var h by remember(value){mutableFloatStateOf(start[0])}
    var s by remember(value){mutableFloatStateOf(start[1])}
    var v by remember(value){mutableFloatStateOf(start[2])}
    var field by remember(value){mutableStateOf(value ?: StudioColorMath.hex(h,s,v))}
    val preview=StudioColorMath.hex(h,s,v)
    Column(Modifier.fillMaxWidth().padding(start=56.dp,top=4.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(parseTableColorHex(preview) ?: Color.Gray))
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(field,{t->field=t;parseTableColorHex(t)?.let{onChange(tableColorHex(it))}},singleLine=true,
                label={Text("Hex")},modifier=Modifier.weight(1f).testTag("$tag-hex"))
        }
        // Sliders preview locally and commit one undo step when the finger lifts.
        val commit={onChange(StudioColorMath.hex(h,s,v))}
        StudioSliderRow("Hue",h,0f..360f,Brush.horizontalGradient(listOf(Color.Red,Color.Yellow,Color.Green,Color.Cyan,Color.Blue,Color.Magenta,Color.Red)),"$tag-hue",commit) {h=it}
        StudioSliderRow("Sat",s,0f..1f,null,"$tag-sat",commit) {s=it}
        StudioSliderRow("Light",v,0f..1f,null,"$tag-val",commit) {v=it}
    }
}

@Composable
private fun StudioSliderRow(label:String,value:Float,range:ClosedFloatingPointRange<Float>,track:Brush?,tag:String,onFinished:()->Unit,onValue:(Float)->Unit) {
    Row(verticalAlignment=Alignment.CenterVertically) {
        Text(label,color=darkTableColors.muted,fontSize=12.sp,modifier=Modifier.width(44.dp))
        Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
            if(track!=null)Box(Modifier.fillMaxWidth().padding(horizontal=10.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(track))
            Slider(value,onValue,valueRange=range,onValueChangeFinished=onFinished,modifier=Modifier.testTag(tag),
                colors=if(track!=null)SliderDefaults.colors(activeTrackColor=Color.Transparent,inactiveTrackColor=Color.Transparent) else SliderDefaults.colors())
        }
    }
}
