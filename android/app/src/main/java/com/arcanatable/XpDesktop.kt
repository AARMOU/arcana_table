package com.arcanatable

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.max

/** XP is a native, optional Studio presentation skin; the regular game stays the sole rules/UI source. */
@Composable
internal fun xpSkinEnabled():Boolean {
    val studio=LocalStudioControls.current
    return BuildConfig.STUDIO_ENABLED && studio?.editing!=true && studio?.project()?.skin=="Windows XP"
}

/** Dimensions are dp, not pixels. Separate saved positions are kept for Fold cover and unfolded screens. */
internal data class XpRect(val x:Float,val y:Float,val width:Float,val height:Float) {
    fun bounded(maxWidth:Float,maxHeight:Float,minWidth:Float,minHeight:Float):XpRect {
        val w=width.coerceIn(minWidth.coerceAtMost(maxWidth),maxWidth)
        val h=height.coerceIn(minHeight.coerceAtMost(maxHeight),maxHeight)
        return XpRect(x.coerceIn(0f,max(0f,maxWidth-w)),y.coerceIn(0f,max(0f,maxHeight-h)),w,h)
    }
    fun moved(dx:Float,dy:Float,maxWidth:Float,maxHeight:Float,minWidth:Float,minHeight:Float):XpRect =
        copy(x=x+dx,y=y+dy).bounded(maxWidth,maxHeight,minWidth,minHeight)

    fun resized(dx:Float,dy:Float,edge:XpEdge,maxWidth:Float,maxHeight:Float,minWidth:Float,minHeight:Float):XpRect {
        val old=bounded(maxWidth,maxHeight,minWidth,minHeight)
        var left=old.x;var top=old.y;var right=left+old.width;var bottom=top+old.height
        if(edge.left)left=(left+dx).coerceIn(0f,right-minWidth.coerceAtMost(maxWidth))
        if(edge.right)right=(right+dx).coerceIn(left+minWidth.coerceAtMost(maxWidth),maxWidth)
        if(edge.top)top=(top+dy).coerceIn(0f,bottom-minHeight.coerceAtMost(maxHeight))
        if(edge.bottom)bottom=(bottom+dy).coerceIn(top+minHeight.coerceAtMost(maxHeight),maxHeight)
        return XpRect(left,top,right-left,bottom-top).bounded(maxWidth,maxHeight,minWidth,minHeight)
    }
}

internal enum class XpEdge(val left:Boolean=false,val right:Boolean=false,val top:Boolean=false,val bottom:Boolean=false) {
    Left(left=true),Right(right=true),Top(top=true),Bottom(bottom=true),
    TopLeft(left=true,top=true),TopRight(right=true,top=true),
    BottomLeft(left=true,bottom=true),BottomRight(right=true,bottom=true)
}

internal object XpWindowStore {
    fun load(prefs:SharedPreferences,key:String,default:XpRect):XpRect {
        val raw=prefs.getString(key,null) ?: return default
        val numbers=raw.split(",").mapNotNull{it.toFloatOrNull()}
        return if(numbers.size==4 && numbers.all{it.isFinite()})XpRect(numbers[0],numbers[1],numbers[2],numbers[3]) else default
    }
    fun save(prefs:SharedPreferences,key:String,value:XpRect) {
        prefs.edit().putString(key,listOf(value.x,value.y,value.width,value.height).joinToString(",")).apply()
    }
}

private val xpBlue=Color(0xFF0C3DBD)
private val xpDarkBlue=Color(0xFF062B83)
private val xpBeige=Color(0xFFECE9D8)

@Composable
internal fun XpDesktopHost(hasPriority:Boolean,content:@Composable ()->Unit) {
    val context=LocalContext.current
    val prefs=remember {context.getSharedPreferences("arcana_xp_desktop",Context.MODE_PRIVATE)}
    BoxWithConstraints(Modifier.fillMaxSize().testTag("xp-desktop")) {
        val screenW=maxWidth.value.coerceAtLeast(1f)
        val screenH=(maxHeight-50.dp).value.coerceAtLeast(1f)
        val screenClass=if(screenW<550f)"folded" else "unfolded"
        val gameKey="game_$screenClass"
        val gooseKey="goose_$screenClass"
        val minGameW=minOf(230f,screenW)
        val minGameH=minOf(250f,screenH)
        val minGooseW=minOf(176f,screenW)
        val minGooseH=minOf(132f,screenH)
        var game by remember(screenClass){mutableStateOf(
            XpWindowStore.load(prefs,gameKey,XpRect(4f,12f,screenW-8f,screenH-20f))
                .bounded(screenW,screenH,minGameW,minGameH))}
        var goose by remember(screenClass){mutableStateOf(
            XpWindowStore.load(prefs,gooseKey,XpRect((screenW-228f).coerceAtLeast(0f),45f,220f,172f))
                .bounded(screenW,screenH,minGooseW,minGooseH))}
        var minimized by remember(screenClass){mutableStateOf(false)}
        var gooseOpen by remember(screenClass){mutableStateOf(true)}
        var startOpen by remember{mutableStateOf(false)}
        var maximized by remember(screenClass){mutableStateOf(false)}
        var beforeMax by remember(screenClass){mutableStateOf(game)}
        var alert by remember{mutableStateOf<String?>(null)}
        var firstPriority by remember{mutableStateOf<Boolean?>(null)}
        var gooseText by remember{mutableStateOf("It looks like you're playing Commander. Need some glue?")}
        // Use a real game transition only; never invent mana errors, counterspells, or card actions.
        LaunchedEffect(hasPriority) {
            if(firstPriority==false && hasPriority) {
                gooseText="It looks like you have priority. Please use it irresponsibly."
                alert="Your opponents have passed priority to you. Arcana.exe requires your attention."
            }
            firstPriority=hasPriority
        }

        XpHills(Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.TopStart).padding(start=12.dp,top=12.dp)) {
            Text("🖥️",fontSize=25.sp)
            Text("My Commander",fontSize=11.sp,color=Color.White)
            Spacer(Modifier.height(14.dp))
            Text("📁",fontSize=25.sp)
            Text("The Stack",fontSize=11.sp,color=Color.White)
        }

        if(!minimized) {
            XpWindow(title="Arcana Table — Commander",rect=game,modifier=Modifier.zIndex(1f),
                minWidth=minGameW,minHeight=minGameH,limitWidth=screenW,limitHeight=screenH,
                onMove={dx,dy->game=game.moved(dx,dy,screenW,screenH,minGameW,minGameH)},
                onResize={dx,dy,edge->game=game.resized(dx,dy,edge,screenW,screenH,minGameW,minGameH)},
                onFinish={XpWindowStore.save(prefs,gameKey,game)},
                onMinimize={minimized=true},
                onMaximize={
                    if(maximized){game=beforeMax.bounded(screenW,screenH,minGameW,minGameH)}
                    else{beforeMax=game;game=XpRect(0f,0f,screenW,screenH)}
                    maximized=!maximized;XpWindowStore.save(prefs,gameKey,game)
                }) {
                content()
            }
        }
        if(gooseOpen) {
            XpWindow(title="GooseClippy.exe",rect=goose,modifier=Modifier.zIndex(2f),
                minWidth=minGooseW,minHeight=minGooseH,limitWidth=screenW,limitHeight=screenH,
                onMove={dx,dy->goose=goose.moved(dx,dy,screenW,screenH,minGooseW,minGooseH)},
                onResize={dx,dy,edge->goose=goose.resized(dx,dy,edge,screenW,screenH,minGooseW,minGooseH)},
                onFinish={XpWindowStore.save(prefs,gooseKey,goose)},
                onMinimize={gooseOpen=false}) {
                Row(Modifier.fillMaxSize().background(Color(0xFFFFFFF1)).padding(8.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    GooseClippy(Modifier.size(64.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(gooseText,color=Color(0xFF171717),fontSize=12.sp,lineHeight=15.sp)
                }
            }
        }
        if(alert!=null) {
            Box(Modifier.fillMaxSize().zIndex(3f).background(Color.Black.copy(alpha=0.18f)),
                contentAlignment=Alignment.Center) {
                Column(Modifier.fillMaxWidth(0.86f).border(2.dp,xpDarkBlue).background(xpBeige).testTag("xp-alert")) {
                    XpTitle("Arcana.exe — Priority Alert")
                    Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text("❌",fontSize=25.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(alert!!,fontSize=13.sp,color=Color.Black)
                    }
                    Row(Modifier.fillMaxWidth().padding(bottom=12.dp,end=12.dp),
                        horizontalArrangement=Arrangement.End) {
                        XpButton("OK"){alert=null}
                    }
                }
            }
        }
        if(startOpen) {
            Column(Modifier.align(Alignment.BottomStart).padding(bottom=50.dp)
                .widthIn(max=260.dp).border(2.dp,xpDarkBlue).background(xpBeige).zIndex(4f).padding(8.dp)) {
                Text("🪿  Arcana XP",color=xpDarkBlue,fontWeight=FontWeight.Bold)
                TextButton(onClick={
                    game=XpRect(4f,12f,screenW-8f,screenH-20f).bounded(screenW,screenH,minGameW,minGameH)
                    goose=XpRect((screenW-228f).coerceAtLeast(0f),45f,220f,172f).bounded(screenW,screenH,minGooseW,minGooseH)
                    XpWindowStore.save(prefs,gameKey,game);XpWindowStore.save(prefs,gooseKey,goose)
                    minimized=false;maximized=false;startOpen=false
                }){Text("Reset window positions",color=xpDarkBlue)}
                TextButton(onClick={gooseOpen=!gooseOpen;startOpen=false}) {
                    Text(if(gooseOpen)"Hide GooseClippy" else "Show GooseClippy",color=xpDarkBlue)
                }
                Text("Change skins in Studio → Layout → Presets",fontSize=11.sp,color=Color.DarkGray)
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(50.dp).zIndex(5f)
            .background(Brush.verticalGradient(listOf(Color(0xFF3788EF),xpBlue,xpDarkBlue))),
            verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.height(44.dp).width(82.dp).clip(RoundedCornerShape(topEnd=17.dp,bottomEnd=17.dp))
                .background(Color(0xFF398926)).clickable{startOpen=!startOpen},
                contentAlignment=Alignment.Center) {
                Text("🪿 Start",fontWeight=FontWeight.Bold,color=Color.White,fontSize=13.sp)
            }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f).height(38.dp).border(1.dp,Color(0xFF9DBEFF))
                .background(if(minimized)xpDarkBlue else Color(0xFF2564D8))
                .clickable{minimized=!minimized}.padding(horizontal=8.dp),
                contentAlignment=Alignment.CenterStart) {
                Text("Arcana Table",color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=12.sp)
            }
            if(!gooseOpen) TextButton(onClick={gooseOpen=true}){Text("📎",color=Color.White)}
            Text("XP",color=Color.White,fontSize=12.sp,modifier=Modifier.padding(horizontal=10.dp))
        }
    }
}

@Composable
private fun XpHills(modifier:Modifier=Modifier) {
    Canvas(modifier) {
        drawRect(brush=Brush.verticalGradient(listOf(Color(0xFF53A6EB),Color(0xFFBEEBFF),Color(0xFF8FD2FB))))
        val hill=Path().apply {
            moveTo(0f,size.height*0.65f)
            cubicTo(size.width*0.25f,size.height*0.30f,size.width*0.45f,size.height*0.48f,size.width,size.height*0.60f)
            lineTo(size.width,size.height);lineTo(0f,size.height);close()
        }
        drawPath(hill,brush=Brush.verticalGradient(listOf(Color(0xFF98CD46),Color(0xFF318B25)),startY=size.height*0.45f,endY=size.height))
    }
}

@Composable
private fun XpTitle(name:String) {
    Box(Modifier.fillMaxWidth().height(30.dp)
        .background(Brush.horizontalGradient(listOf(xpDarkBlue,xpBlue,Color(0xFF54A6F7))))
        .padding(horizontal=8.dp),contentAlignment=Alignment.CenterStart) {
        Text(name,color=Color.White,fontWeight=FontWeight.Bold,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}

@Composable
private fun XpButton(label:String,onClick:()->Unit) {
    Box(Modifier.heightIn(min=32.dp).widthIn(min=48.dp).border(1.dp,Color.DarkGray)
        .background(Color(0xFFF7F5EF)).clickable(onClick=onClick).padding(horizontal=10.dp),
        contentAlignment=Alignment.Center) {
        Text(label,color=Color.Black,fontSize=12.sp)
    }
}

/** All four edges and corners are independently draggable; title drag and resizing persist across launches. */
@Composable
private fun XpWindow(title:String,rect:XpRect,modifier:Modifier=Modifier,
    minWidth:Float,minHeight:Float,limitWidth:Float,limitHeight:Float,
    onMove:(Float,Float)->Unit,onResize:(Float,Float,XpEdge)->Unit,
    onFinish:()->Unit,onMinimize:()->Unit,onMaximize:(()->Unit)?=null,content:@Composable ()->Unit) {
    val density=LocalDensity.current.density
    val handle:(XpEdge)->Modifier={edge->
        Modifier.pointerInput(edge) {
            detectDragGestures(onDragEnd=onFinish,onDragCancel=onFinish){change,delta->
                change.consume();onResize(delta.x/density,delta.y/density,edge)
            }
        }.testTag("xp-resize-${title}-${edge.name}")
    }
    Box(modifier.offset(rect.x.dp,rect.y.dp).size(rect.width.dp,rect.height.dp)
        .shadow(5.dp).border(2.dp,xpDarkBlue).background(xpBeige)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(34.dp)
                .background(Brush.horizontalGradient(listOf(xpDarkBlue,xpBlue,Color(0xFF58A6FF)))),
                verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.weight(1f).fillMaxHeight().pointerInput(title) {
                    detectDragGestures(onDragEnd=onFinish,onDragCancel=onFinish){change,delta->
                        change.consume();onMove(delta.x/density,delta.y/density)
                    }
                }.padding(horizontal=6.dp),contentAlignment=Alignment.CenterStart) {
                    Text("🪿 $title",maxLines=1,overflow=TextOverflow.Ellipsis,color=Color.White,
                        fontSize=12.sp,fontWeight=FontWeight.Bold)
                }
                XpButton("_",onMinimize)
                onMaximize?.let{XpButton("□",it)}
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(2.dp).clip(RoundedCornerShape(1.dp))
                .background(Color(0xFF121927))) {content()}
        }
        Box(Modifier.align(Alignment.CenterStart).width(9.dp).fillMaxHeight().then(handle(XpEdge.Left)))
        Box(Modifier.align(Alignment.CenterEnd).width(9.dp).fillMaxHeight().then(handle(XpEdge.Right)))
        Box(Modifier.align(Alignment.TopCenter).height(7.dp).fillMaxWidth().then(handle(XpEdge.Top)))
        Box(Modifier.align(Alignment.BottomCenter).height(9.dp).fillMaxWidth().then(handle(XpEdge.Bottom)))
        Box(Modifier.align(Alignment.TopStart).size(20.dp).then(handle(XpEdge.TopLeft)))
        Box(Modifier.align(Alignment.TopEnd).size(20.dp).then(handle(XpEdge.TopRight)))
        Box(Modifier.align(Alignment.BottomStart).size(20.dp).then(handle(XpEdge.BottomLeft)))
        Box(Modifier.align(Alignment.BottomEnd).size(27.dp).background(Color(0xFFADB7C8))
            .then(handle(XpEdge.BottomRight)).testTag("xp-resize-corner"))
    }
}

/** An intentionally clumsy orange beak pasted over a friendly original paperclip mascot. */
@Composable
internal fun GooseClippy(modifier:Modifier=Modifier) {
    Canvas(modifier.testTag("goose-clippy")) {
        val w=size.width;val h=size.height
        val wire=Path().apply {
            moveTo(w*.53f,h*.86f)
            cubicTo(w*.13f,h*.88f,w*.18f,h*.27f,w*.32f,h*.18f)
            cubicTo(w*.64f,h*-.02f,w*.87f,h*.22f,w*.73f,h*.45f)
            lineTo(w*.48f,h*.73f)
            cubicTo(w*.34f,h*.86f,w*.19f,h*.65f,w*.35f,h*.43f)
            lineTo(w*.57f,h*.19f)
        }
        drawPath(wire,Color(0xFF5D6571),style=Stroke(width=w*.18f,cap=StrokeCap.Round))
        drawPath(wire,Color(0xFFE9EEF3),style=Stroke(width=w*.11f,cap=StrokeCap.Round))
        drawCircle(Color.White,radius=w*.115f,center=Offset(w*.46f,h*.29f))
        drawCircle(Color.White,radius=w*.115f,center=Offset(w*.68f,h*.28f))
        drawCircle(Color.Black,radius=w*.045f,center=Offset(w*.48f,h*.30f))
        drawCircle(Color.Black,radius=w*.045f,center=Offset(w*.70f,h*.29f))
        // The very obvious pasted-on orange triangle is deliberate; it is GooseClippy.
        val beak=Path().apply {
            moveTo(w*.50f,h*.39f);lineTo(w*.95f,h*.46f);lineTo(w*.53f,h*.55f);close()
        }
        drawPath(beak,Color(0xFFFF9016))
        drawPath(beak,Color(0xFFBB5400),style=Stroke(width=2f))
    }
}

/** Classic XP framing for existing real game dialog content; callbacks remain owned by Arcana. */
@Composable
internal fun XpGameDialogFrame(title:String,content:@Composable ()->Unit) {
    Column(Modifier.fillMaxWidth().border(2.dp,xpDarkBlue).background(xpBeige).testTag("xp-game-dialog")) {
        XpTitle("Arcana.exe — ${title.ifBlank { "Dialog" }}")
        Box(Modifier.fillMaxWidth().padding(3.dp)) {content()}
    }
}
