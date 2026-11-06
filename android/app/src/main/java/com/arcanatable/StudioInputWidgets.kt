package com.arcanatable

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

@Composable
internal fun StudioCheckbox(site:String,screen:String,checked:Boolean,onCheckedChange:((Boolean)->Unit)?,modifier:Modifier=Modifier,
    enabled:Boolean=true,colors:CheckboxColors=CheckboxDefaults.colors(),interactionSource:MutableInteractionSource?=null) {
    val w=studioWidget(site,screen,"toggle","Checkbox",enabled)
    if(w?.style?.visible==false)return
    val c=w?.style?.ink?.let(::parseTableColorHex)?.let {colors.copy(checkedCheckmarkColor=it)} ?: colors
    Checkbox(checked,if(onCheckedChange==null)null else {v->if(w?.runtime?.editing==true)w.runtime.select(w.id) else onCheckedChange(v)},modifier.studioWidget(w),
        enabled,c,interactionSource)
}

@Composable
internal fun StudioRadioButton(site:String,screen:String,selected:Boolean,onClick:(()->Unit)?,modifier:Modifier=Modifier,
    enabled:Boolean=true,colors:RadioButtonColors=RadioButtonDefaults.colors(),interactionSource:MutableInteractionSource?=null) {
    val w=studioWidget(site,screen,"toggle","Choice",enabled)
    if(w?.style?.visible==false)return
    val c=w?.style?.ink?.let(::parseTableColorHex)?.let {colors.copy(selectedColor=it)} ?: colors
    RadioButton(selected,if(onClick==null)null else ({if(w?.runtime?.editing==true)w.runtime.select(w.id) else onClick()}),modifier.studioWidget(w),enabled,c,interactionSource)
}

@Composable
internal fun StudioSwitch(site:String,screen:String,checked:Boolean,onCheckedChange:((Boolean)->Unit)?,modifier:Modifier=Modifier,
    thumbContent:(@Composable ()->Unit)?=null,enabled:Boolean=true,colors:SwitchColors=SwitchDefaults.colors(),interactionSource:MutableInteractionSource?=null) {
    val w=studioWidget(site,screen,"toggle","Switch",enabled)
    if(w?.style?.visible==false)return
    val c=w?.style?.ink?.let(::parseTableColorHex)?.let {colors.copy(checkedThumbColor=it,uncheckedThumbColor=it)} ?: colors
    Switch(checked,{v->if(w?.runtime?.editing==true)w.runtime.select(w.id) else onCheckedChange?.invoke(v)},modifier.studioWidget(w),thumbContent,
        enabled,c,interactionSource)
}

@Composable
internal fun StudioSlider(site:String,screen:String,value:Float,onValueChange:(Float)->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    valueRange:ClosedFloatingPointRange<Float> = 0f..1f,steps:Int=0,onValueChangeFinished:(()->Unit)?=null,
    colors:SliderColors=SliderDefaults.colors(),interactionSource:MutableInteractionSource=remember{MutableInteractionSource()}) {
    val w=studioWidget(site,screen,"range","Slider",enabled)
    if(w?.style?.visible==false)return
    val c=w?.style?.ink?.let(::parseTableColorHex)?.let {colors.copy(thumbColor=it,activeTrackColor=it)} ?: colors
    Slider(value,{v->if(w?.runtime?.editing==true)w.runtime.select(w.id) else onValueChange(v)},modifier.studioWidget(w),enabled,
        valueRange,steps,if(w?.runtime?.editing==true)null else onValueChangeFinished,c,interactionSource)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudioRangeSlider(site:String,screen:String,value:ClosedFloatingPointRange<Float>,onValueChange:(ClosedFloatingPointRange<Float>)->Unit,
    modifier:Modifier=Modifier,enabled:Boolean=true,valueRange:ClosedFloatingPointRange<Float> = 0f..1f,steps:Int=0,
    onValueChangeFinished:(()->Unit)?=null,colors:SliderColors=SliderDefaults.colors()) {
    val w=studioWidget(site,screen,"range","Range",enabled)
    if(w?.style?.visible==false)return
    val c=w?.style?.ink?.let(::parseTableColorHex)?.let {colors.copy(thumbColor=it,activeTrackColor=it)} ?: colors
    RangeSlider(value,{v->if(w?.runtime?.editing==true)w.runtime.select(w.id) else onValueChange(v)},modifier.studioWidget(w),enabled,
        valueRange,steps,if(w?.runtime?.editing==true)null else onValueChangeFinished,c)
}

@Composable
internal fun StudioOutlinedTextField(site:String,screen:String,value:String,onValueChange:(String)->Unit,modifier:Modifier=Modifier,
    enabled:Boolean=true,readOnly:Boolean=false,textStyle:TextStyle=LocalTextStyle.current,label:(@Composable ()->Unit)?=null,
    placeholder:(@Composable ()->Unit)?=null,leadingIcon:(@Composable ()->Unit)?=null,trailingIcon:(@Composable ()->Unit)?=null,
    prefix:(@Composable ()->Unit)?=null,suffix:(@Composable ()->Unit)?=null,supportingText:(@Composable ()->Unit)?=null,
    isError:Boolean=false,visualTransformation:VisualTransformation=VisualTransformation.None,keyboardOptions:KeyboardOptions=KeyboardOptions.Default,
    keyboardActions:KeyboardActions=KeyboardActions.Default,singleLine:Boolean=false,maxLines:Int=if(singleLine)1 else Int.MAX_VALUE,
    minLines:Int=1,interactionSource:MutableInteractionSource?=null,shape:Shape=OutlinedTextFieldDefaults.shape,colors:TextFieldColors=OutlinedTextFieldDefaults.colors()) {
    val w=studioWidget(site,screen,"input","Text input",enabled)
    if(w?.style?.visible==false)return
    val style=textStyle.copy(color=w?.style?.ink?.let(::parseTableColorHex) ?: textStyle.color,
        fontSize=w?.style?.font?.coerceIn(8f,64f)?.sp ?: textStyle.fontSize)
    val nativeLabel:(@Composable ()->Unit)?=label?.let {original->{CompositionLocalProvider(LocalStudioControlParent provides w?.parent()){original()}}}
    OutlinedTextField(value,{v->if(w?.runtime?.editing==true)w.runtime.select(w.id) else onValueChange(v)},modifier.studioWidget(w),enabled,
        readOnly || w?.runtime?.editing==true,style,nativeLabel,placeholder,leadingIcon,trailingIcon,prefix,suffix,supportingText,isError,
        visualTransformation,keyboardOptions,keyboardActions,singleLine,maxLines,minLines,interactionSource,
        w?.style?.radius?.let{androidx.compose.foundation.shape.RoundedCornerShape(it.coerceIn(0f,100f).dp)} ?: shape,colors)
}
